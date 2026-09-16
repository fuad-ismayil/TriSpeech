package com.trispeech.app.ads

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.trispeech.app.BuildConfig
import com.trispeech.app.R
import com.trispeech.app.network.BackendClient
import com.trispeech.app.network.BackendException
import com.trispeech.app.network.OnlineAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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

class OnlineRewards(context: Context, private val backend: BackendClient, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("online_rewards", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(RewardState())
    val state = mutableState.asStateFlow()
    private var initialized = false
    val hasPrivacyChoice get() = prefs.contains(PERSONALIZED)

    fun choosePrivacy(personalized: Boolean) {
        prefs.edit().putBoolean(PERSONALIZED, personalized).putLong("consent_at", System.currentTimeMillis()).apply()
    }

    fun refresh() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        scope.launch {
            try {
                mutableState.update { it.copy(access = reconcilePendingReward()) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = error.message ?: context.getString(R.string.ad_unavailable)) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun watch(activity: Activity, onFinished: (Boolean) -> Unit = {}) {
        if (mutableState.value.busy) return
        if (!hasPrivacyChoice) {
            onFinished(false)
            return
        }
        if (BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.isBlank()) {
            mutableState.update { it.copy(message = context.getString(R.string.ad_not_configured)) }
            onFinished(false)
            return
        }
        mutableState.update { it.copy(busy = true, message = context.getString(R.string.loading_ad)) }
        val offeredSeconds = mutableState.value.access?.rewardSeconds
        scope.launch {
            var rewarded = false
            try {
                val pendingSession = prefs.getString(PENDING_REWARD_SESSION, null)
                val access = reconcilePendingReward()
                mutableState.update { it.copy(access = access) }
                if (pendingSession != null && access.lastGrantSessionId == pendingSession) {
                    rewarded = true
                    return@launch
                }
                require(offeredSeconds == access.rewardSeconds) { context.getString(R.string.ad_offer_changed) }
                require(access.enabled && access.provider == "admob" && access.mode == "rewarded-credit") {
                    context.getString(R.string.ad_not_configured)
                }
                require(access.testRewards == BuildConfig.ADMOB_TEST_ADS) { context.getString(R.string.ad_mode_mismatch) }
                val session = backend.createAdSession()
                require(session.rewardSeconds == access.rewardSeconds) { context.getString(R.string.ad_offer_changed) }
                withTimeout(15_000) { initializeSdk() }
                val ad = withTimeout(30_000) { load(activity, session.id) }
                check(!activity.isFinishing && !activity.isDestroyed) { context.getString(R.string.ad_unavailable) }
                mutableState.update { it.copy(showing = true, message = context.getString(R.string.ad_in_progress)) }
                val completed = show(activity, ad) {
                    prefs.edit().putString(PENDING_REWARD_SESSION, session.id).commit()
                }
                mutableState.update { it.copy(showing = false) }
                if (completed) {
                    mutableState.update { it.copy(message = context.getString(R.string.verifying_reward)) }
                    val confirmed = awaitServerReward(session.id)
                    mutableState.update { it.copy(access = confirmed, message = null) }
                    rewarded = true
                } else {
                    mutableState.update { it.copy(access = backend.onlineAccess(), message = context.getString(R.string.ad_incomplete)) }
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                val message = if (error is kotlinx.coroutines.TimeoutCancellationException) {
                    context.getString(R.string.ad_unavailable)
                } else {
                    error.message ?: context.getString(R.string.ad_unavailable)
                }
                mutableState.update { it.copy(message = message) }
            } finally {
                mutableState.update { it.copy(busy = false, showing = false) }
                onFinished(rewarded)
            }
        }
    }

    private suspend fun reconcilePendingReward(): OnlineAccess {
        val pending = prefs.getString(PENDING_REWARD_SESSION, null)
        if (pending != null && BuildConfig.ADMOB_TEST_ADS) {
            try {
                backend.completeAdMobTestReward(pending)
            } catch (error: BackendException) {
                if (error.status == 404 || error.status == 410) {
                    prefs.edit().remove(PENDING_REWARD_SESSION).commit()
                } else if (error.status != 503) {
                    throw error
                }
            }
        }
        val access = backend.onlineAccess()
        if (pending != null && access.lastGrantSessionId == pending) {
            prefs.edit().remove(PENDING_REWARD_SESSION).commit()
        }
        return access
    }

    private suspend fun awaitServerReward(sessionId: String): OnlineAccess {
        if (BuildConfig.ADMOB_TEST_ADS) {
            try {
                backend.completeAdMobTestReward(sessionId)
            } catch (error: BackendException) {
                if (error.status != 503) throw error
            }
        }
        repeat(40) {
            try {
                val access = backend.onlineAccess()
                if (access.lastGrantSessionId == sessionId) {
                    prefs.edit().remove(PENDING_REWARD_SESSION).commit()
                    return access
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The server callback and balance refresh are independently retryable.
            }
            delay(750)
        }
        throw IllegalStateException(context.getString(R.string.reward_verification_pending))
    }

    private suspend fun initializeSdk() {
        if (initialized) return
        suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(context) {
                initialized = true
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private suspend fun load(activity: Activity, sessionId: String): RewardedAd =
        suspendCancellableCoroutine { continuation ->
            val requestBuilder = AdRequest.Builder()
            if (!prefs.getBoolean(PERSONALIZED, false)) {
                requestBuilder.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") })
            }
            RewardedAd.load(
                activity,
                BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
                requestBuilder.build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        ad.setServerSideVerificationOptions(
                            ServerSideVerificationOptions.Builder().setCustomData(sessionId).build()
                        )
                        if (continuation.isActive) continuation.resume(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException(context.getString(R.string.ad_unavailable))
                        )
                    }
                }
            )
        }

    private suspend fun show(activity: Activity, ad: RewardedAd, completed: () -> Unit): Boolean =
        suspendCancellableCoroutine { continuation ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) continuation.resume(earned)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException(context.getString(R.string.ad_unavailable))
                    )
                }
            }
            ad.show(activity) {
                if (!earned) {
                    earned = true
                    completed()
                }
            }
        }

    private companion object {
        const val PERSONALIZED = "personalized"
        const val PENDING_REWARD_SESSION = "pending_reward_session"
    }
}
