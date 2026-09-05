@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.trispeech.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trispeech.app.BuildConfig
import com.trispeech.app.LocaleController
import com.trispeech.app.R
import com.trispeech.app.data.TranscriptEntity
import com.trispeech.app.export.TranscriptExporter
import com.trispeech.app.models.DeviceMemoryProfiler
import com.trispeech.app.models.ModelCatalog
import com.trispeech.app.models.ModelDownloadManager
import com.trispeech.app.transcription.EngineKind
import com.trispeech.app.transcription.ModelTier
import com.trispeech.app.transcription.OnlineModel
import com.trispeech.app.transcription.WhisperLanguages
import com.trispeech.app.ui.theme.liquidGlass
import java.text.DateFormat
import java.io.File
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.ViewInAr

private enum class Screen { STUDIO, PROCESSING, HISTORY, MODELS, VIEWER, LICENSES }
private enum class LegalDocument { PRIVACY, TERMS }

@Composable
fun TriSpeechApp(darkTheme: Boolean, vm: AppViewModel = viewModel(), onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val rewardState by vm.onlineRewards.state.collectAsState()
    val history by vm.transcripts.collectAsState()
    val installedModels by vm.installedModels.collectAsState()
    var screen by remember { mutableStateOf(Screen.STUDIO) }
    var viewedId by remember { mutableStateOf<Long?>(null) }
    var observedProcessing by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val previewPreferences = remember { context.getSharedPreferences("development_preview", Context.MODE_PRIVATE) }
    var showDevelopmentNotice by remember { mutableStateOf(!previewPreferences.getBoolean("hide_notice", false)) }
    var dontShowDevelopmentNotice by remember { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else scope.launch { snackbar.showSnackbar(context.getString(R.string.permission_required)) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importAudio(it, { screen = Screen.PROCESSING }) { id -> viewedId = id; screen = Screen.VIEWER } }
    }
    val openSupport = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kofe.al/@fuadismayil"))) }

    if (state.showOnlineRewards) {
        OnlineRewardDialog(state, rewardState, vm.onlineRewards, context.activity(), vm::closeOnlineRewards,
            { vm.resumePendingOnline { id -> viewedId = id; screen = Screen.VIEWER } }, vm::discardPendingOnline)
    }
    LaunchedEffect(state.showOnlineRewards) { if (state.showOnlineRewards) screen = Screen.STUDIO }

    if (showDevelopmentNotice) {
        DevelopmentNoticeDialog(
            dontShowAgain = dontShowDevelopmentNotice,
            onDontShowAgainChange = { dontShowDevelopmentNotice = it },
            onDismiss = {
                if (dontShowDevelopmentNotice) previewPreferences.edit().putBoolean("hide_notice", true).apply()
                showDevelopmentNotice = false
                dontShowDevelopmentNotice = false
            },
            onSupport = {
                if (dontShowDevelopmentNotice) previewPreferences.edit().putBoolean("hide_notice", true).apply()
                showDevelopmentNotice = false
                dontShowDevelopmentNotice = false
                openSupport()
            }
        )
    }

    legalDocument?.let { document ->
        LegalDialog(
            title = stringResource(if (document == LegalDocument.PRIVACY) R.string.privacy_policy else R.string.terms_of_use),
            body = stringResource(if (document == LegalDocument.PRIVACY) R.string.privacy_body else R.string.terms_body),
            onDismiss = { legalDocument = null }
        )
    }

    if (state.showOnlineDisclosure) {
        AlertDialog(
            onDismissRequest = vm::dismissOnlineDisclosure,
            title = { Text(stringResource(R.string.online_disclosure_title)) },
            text = { Text(stringResource(R.string.online_disclosure_body)) },
            confirmButton = { Button(onClick = vm::acceptOnlineDisclosure) { Text(stringResource(R.string.continue_label)) } },
            dismissButton = { TextButton(onClick = vm::dismissOnlineDisclosure) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (state.showSupportPrompt) {
        AlertDialog(
            onDismissRequest = vm::dismissSupportPrompt,
            title = { Text(stringResource(R.string.support_developer)) },
            text = { Text(stringResource(R.string.support_prompt_body)) },
            confirmButton = { Button(onClick = { vm.dismissSupportPrompt(); openSupport() }) { Text(stringResource(R.string.support_developer)) } },
            dismissButton = { TextButton(onClick = vm::dismissSupportPrompt) { Text(stringResource(R.string.not_now)) } }
        )
    }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError(); if (screen == Screen.PROCESSING) screen = Screen.STUDIO } }
    LaunchedEffect(state.processing) {
        if (state.processing) {
            observedProcessing = true
            screen = Screen.PROCESSING
        } else if (observedProcessing && screen == Screen.PROCESSING) {
            observedProcessing = false
            screen = if (state.error == null) Screen.HISTORY else Screen.STUDIO
        }
    }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        AppDrawer(
            close = { scope.launch { drawer.close() } },
            onModels = { screen = Screen.MODELS; scope.launch { drawer.close() } },
            onPrivacy = { legalDocument = LegalDocument.PRIVACY; scope.launch { drawer.close() } },
            onTerms = { legalDocument = LegalDocument.TERMS; scope.launch { drawer.close() } },
            onLicenses = { screen = Screen.LICENSES; scope.launch { drawer.close() } },
            darkTheme = darkTheme,
            onToggleTheme = onToggleTheme
        )
    }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (screen != Screen.PROCESSING) BottomNav(screen) { destination ->
                if (destination == Screen.STUDIO) vm.resetStudio()
                screen = destination
            } },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.STUDIO -> StudioScreen(
                        state = state, amplitudes = vm.amplitudes.collectAsState().value,
                        openDrawer = { scope.launch { drawer.open() } },
                        onFiles = { filePicker.launch(arrayOf("audio/*", "video/*")) },
                        onRecord = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onPause = vm::togglePause,
                        onDiscard = vm::discardCurrentRecording,
                        onStop = { vm.stopAndTranscribe({ screen = Screen.PROCESSING }) { id -> viewedId = id; screen = Screen.VIEWER } },
                        setLanguage = vm::setLanguage, setMethod = vm::setMethod,
                        installedModels = installedModels,
                        openModels = { screen = Screen.MODELS },
                        openOnlineRewards = vm::openOnlineRewards,
                        openDevelopmentNotice = {
                            dontShowDevelopmentNotice = false
                            showDevelopmentNotice = true
                        }
                    )
                    Screen.PROCESSING -> ProcessingScreen(state) { vm.cancelTranscription(); screen = Screen.STUDIO }
                    Screen.HISTORY -> HistoryScreen(history) { viewedId = it; screen = Screen.VIEWER }
                    Screen.MODELS -> ModelsScreen(installedModels, vm::refreshInstalledModels, vm::deleteModel)
                    Screen.VIEWER -> TranscriptScreen(
                        transcript = history.firstOrNull { it.id == viewedId },
                        back = { screen = Screen.HISTORY },
                        updateTitle = vm::updateTitle,
                        loadAudioPath = vm::audioPath,
                        delete = { transcript -> vm.deleteTranscript(transcript); screen = Screen.HISTORY },
                        showSummaryUnavailable = {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.summary_under_development)) }
                        }
                    )
                    Screen.LICENSES -> LegalScreen(stringResource(R.string.licenses), stringResource(R.string.licenses_body)) { screen = Screen.STUDIO }
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(
    close: () -> Unit,
    onModels: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onLicenses: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localeMenu by remember { mutableStateOf(false) }
    val locales = listOf("en" to "English", "az" to "Azərbaycan", "tr" to "Türkçe", "ru" to "Русский")
    ModalDrawerSheet(Modifier.fillMaxWidth(.84f)) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text("v${BuildConfig.VERSION_NAME} · ${stringResource(R.string.tagline)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    Surface(
                        Modifier.size(48.dp).clickable { localeMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Language, contentDescription = stringResource(R.string.app_language)) } }
                    DropdownMenu(localeMenu, { localeMenu = false }, modifier = Modifier.width(190.dp)) {
                    locales.forEach { (tag, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { localeMenu = false; close(); scope.launch { delay(300); LocaleController.set(context, tag) } }) }
                    }
                }
                Surface(
                    Modifier.size(48.dp).clickable(onClick = onToggleTheme),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Box(contentAlignment = Alignment.Center) { Icon(if (darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, contentDescription = stringResource(R.string.toggle_theme)) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            DrawerRow(Icons.Outlined.ViewInAr, stringResource(R.string.models), onModels)
            DrawerRow(Icons.Outlined.PrivacyTip, stringResource(R.string.privacy_policy), onPrivacy)
            DrawerRow(Icons.Outlined.Info, stringResource(R.string.terms_of_use), onTerms)
            DrawerRow(Icons.Outlined.Info, stringResource(R.string.licenses), onLicenses)
            Spacer(Modifier.weight(1f))
            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kofe.al/@fuadismayil"))) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Outlined.Coffee, contentDescription = null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.support_developer))
            }
        }
    }
}

@Composable
private fun DevelopmentNoticeDialog(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSupport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
        title = { Text(stringResource(R.string.development_notice_title)) },
        text = {
            Column {
                Text(stringResource(R.string.development_notice_body))
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { onDontShowAgainChange(!dontShowAgain) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = onDontShowAgainChange)
                    Text(stringResource(R.string.dont_show_again))
                }
            }
        },
        confirmButton = { Button(onClick = onSupport) { Text(stringResource(R.string.support_developer)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun LegalDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                body,
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun LegalScreen(title: String, body: String, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cancel), back)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(body, modifier = Modifier.padding(top = 24.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun DrawerRow(icon: ImageVector, label: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)); Text(label)
    }
}

@Composable
private fun StudioScreen(
    state: StudioState, amplitudes: List<Float>, openDrawer: () -> Unit,
    onFiles: () -> Unit,
    onRecord: () -> Unit, onPause: () -> Unit, onDiscard: () -> Unit, onStop: () -> Unit,
    setLanguage: (String) -> Unit, setMethod: (EngineKind, ModelTier?, OnlineModel?) -> Unit,
    installedModels: Set<ModelTier>, openModels: () -> Unit,
    openDevelopmentNotice: () -> Unit,
    openOnlineRewards: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.Outlined.Menu, stringResource(R.string.more), openDrawer)
            Spacer(Modifier.weight(1f)); SegmentedHeader(onFiles); Spacer(Modifier.weight(1f)); CircleIconButton(Icons.Outlined.Build, stringResource(R.string.development_status), openDevelopmentNotice)
        }
        Spacer(Modifier.height(if (state.engine == EngineKind.ONLINE_WHISPER) 40.dp else 72.dp))
        Text(formatDuration(state.elapsedSeconds), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 48.sp)
        Text(if (state.recording) stringResource(R.string.recording) else stringResource(R.string.ready), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(50.dp))
        Waveform(Modifier.fillMaxWidth().height(120.dp), amplitudes, state.recording)
        if (state.partialText.isNotBlank()) Text(state.partialText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CircleControl(Icons.Outlined.DeleteSweep, stringResource(R.string.discard_recording), 64, state.recording || state.elapsedSeconds > 0, onDiscard)
            Box(Modifier.size(120.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).padding(14.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable { if (state.recording) onStop() else onRecord() }, contentAlignment = Alignment.Center) {
                Icon(if (state.recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (state.recording) stringResource(R.string.cancel) else stringResource(R.string.record), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp))
            }
            CircleControl(if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, stringResource(R.string.recording), 64) { if (state.recording) onPause() }
        }
        Spacer(Modifier.height(if (state.engine == EngineKind.ONLINE_WHISPER) 20.dp else 54.dp))
        SelectorCard(state, installedModels, setLanguage, setMethod, openModels)
        if (state.engine == EngineKind.ONLINE_WHISPER) {
            TextButton(onClick = openOnlineRewards, enabled = !state.recording && !state.processing) {
                Text(stringResource(R.string.online_reward_access))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun SegmentedHeader(onFiles: () -> Unit) {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(3.dp)) {
        Text(stringResource(R.string.record), Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp)).padding(horizontal = 22.dp, vertical = 10.dp), fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.files), Modifier.clickable(onClick = onFiles).padding(horizontal = 22.dp, vertical = 10.dp))
    }
}

@Composable private fun SelectorCard(state: StudioState, installedModels: Set<ModelTier>, setLanguage: (String) -> Unit, setMethod: (EngineKind, ModelTier?, OnlineModel?) -> Unit, openModels: () -> Unit) {
    var languageMenu by remember { mutableStateOf(false) }; var engineMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().liquidGlass().padding(18.dp)) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.clickable { languageMenu = true }) { Text(stringResource(R.string.language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Row(verticalAlignment = Alignment.CenterVertically) { Text(languageLabel(state.language), fontWeight = FontWeight.Medium, maxLines = 1); Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) } }
            DropdownMenu(languageMenu, { languageMenu = false }, modifier = Modifier.height(420.dp)) {
                WhisperLanguages.all.forEach { item -> DropdownMenuItem(text = { Text(languageLabel(item.code)) }, onClick = { setLanguage(item.code); languageMenu = false }) }
            }
        }
        Box(Modifier.height(46.dp).width(1.dp).background(MaterialTheme.colorScheme.outline)); Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f)) {
            Column(Modifier.clickable { engineMenu = true }) { Text(stringResource(R.string.method), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Row(verticalAlignment = Alignment.CenterVertically) { Text(methodLabel(state), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) } }
            DropdownMenu(engineMenu, { engineMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.online_mini)) }, onClick = { setMethod(EngineKind.ONLINE_WHISPER, null, OnlineModel.MINI); engineMenu = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.native_stt)) }, onClick = { setMethod(EngineKind.NATIVE_STT, null, null); engineMenu = false })
                if (installedModels.isNotEmpty()) HorizontalDivider()
                ModelTier.entries.filter { it in installedModels }.forEach { tier ->
                    DropdownMenuItem(text = { Text(tierTitle(tier)) }, onClick = { setMethod(EngineKind.OFFLINE_WHISPER, tier, null); engineMenu = false })
                }
                if (installedModels.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.download_offline_models)) }, onClick = { engineMenu = false; openModels() })
            }
        }
    }
}

@Composable private fun methodLabel(state: StudioState) = when (state.engine) {
    EngineKind.ONLINE_WHISPER -> stringResource(R.string.online_mini)
    EngineKind.OFFLINE_WHISPER -> tierTitle(state.modelTier)
    EngineKind.NATIVE_STT -> stringResource(R.string.native_stt)
}

@Composable private fun languageLabel(code: String): String = when (code) {
    "auto" -> stringResource(R.string.auto_detect)
    "en" -> stringResource(R.string.english)
    "az" -> stringResource(R.string.azerbaijani)
    "tr" -> stringResource(R.string.turkish)
    "ru" -> stringResource(R.string.russian)
    else -> WhisperLanguages.all.firstOrNull { it.code == code }?.name ?: code
}

@Composable private fun Waveform(modifier: Modifier = Modifier, samples: List<Float>, recording: Boolean = false) {
    val activeColor = MaterialTheme.colorScheme.onBackground
    val idleColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val gap = size.width / samples.size
        val hasAudio = recording && samples.any { it >= .008f }
        samples.forEachIndexed { index, value ->
            if (!hasAudio || value < .004f) {
                drawCircle(idleColor, 1.6.dp.toPx(), Offset(index * gap, size.height / 2))
            } else {
                val normalized = ((value - .004f) * 15f).coerceIn(.035f, .92f)
                val h = size.height * normalized
                drawLine(activeColor, Offset(index * gap, size.height / 2 - h / 2), Offset(index * gap, size.height / 2 + h / 2), 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable private fun ProcessingScreen(state: StudioState, cancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cancel), cancel); Spacer(Modifier.weight(1f)); TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) } }
        Spacer(Modifier.height(88.dp)); DottedProgress(state.processingProgress.coerceAtLeast(1)); Spacer(Modifier.weight(1f))
        InfoCard(state.sourceName); Spacer(Modifier.height(20.dp)); MetadataCard(state); Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.background_reassurance), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun DottedProgress(progress: Int) {
    val transition = rememberInfiniteTransition(label = "processing orbit")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "processing orbit angle"
    )
    val activeColor = MaterialTheme.colorScheme.onBackground
    val inactiveColor = MaterialTheme.colorScheme.outline
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .42f
            repeat(48) { i ->
                val angle = i / 48f * 2f * PI.toFloat() - PI.toFloat() / 2
                val active = i / 48f * 100 <= progress
                drawCircle(if (active) activeColor else inactiveColor, if (active) 3.2.dp.toPx() else 2.2.dp.toPx(), Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius))
            }
            drawCircle(
                color = activeColor,
                radius = 5.5.dp.toPx(),
                center = Offset(center.x + cos(orbit - PI.toFloat() / 2) * radius, center.y + sin(orbit - PI.toFloat() / 2) * radius)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$progress%", fontSize = 48.sp, fontWeight = FontWeight.Medium); Text(stringResource(R.string.transcribing)) }
    }
}

@Composable private fun InfoCard(name: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(16.dp)); Column { Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Audio", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
@Composable private fun MetadataCard(state: StudioState) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Column(Modifier.padding(20.dp)) { MetadataRow(Icons.Outlined.MicNone, stringResource(R.string.engine), when(state.engine) { EngineKind.ONLINE_WHISPER -> stringResource(R.string.online_whisper); EngineKind.NATIVE_STT -> stringResource(R.string.native_stt); EngineKind.OFFLINE_WHISPER -> stringResource(R.string.offline_local) }); HorizontalDivider(Modifier.padding(vertical = 14.dp)); MetadataRow(Icons.Outlined.Memory, stringResource(R.string.model), methodLabel(state)); HorizontalDivider(Modifier.padding(vertical = 14.dp)); MetadataRow(Icons.Outlined.Language, stringResource(R.string.language), languageLabel(state.language)) } } }
@Composable private fun MetadataRow(icon: ImageVector, key: String, value: String) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(16.dp)); Column { Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Text(value, fontWeight = FontWeight.Medium) } } }

@Composable private fun HistoryScreen(history: List<TranscriptEntity>, open: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 18.dp, bottom = 14.dp))
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_history)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        val filtered = history.filter { query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true) }
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_history), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(filtered, key = { it.id }) { transcript ->
            Card(Modifier.fillMaxWidth().clickable { open(transcript.id) }, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) { Text(transcript.title, fontWeight = FontWeight.SemiBold); Text(formatDateTime(transcript.createdAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Spacer(Modifier.height(8.dp)); Text(transcript.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        } }
    }
}

@Composable
private fun TranscriptScreen(
    transcript: TranscriptEntity?,
    back: () -> Unit,
    updateTitle: (TranscriptEntity, String) -> Unit,
    loadAudioPath: suspend (Long) -> String?,
    delete: (TranscriptEntity) -> Unit,
    showSummaryUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val item = transcript ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_history)) }
    var editTitle by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var draftTitle by remember(item.title) { mutableStateOf(item.title) }
    var exportMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var audioPath by remember(item.id) { mutableStateOf<String?>(null) }
    var audioLoaded by remember(item.id) { mutableStateOf(false) }
    var player by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var playerReady by remember(item.id) { mutableStateOf(false) }
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var positionMs by remember(item.id) { mutableIntStateOf(0) }
    var durationMs by remember(item.id) { mutableIntStateOf(item.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }

    LaunchedEffect(item.id) {
        audioPath = loadAudioPath(item.id)
        audioLoaded = true
    }
    DisposableEffect(audioPath) {
        val media = audioPath?.takeIf { File(it).isFile }?.let { path ->
            runCatching {
                MediaPlayer().apply {
                    setDataSource(path)
                    setOnPreparedListener {
                        durationMs = it.duration.coerceAtLeast(0)
                        playerReady = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        positionMs = it.duration.coerceAtLeast(0)
                    }
                    setOnErrorListener { _, _, _ ->
                        playerReady = false
                        isPlaying = false
                        false
                    }
                    prepareAsync()
                }
            }.getOrNull()
        }
        player = media
        onDispose {
            if (player === media) player = null
            media?.release()
        }
    }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            positionMs = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(200)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.history), back)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Outlined.Edit, stringResource(R.string.edit)) { editTitle = true }
            CircleIconButton(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) { confirmDelete = true }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineLarge)
            Text(formatDateTime(item.createdAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.transcript), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    stringResource(R.string.summary),
                    Modifier.weight(1f).clickable(onClick = showSummaryUnavailable),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
                )
            }
            HorizontalDivider(Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(20.dp))
            Text(item.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(30.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play)) {
                            val activePlayer = player
                            if (playerReady && activePlayer != null) {
                                if (isPlaying) activePlayer.pause() else {
                                    if (positionMs >= durationMs - 250) activePlayer.seekTo(0)
                                    activePlayer.start()
                                }
                                isPlaying = !isPlaying
                            }
                        }
                        Slider(
                            value = positionMs.coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
                            onValueChange = { value ->
                                positionMs = value.toInt()
                                if (playerReady) player?.seekTo(positionMs)
                            },
                            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                            enabled = playerReady,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )
                        Box {
                            Surface(
                                modifier = Modifier.width(58.dp).height(40.dp).clickable(enabled = playerReady) { speedMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) { Box(contentAlignment = Alignment.Center) { Text(formatSpeed(playbackSpeed), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } }
                            DropdownMenu(speedMenu, { speedMenu = false }) {
                                listOf(1f, 1.5f, 2f).forEach { speed ->
                                    DropdownMenuItem(text = { Text(formatSpeed(speed)) }, onClick = {
                                        speedMenu = false
                                        playbackSpeed = speed
                                        runCatching { player?.playbackParams = PlaybackParams().setSpeed(speed) }
                                    })
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(positionMs / 1_000L), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(durationMs / 1_000L), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (audioLoaded && audioPath == null) Text(stringResource(R.string.audio_unavailable), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(18.dp).liquidGlass().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Action(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) { clipboard.setText(AnnotatedString(item.text)) }
            Action(Icons.Outlined.IosShare, stringResource(R.string.share)) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, item.text), null)) }
            Action(Icons.Outlined.Edit, stringResource(R.string.edit)) { editTitle = true }
            Box { Action(Icons.Outlined.UploadFile, stringResource(R.string.export)) { exportMenu = true }; DropdownMenu(exportMenu, { exportMenu = false }) {
                listOf("TXT" to "text/plain", "SRT" to "application/x-subrip", "PDF" to "application/pdf").forEach { (format, mime) -> DropdownMenuItem(text = { Text(format) }, onClick = { exportMenu = false; val file = when(format) { "SRT" -> TranscriptExporter.subtitles(context, item); "PDF" -> TranscriptExporter.pdf(context, item); else -> TranscriptExporter.text(context, item) }; shareFile(context, file, mime) }) }
            } }
        }
    }
    if (editTitle) AlertDialog(onDismissRequest = { editTitle = false }, title = { Text(stringResource(R.string.edit)) }, text = { OutlinedTextField(draftTitle, { draftTitle = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { updateTitle(item, draftTitle); editTitle = false }) { Text(stringResource(R.string.continue_label)) } }, dismissButton = { TextButton(onClick = { editTitle = false }) { Text(stringResource(R.string.cancel)) } })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_transcript_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; delete(item) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable private fun ModelsScreen(installedModels: Set<ModelTier>, onInstalled: () -> Unit, onDelete: (ModelTier) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ModelDownloadManager(java.io.File(context.filesDir, "models")) }
    val recommended = remember { DeviceMemoryProfiler.recommend(DeviceMemoryProfiler.totalMemory(context)) }
    var details by remember { mutableStateOf(false) }; var showDisclaimer by remember { mutableStateOf(false) }
    var selectedTier by remember { mutableStateOf(recommended) }
    var downloading by remember { mutableStateOf<ModelTier?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ModelTier?>(null) }
    fun request(tier: ModelTier) { selectedTier = tier; showDisclaimer = true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text(stringResource(R.string.offline_models), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), textAlign = TextAlign.Center)
        Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recommended_device), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(tierTitle(recommended), style = MaterialTheme.typography.headlineMedium)
                    Text(tierDescription(recommended))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { if (recommended in installedModels) pendingDelete = recommended else request(recommended) },
                        enabled = downloading == null
                    ) {
                        Icon(
                            if (recommended in installedModels) Icons.Outlined.DeleteOutline else Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (recommended in installedModels) R.string.delete else R.string.download))
                    }
                }
                Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(68.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        ModelTier.entries.reversed().forEach { tier ->
            ModelCard(
                tier = tier,
                recommended = tier == recommended,
                installed = tier in installedModels,
                progress = if (downloading == tier) downloadProgress else null,
                download = { request(tier) },
                delete = { pendingDelete = tier }
            )
            Spacer(Modifier.height(10.dp))
        }
        downloadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        Row(Modifier.fillMaxWidth().clickable { details = !details }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.technical_details)); Spacer(Modifier.weight(1f)); Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null) }
        AnimatedVisibility(details) { Text("Quantization: Q5_1 · 16 kHz mono · C++20 SIMD · Estimated real-time factor varies by CPU and thermal state.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
    }
    if (showDisclaimer) AlertDialog(onDismissRequest = { showDisclaimer = false }, title = { Text(stringResource(R.string.offline_disclaimer_title)) }, text = { Text(stringResource(R.string.offline_disclaimer)) }, confirmButton = { TextButton(onClick = { showDisclaimer = false; downloading = selectedTier; downloadProgress = 0; downloadError = null; scope.launch { manager.download(ModelCatalog.spec(selectedTier)) { done, total -> downloadProgress = if (total > 0) (done * 100 / total).toInt() else 0 }.onSuccess { onInstalled() }.onFailure { downloadError = it.message }; downloading = null } }) { Text(stringResource(R.string.continue_label)) } }, dismissButton = { TextButton(onClick = { showDisclaimer = false }) { Text(stringResource(R.string.cancel)) } })
    pendingDelete?.let { tier ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_model_title)) },
            text = { Text(stringResource(R.string.delete_model_confirm, tierTitle(tier))) },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(tier) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable private fun ModelCard(tier: ModelTier, recommended: Boolean, installed: Boolean, progress: Int?, download: () -> Unit, delete: () -> Unit) { Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(tierIcon(tier), contentDescription = null, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Row { Text(tierTitle(tier), fontWeight = FontWeight.SemiBold); if (recommended) Text("  ${stringResource(R.string.recommended)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }; Text(tierDescription(tier), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (progress != null) "$progress%" else "~${tier.downloadBytes / 1024 / 1024} MB", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }; CircleIconButton(if (installed) Icons.Outlined.DeleteOutline else Icons.Outlined.Download, if (installed) stringResource(R.string.delete_model_title) else stringResource(R.string.download)) { if (installed) delete() else if (progress == null) download() } } } }
private fun tierIcon(tier: ModelTier): ImageVector = when(tier) { ModelTier.MAXIMUM_QUALITY -> Icons.Outlined.HighQuality; ModelTier.BEST_BALANCE -> Icons.Outlined.Balance; ModelTier.MAXIMUM_SPEED -> Icons.Outlined.Speed }
@Composable private fun tierTitle(tier: ModelTier) = when(tier){ ModelTier.MAXIMUM_QUALITY -> stringResource(R.string.maximum_quality); ModelTier.BEST_BALANCE -> stringResource(R.string.best_balance); ModelTier.MAXIMUM_SPEED -> stringResource(R.string.maximum_speed) }
@Composable private fun tierDescription(tier: ModelTier) = when(tier){ ModelTier.MAXIMUM_QUALITY -> stringResource(R.string.max_quality_desc); ModelTier.BEST_BALANCE -> stringResource(R.string.balance_desc); ModelTier.MAXIMUM_SPEED -> stringResource(R.string.max_speed_desc) }

@Composable private fun BottomNav(selected: Screen, action: (Screen) -> Unit) { Surface(Modifier.navigationBarsPadding(), shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) { NavItem(Icons.Outlined.MicNone, stringResource(R.string.transcribe), selected == Screen.STUDIO) { action(Screen.STUDIO) }; NavItem(Icons.Outlined.History, stringResource(R.string.history), selected == Screen.HISTORY || selected == Screen.VIEWER) { action(Screen.HISTORY) }; NavItem(Icons.Outlined.ViewInAr, stringResource(R.string.models), selected == Screen.MODELS) { action(Screen.MODELS) } } } }
@Composable private fun NavItem(icon: ImageVector, label: String, active: Boolean, action: () -> Unit) { Column(Modifier.clickable(onClick = action).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) { val color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant; Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = color); Text(label, fontSize = 11.sp, fontWeight = if(active) FontWeight.SemiBold else FontWeight.Normal, color = color) } }
@Composable private fun CircleIconButton(icon: ImageVector, description: String, action: () -> Unit) { Box(Modifier.size(44.dp).clickable(onClick = action), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(25.dp)) } }
@Composable private fun CircleControl(icon: ImageVector, description: String, size: Int, enabled: Boolean = true, action: () -> Unit) { Surface(Modifier.size(size.dp).clickable(enabled = enabled, onClick = action), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 4.dp) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(27.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .28f)) } } }
@Composable private fun Action(icon: ImageVector, label: String, action: () -> Unit) { Column(Modifier.clickable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)); Spacer(Modifier.height(4.dp)); Text(label, fontSize = 10.sp) } }
private fun formatDuration(seconds: Long): String = if (seconds >= 3_600) {
    "%02d:%02d:%02d".format(seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
} else {
    "%02d:%02d".format(seconds / 60, seconds % 60)
}
private fun formatDateTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
private fun formatSpeed(speed: Float): String = if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"
private fun shareFile(context: android.content.Context, file: java.io.File, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, TranscriptExporter.uri(context, file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, file.name))
}
