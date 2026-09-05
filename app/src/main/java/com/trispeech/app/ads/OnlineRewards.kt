package com.trispeech.app.ads

import android.app.Activity
import android.content.Context
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.trispeech.app.BuildConfig
import com.trispeech.app.R
import com.trispeech.app.network.BackendClient
import com.trispeech.app.network.BackendException
import com.trispeech.app.network.OnlineAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RewardState(
    val access: OnlineAccess? = null,
    val busy: Boolean = false,
    val showing: Boolean = false,
    val message: String? = null
)

/** Client completion is an experimental signal, never server-side ad verification. */
class OnlineRewards(context: Context, private val backend: BackendClient, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("online_rewards", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(RewardState())
    val state = mutableState.asStateFlow()
    private var initialized = false
    val hasPrivacyChoice get() = prefs.contains("personalized")

    fun choosePrivacy(personalized: Boolean) {
        prefs.edit().putBoolean("personalized", personalized).putLong("consent_at", System.currentTimeMillis()).apply()
        if (initialized) StartAppSDK.setUserConsent(context, "pas", prefs.getLong("consent_at", 0), personalized)
    }

    fun refresh() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        scope.launch {
            try {
                reconcile()
                mutableState.update { it.copy(access = backend.onlineAccess()) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = error.message ?: context.getString(R.string.ad_unavailable)) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun reconcile() {
        val pending = prefs.getString("pending_completion", null) ?: return
        try {
            backend.completeStartIoReward(pending)
            prefs.edit().remove("pending_completion").commit()
        } catch (error: BackendException) {
            // An expired session cannot be recovered. Network/server failures remain retryable.
            if (error.status == 404 || error.status == 410) prefs.edit().remove("pending_completion").commit()
            throw error
        }
    }

    fun watch(activity: Activity, onFinished: (Boolean) -> Unit = {}) {
        if (mutableState.value.busy) return
        if (!hasPrivacyChoice) {
            onFinished(false)
            return
        }
        if (BuildConfig.STARTIO_APP_ID.isBlank()) {
            mutableState.update { it.copy(message = context.getString(R.string.ad_not_configured)) }
            onFinished(false)
            return
        }
        mutableState.update { it.copy(busy = true, message = context.getString(R.string.loading_ad)) }
        val offeredSeconds = mutableState.value.access?.rewardSeconds
        scope.launch {
            var rewarded = false
            try {
                // Retry an already completed reward before requesting another video.
                if (prefs.contains("pending_completion")) {
                    reconcile()
                    mutableState.update { it.copy(access = backend.onlineAccess(), message = null) }
                    rewarded = true
                    return@launch
                }
                val access = backend.onlineAccess()
                mutableState.update { it.copy(access = access) }
                require(offeredSeconds == access.rewardSeconds) { context.getString(R.string.ad_offer_changed) }
                require(access.enabled && access.provider == "startio" && access.mode == "rewarded-credit") {
                    context.getString(R.string.ad_not_configured)
                }
                require(access.testRewards == BuildConfig.STARTIO_TEST_ADS) { context.getString(R.string.ad_mode_mismatch) }
                val session = backend.createAdSession()
                require(session.rewardSeconds == access.rewardSeconds) { context.getString(R.string.ad_offer_changed) }
                withTimeout(15_000) { initializeSdk() }
                val ad = StartAppAd(activity)
                withTimeout(30_000) { load(ad) }
                check(!activity.isFinishing && !activity.isDestroyed) {
                    context.getString(R.string.ad_unavailable)
                }
                mutableState.update { it.copy(showing = true, message = context.getString(R.string.ad_in_progress)) }
                val completed = show(ad) {
                    // Persist before making a network request, so a lost response is recoverable.
                    prefs.edit().putString("pending_completion", session.id).commit()
                }
                mutableState.update { it.copy(showing = false) }
                if (completed) {
                    mutableState.update { it.copy(message = context.getString(R.string.applying_reward)) }
                    reconcile()
                    rewarded = true
                }
                mutableState.update { it.copy(access = backend.onlineAccess(), message = if (completed) null else context.getString(R.string.ad_incomplete)) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                mutableState.update { it.copy(message = if (error is kotlinx.coroutines.TimeoutCancellationException) context.getString(R.string.ad_unavailable) else error.message ?: context.getString(R.string.ad_unavailable)) }
            } finally {
                mutableState.update { it.copy(busy = false, showing = false) }
                onFinished(rewarded)
            }
        }
    }

    private suspend fun initializeSdk(): Unit {
        if (initialized) return
        StartAppSDK.setTestAdsEnabled(BuildConfig.STARTIO_TEST_ADS)
        StartAppSDK.setUserConsent(context, "pas", prefs.getLong("consent_at", 0), prefs.getBoolean("personalized", false))
        StartAppAd.disableAutoInterstitial()
        suspendCancellableCoroutine { continuation ->
            StartAppSDK.initParams(context, BuildConfig.STARTIO_APP_ID)
                .setCallback {
                    initialized = true
                    if (continuation.isActive) continuation.resume(Unit)
                }
                .init()
        }
    }

    private suspend fun load(ad: StartAppAd): Unit = suspendCancellableCoroutine { continuation ->
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(value: Ad) { if (continuation.isActive) continuation.resume(Unit) }
            override fun onFailedToReceiveAd(value: Ad?) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(context.getString(R.string.ad_unavailable)))
            }
        })
    }

    private suspend fun show(ad: StartAppAd, completed: () -> Unit): Boolean = suspendCancellableCoroutine { continuation ->
        var granted = false
        ad.setVideoListener {
            if (!granted) { granted = true; completed() }
        }
        val displayed = ad.showAd(object : AdDisplayListener {
            override fun adHidden(value: Ad?) { if (continuation.isActive) continuation.resume(granted) }
            override fun adDisplayed(value: Ad?) = Unit
            override fun adClicked(value: Ad?) = Unit
            override fun adNotDisplayed(value: Ad?) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(context.getString(R.string.ad_unavailable)))
            }
        })
        if (!displayed && continuation.isActive) continuation.resumeWithException(IllegalStateException(context.getString(R.string.ad_unavailable)))
    }
}
