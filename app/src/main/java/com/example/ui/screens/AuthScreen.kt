package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.FirebaseDiagnosticState
import com.example.data.attemptAutoSignIn
import com.example.data.checkFirebaseConfigStatus
import com.example.data.onGoogleSignInClicked
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalBillCircleColors

@Composable
fun AuthScreen(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var authFailureState by remember { mutableStateOf<FirebaseDiagnosticState?>(null) }
    val firebaseStatus = remember(context) { checkFirebaseConfigStatus(context) }
    val activeDiagnostic = authFailureState ?: firebaseStatus.diagnosticState

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Show interactive Google Sign-In button */ },
            scope = scope
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Language selector pill strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SingleChoiceSegmentedButtonRow {
                    AppLanguage.entries.forEachIndexed { idx, lang ->
                        SegmentedButton(
                            selected = selectedLanguage == lang,
                            onClick = { onSelectLanguage(lang) },
                            shape = SegmentedButtonDefaults.itemShape(idx, AppLanguage.entries.size),
                            modifier = Modifier.testTag("auth_lang_${lang.code}")
                        ) {
                            Text(lang.nativeLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Hero Artwork Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(210.dp),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_billcircle_hero_1795699776433),
                        contentDescription = "BillCircle shared finance overview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        ext.heroStart.copy(alpha = 0.15f),
                                        ext.heroStart.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
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
                                    Icons.Filled.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(8.dp).size(22.dp)
                                )
                            }
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.displayMedium,
                                color = ext.heroOnSurface
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ext.heroOnSurface.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Key Trust Pillars
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuthFeatureRow(
                    Icons.Filled.Calculate,
                    "Exact Indian Rupee Splitting",
                    "Integer paise precision for Equal, Custom & Percentage splits"
                )
                AuthFeatureRow(
                    Icons.Filled.VerifiedUser,
                    "Role-Based Approvals & Receipts",
                    "Share receipts, verify UPI payments manually, and keep immutable audit trails"
                )
                AuthFeatureRow(
                    Icons.Filled.DocumentScanner,
                    "Gemini AI Bill Scanner",
                    "Scan bill photos to extract merchant, date, and totals for human review"
                )
            }

            when (activeDiagnostic) {
                FirebaseDiagnosticState.CONFIGURED_AND_CONNECTED -> {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ext.successContainer,
                        border = BorderStroke(1.dp, ext.success.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = ext.success,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Firebase configured & connected",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                FirebaseDiagnosticState.CONFIG_MISSING -> {
                    val configWarning = if (!firebaseStatus.firebaseAppInitialized) {
                        "Firebase configuration is missing in this build. Add app/google-services.json for com.aistudio.billcircle.vkmqrx (or set GOOGLE_SERVICES_JSON in GitHub Secrets) and rebuild."
                    } else {
                        "Google Sign-In Web Client ID (default_web_client_id) is missing. Enable Google Sign-In and add your signing SHA-1/SHA-256 in Firebase Console, then update google-services.json."
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                        colors = CardDefaults.cardColors(containerColor = ext.warningContainer),
                        border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = ext.warning)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = FirebaseDiagnosticState.CONFIG_MISSING.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = configWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                FirebaseDiagnosticState.NETWORK_UNAVAILABLE -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                        colors = CardDefaults.cardColors(containerColor = ext.warningContainer),
                        border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = ext.warning)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = FirebaseDiagnosticState.NETWORK_UNAVAILABLE.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = errorMessage ?: "No internet connection detected. Connect to Wi-Fi or mobile data to sign in with Google.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                FirebaseDiagnosticState.AUTHENTICATION_FAILED,
                FirebaseDiagnosticState.FIRESTORE_PERMISSION_DENIED -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = activeDiagnostic.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Primary Google Sign-In Button
            Button(
                onClick = {
                    errorMessage = null
                    authFailureState = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { diagState, msg ->
                            isLoading = false
                            authFailureState = diagState
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading && firebaseStatus.isFullyConfigured,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Signing in...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.signInWithGoogle, style = MaterialTheme.typography.titleMedium)
                }
            }

            Text(
                text = strings.signInSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AuthFeatureRow(icon: ImageVector, title: String, subtitle: String) {
    val ext = LocalBillCircleColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
