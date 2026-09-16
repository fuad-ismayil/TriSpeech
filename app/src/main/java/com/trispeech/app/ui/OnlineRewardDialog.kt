package com.trispeech.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.trispeech.app.BuildConfig
import com.trispeech.app.R
import com.trispeech.app.ads.OnlineRewards
import com.trispeech.app.ads.RewardState

@Composable
internal fun BalanceDialog(state: RewardState, rewards: OnlineRewards, activity: Activity?, close: () -> Unit) {
    var privacy by remember { mutableStateOf(false) }
    if (privacy) {
        PrivacyChoiceDialog(
            choose = { personalized -> rewards.choosePrivacy(personalized); privacy = false },
            close = { privacy = false }
        )
        return
    }
    val access = state.access
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.online_balance)) },
        text = {
            Column {
                Text(
                    if (access == null) stringResource(R.string.refreshing)
                    else pluralStringResource(R.plurals.credit_seconds, access.availableSeconds, access.availableSeconds)
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.reward_expiry))
                if (BuildConfig.ADMOB_TEST_ADS) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.test_ads_notice))
                }
                state.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !state.busy && access?.enabled == true && access.provider == "admob" && access.mode == "rewarded-credit" && activity != null,
                    onClick = {
                        if (!rewards.hasPrivacyChoice) privacy = true
                        else activity?.let { rewards.watch(it) }
                    }
                ) {
                    Text(if (state.busy) stringResource(R.string.loading_ad) else stringResource(R.string.watch_ad_increase_balance))
                }
                TextButton(onClick = { privacy = true }, enabled = !state.busy) {
                    Text(stringResource(R.string.privacy_choices))
                }
            }
        },
        confirmButton = { TextButton(onClick = close, enabled = !state.busy) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
internal fun PrivacyChoiceDialog(choose: (Boolean) -> Unit, close: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = { close?.invoke() },
        title = { Text(stringResource(R.string.privacy_choices)) },
        text = { Text(stringResource(R.string.ad_privacy_body)) },
        confirmButton = { TextButton(onClick = { choose(true) }) { Text(stringResource(R.string.ad_personalized)) } },
        dismissButton = { TextButton(onClick = { choose(false) }) { Text(stringResource(R.string.ad_nonpersonalized)) } }
    )
}

internal fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
