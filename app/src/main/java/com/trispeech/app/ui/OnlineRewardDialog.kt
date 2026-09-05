package com.trispeech.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.trispeech.app.BuildConfig
import com.trispeech.app.R
import com.trispeech.app.ads.OnlineRewards
import com.trispeech.app.ads.RewardState

@Composable
internal fun OnlineRewardDialog(studio: StudioState, state: RewardState, rewards: OnlineRewards,
    activity: Activity?, close: () -> Unit, resume: () -> Unit, discard: () -> Unit) {
    var privacy by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    val access = state.access
    if (privacy) {
        AlertDialog(onDismissRequest = { privacy = false }, title = { Text(stringResource(R.string.privacy_choices)) },
            text = { Text(stringResource(R.string.ad_privacy_body)) },
            confirmButton = { TextButton(onClick = { rewards.choosePrivacy(true); privacy = false }) { Text(stringResource(R.string.ad_personalized)) } },
            dismissButton = { TextButton(onClick = { rewards.choosePrivacy(false); privacy = false }) { Text(stringResource(R.string.ad_nonpersonalized)) } })
    } else if (discardConfirmation) {
        AlertDialog(onDismissRequest = { discardConfirmation = false }, title = { Text(stringResource(R.string.discard_recording)) },
            text = { Text(stringResource(R.string.discard_pending_audio)) },
            confirmButton = { TextButton(onClick = discard) { Text(stringResource(R.string.discard_recording)) } },
            dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text(stringResource(R.string.cancel)) } })
    } else {
        AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.online_reward_access)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.online_reward_body))
                    if (access != null) {
                        Text(pluralStringResource(R.plurals.credit_seconds, access.availableSeconds, access.availableSeconds))
                        if (access.mode == "development-preview") Text(stringResource(R.string.online_preview_access))
                        else Text(stringResource(R.string.reward_expiry))
                    }
                    if (studio.hasPendingOnline) Text(stringResource(R.string.pending_audio_credit, studio.pendingOnlineSeconds))
                    if (BuildConfig.STARTIO_TEST_ADS) Text(stringResource(R.string.test_ads_notice))
                    state.message?.let { Text(it) }
                    TextButton(onClick = rewards::refresh, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = { privacy = true }, enabled = !state.busy) { Text(stringResource(R.string.privacy_choices)) }
                    if (access != null && access.mode != "development-preview") {
                        Button(enabled = !state.busy && access.enabled && access.provider == "startio" && activity != null,
                            onClick = { if (!rewards.hasPrivacyChoice) privacy = true else activity?.let(rewards::watch) }) {
                            Text(if (state.busy) stringResource(R.string.loading_ad) else pluralStringResource(R.plurals.watch_ad_reward, access.rewardSeconds, access.rewardSeconds))
                        }
                    }
                    if (studio.hasPendingOnline) {
                        Button(onClick = resume, enabled = !state.busy && !studio.processing && studio.pendingOnlineSeconds in 1..600 &&
                            access?.enabled == true && (access.mode == "development-preview" || access.availableSeconds >= studio.pendingOnlineSeconds)) {
                            Text(stringResource(R.string.continue_label))
                        }
                        TextButton(onClick = { discardConfirmation = true }, enabled = !state.busy && !studio.processing) { Text(stringResource(R.string.discard_recording)) }
                    }
                }
            }, confirmButton = { TextButton(onClick = close, enabled = !state.busy) { Text(stringResource(R.string.close)) } })
    }
}

internal fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
