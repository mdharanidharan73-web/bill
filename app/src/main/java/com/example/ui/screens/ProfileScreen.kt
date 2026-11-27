package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.BillCircleUiState
import com.example.ui.viewmodel.BillCircleViewModel

@Composable
fun ProfileScreen(
    state: BillCircleUiState,
    viewModel: BillCircleViewModel,
    onSignOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current
    val profile = state.profile

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var phone by remember(profile?.phoneNumber) { mutableStateOf(profile?.phoneNumber.orEmpty()) }
    var defaultUpiId by remember(profile?.defaultUpiId) { mutableStateOf(profile?.defaultUpiId.orEmpty()) }
    var defaultUpiName by remember(profile?.defaultUpiName) { mutableStateOf(profile?.defaultUpiName.orEmpty()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(strings.profileTitle, style = MaterialTheme.typography.headlineMedium)
        }

        // User Identity Header
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!profile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(62.dp).clip(CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(62.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile?.displayName?.ifBlank { "BillCircle Member" } ?: "BillCircle Member", style = MaterialTheme.typography.titleLarge)
                        Text(profile?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Editable Profile & Default UPI Details
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Personal & UPI Details", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number") },
                        placeholder = { Text("+91 98765 43210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = defaultUpiId,
                        onValueChange = { defaultUpiId = it },
                        label = { Text("Default UPI ID (for receiving group payments)") },
                        placeholder = { Text("yourname@okicici") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = defaultUpiName,
                        onValueChange = { defaultUpiName = it },
                        label = { Text("Default UPI Recipient Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.saveProfile(displayName, phone, defaultUpiId, defaultUpiName) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp).testTag("save_profile_button")
                    ) {
                        Text(strings.saveProfile)
                    }
                }
            }
        }

        // Language Selection: English, Kannada, Tamil
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppLanguage.entries.forEachIndexed { idx, lang ->
                            SegmentedButton(
                                selected = state.language == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                shape = SegmentedButtonDefaults.itemShape(idx, AppLanguage.entries.size),
                                modifier = Modifier.testTag("profile_lang_${lang.code}")
                            ) {
                                Text(lang.nativeLabel)
                            }
                        }
                    }
                }
            }
        }

        // Clearly Separated Sample Preview Mode Toggle
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(strings.demoModeLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Preview BillCircle with sample ₹30,000 family bill data without modifying your live Firestore database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.demoMode,
                        onCheckedChange = { viewModel.setDemoMode(it) },
                        modifier = Modifier.testTag("demo_mode_switch")
                    )
                }
            }
        }

        // Security & Privacy Info + Sign Out
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Security & Audit Guarantee", style = MaterialTheme.typography.titleMedium)
                    }
                    Surface(
                        color = if (state.firebaseDiagnostic == com.example.data.FirebaseDiagnosticState.CONFIGURED_AND_CONNECTED) {
                            ext.successContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (state.firebaseDiagnostic == com.example.data.FirebaseDiagnosticState.CONFIGURED_AND_CONNECTED) {
                                    Icons.Filled.CloudDone
                                } else {
                                    Icons.Filled.WarningAmber
                                },
                                contentDescription = null,
                                tint = if (state.firebaseDiagnostic == com.example.data.FirebaseDiagnosticState.CONFIGURED_AND_CONNECTED) {
                                    ext.success
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Firebase & Firestore: ${state.firebaseDiagnostic.label}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Text(
                        "• All private groups, bills, and receipts require Google Sign-In and server-enforced Firestore role checks.\n" +
                            "• BillCircle never collects UPI PINs, passwords, or OTPs.\n" +
                            "• Financial audit logs are append-only and cannot be erased.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.signOut)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
