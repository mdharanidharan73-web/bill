package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.FirebaseCheckItem
import com.example.data.FirebaseInitState
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalBillCircleColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AdminSetupStep(
    val title: String,
    val detail: String
)

private val ADMIN_SETUP_STEPS = listOf(
    AdminSetupStep(
        title = "Register or select Android app in Firebase Console",
        detail = "Open console.firebase.google.com → Project Settings → Your apps and confirm package name ${BuildConfig.APPLICATION_ID}."
    ),
    AdminSetupStep(
        title = "Add signing SHA-1 & SHA-256 fingerprints",
        detail = "Under SHA certificate fingerprints, register the debug or release keystore SHA-1 and SHA-256 used to sign this APK."
    ),
    AdminSetupStep(
        title = "Enable Google Sign-In in Authentication",
        detail = "Go to Build → Authentication → Sign-in method → enable Google so Firebase generates default_web_client_id."
    ),
    AdminSetupStep(
        title = "Add genuine google-services.json or CI secret",
        detail = "Download google-services.json into app/google-services.json, or save its contents in GitHub Secret GOOGLE_SERVICES_JSON."
    ),
    AdminSetupStep(
        title = "Rebuild and reinstall BillCircle",
        detail = "Run a fresh build so the Google Services plugin packages your Firebase config, then tap Retry Verification above."
    )
)

@Composable
fun FirebaseSetupScreen(
    initState: FirebaseInitState,
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onRetryCheck: () -> FirebaseInitState
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isChecking by remember { mutableStateOf(false) }
    var isAdminGuideExpanded by rememberSaveable { mutableStateOf(false) }
    var completedSteps by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet<Int>()) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isAdminGuideExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "admin_chevron_rotation"
    )

    val isInvalidConfig = initState is FirebaseInitState.InvalidConfiguration
    val titleText = if (isInvalidConfig) strings.setupInvalidTitle else strings.setupRequiredTitle
    val subtitleText = if (isInvalidConfig) strings.setupInvalidSubtitle else strings.setupRequiredSubtitle
    val summaryReason = when (initState) {
        is FirebaseInitState.NotConfigured -> initState.summaryReason
        is FirebaseInitState.InvalidConfiguration -> initState.summaryReason
        is FirebaseInitState.Ready -> ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Language selector row (EN / KN / TA)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SingleChoiceSegmentedButtonRow {
                    AppLanguage.entries.forEachIndexed { idx, lang ->
                        SegmentedButton(
                            selected = selectedLanguage == lang,
                            onClick = { onSelectLanguage(lang) },
                            shape = SegmentedButtonDefaults.itemShape(idx, AppLanguage.entries.size),
                            modifier = Modifier.testTag("setup_lang_${lang.code}")
                        ) {
                            Text(lang.nativeLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Branded Deep Navy Hero Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(ext.heroStart, ext.heroEnd)))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalance,
                                    contentDescription = "BillCircle emblem",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(9.dp).size(22.dp)
                                )
                            }
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.titleLarge,
                                color = ext.heroOnSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ext.warningContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = ext.warning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isInvalidConfig) "Config Invalid" else "Setup Needed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.warning,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = ext.heroOnSurface
                    )

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.heroOnSurface.copy(alpha = 0.88f)
                    )

                    if (summaryReason.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ext.heroOnSurface.copy(alpha = 0.10f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = ext.heroOnSurface.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(top = 1.dp).size(18.dp)
                                )
                                Text(
                                    text = summaryReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.heroOnSurface.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }
                }
            }

            // Live Runtime Readiness Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Cloud Readiness Checks",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    initState.checks.forEach { check ->
                        ReadinessCheckRow(check = check)
                    }
                }
            }

            // Primary Retry Verification CTA
            Button(
                onClick = {
                    if (!isChecking) {
                        scope.launch {
                            isChecking = true
                            delay(320L)
                            val updated = onRetryCheck()
                            isChecking = false
                            if (updated !is FirebaseInitState.Ready) {
                                snackbarHostState.showSnackbar(
                                    message = strings.setupStillNeededFeedback,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                },
                enabled = !isChecking,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(56.dp)
                    .testTag("retry_firebase_check_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(strings.checkingSetup, style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.retryVerification, style = MaterialTheme.typography.titleMedium)
                }
            }

            // Expandable Administrator Setup Steps Accordion
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 60.dp)
                            .clickable { isAdminGuideExpanded = !isAdminGuideExpanded }
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .testTag("toggle_admin_setup_guide"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.adminSetupSteps,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = strings.adminSetupHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${completedSteps.size}/${ADMIN_SETUP_STEPS.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = if (isAdminGuideExpanded) "Collapse setup steps" else "Expand setup steps",
                            modifier = Modifier.rotate(chevronRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isAdminGuideExpanded,
                        enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = ext.cardBorder)

                            // Copyable Package ID strip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Android Application ID",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = BuildConfig.APPLICATION_ID,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            clipboard?.setPrimaryClip(
                                                ClipData.newPlainText("BillCircle Package ID", BuildConfig.APPLICATION_ID)
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = strings.packageCopied,
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("copy_package_id_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = "Copy Android package ID",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Interactive 5-Step Setup Checklist
                            ADMIN_SETUP_STEPS.forEachIndexed { index, step ->
                                val isDone = index in completedSteps
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDone) ext.successContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDone) ext.success.copy(alpha = 0.45f) else ext.cardBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            completedSteps = if (isDone) completedSteps - index else completedSteps + index
                                        }
                                        .testTag("setup_step_$index")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 56.dp)
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isDone,
                                            onCheckedChange = { checked ->
                                                completedSteps = if (checked) completedSteps + index else completedSteps - index
                                            }
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(top = 6.dp, end = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${index + 1}. ${step.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = step.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun ReadinessCheckRow(check: FirebaseCheckItem) {
    val ext = LocalBillCircleColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (check.passed) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
            contentDescription = if (check.passed) "${check.label} passed" else "${check.label} needs setup",
            tint = if (check.passed) ext.success else ext.warning,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (check.passed) ext.successContainer else ext.warningContainer
        ) {
            Text(
                text = if (check.passed) "OK" else "MISSING",
                style = MaterialTheme.typography.labelSmall,
                color = if (check.passed) ext.success else ext.warning,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
