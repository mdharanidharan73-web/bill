package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.FinancialFigureStyle
import com.example.ui.theme.LocalBillCircleColors
import com.example.ui.viewmodel.AiScanUiState
import com.example.ui.viewmodel.BillCircleUiState
import com.example.util.AttachmentHelper
import com.example.util.CurrencyAndSplitUtils
import com.example.util.PreparedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBillScreen(
    state: BillCircleUiState,
    onBack: () -> Unit,
    onGoToGroups: () -> Unit,
    onScanBillBitmap: (android.graphics.Bitmap) -> Unit,
    onResetAiScan: () -> Unit,
    onPublishBill: (NewBillDraftInput) -> Unit,
    onError: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val ext = LocalBillCircleColors.current

    val adminGroups = remember(state.groups, state.currentUserId) {
        state.groups.filter { state.currentUserId in it.adminUids && !it.archived }
    }

    var selectedGroupId by remember(adminGroups) { mutableStateOf(adminGroups.firstOrNull()?.id.orEmpty()) }
    val selectedGroup = adminGroups.firstOrNull { it.id == selectedGroupId }
    val groupMembers = remember(state.members, selectedGroupId) {
        state.members.filter { it.groupId == selectedGroupId && it.status == "ACTIVE" }
    }

    var billCode by remember { mutableStateOf(CurrencyAndSplitUtils.generateBillCode()) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Utilities") }
    var merchantName by remember { mutableStateOf("") }
    var totalInrText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var billDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var dueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 7L * 86_400_000L) }
    var splitType by remember { mutableStateOf(SplitType.EQUAL) }
    var selectedMemberUids by remember(groupMembers) { mutableStateOf(groupMembers.map { it.userId }.toSet()) }
    var customAmountsText by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var customPercentText by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var attachedBillFile by remember { mutableStateOf<PreparedAttachment?>(null) }
    var aiScannedFlag by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmAsDraft by remember { mutableStateOf(false) }

    val totalPaise = remember(totalInrText) { CurrencyAndSplitUtils.parseRupeesToPaise(totalInrText) ?: 0L }
    val chosenMembers = remember(groupMembers, selectedMemberUids) {
        groupMembers.filter { it.userId in selectedMemberUids }
    }

    val computedShares: Map<String, Long> = remember(totalPaise, chosenMembers, splitType, customAmountsText, customPercentText) {
        when (splitType) {
            SplitType.EQUAL -> CurrencyAndSplitUtils.splitEqually(totalPaise, chosenMembers.map { it.userId })
            SplitType.CUSTOM -> chosenMembers.associate { m ->
                val raw = customAmountsText[m.userId].orEmpty()
                m.userId to (CurrencyAndSplitUtils.parseRupeesToPaise(raw) ?: 0L)
            }
            SplitType.PERCENTAGE -> {
                val bpsMap = chosenMembers.associate { m ->
                    val pct = customPercentText[m.userId]?.toDoubleOrNull() ?: 0.0
                    m.userId to (pct * 100.0).toInt().coerceIn(0, 10000)
                }
                CurrencyAndSplitUtils.splitByPercentageBps(totalPaise, bpsMap)
            }
        }
    }

    val computedBps: Map<String, Int> = remember(chosenMembers, splitType, customPercentText, computedShares, totalPaise) {
        if (chosenMembers.isEmpty()) emptyMap()
        else when (splitType) {
            SplitType.PERCENTAGE -> chosenMembers.associate { m ->
                val pct = customPercentText[m.userId]?.toDoubleOrNull() ?: 0.0
                m.userId to (pct * 100.0).toInt().coerceIn(0, 10000)
            }
            else -> chosenMembers.associate { m ->
                val share = computedShares[m.userId] ?: 0L
                val bps = if (totalPaise > 0L) ((share * 10000L) / totalPaise).toInt().coerceIn(0, 10000) else 0
                m.userId to bps
            }
        }
    }

    val splitSumPaise = computedShares.values.sum()
    val splitValid = totalPaise > 0L && chosenMembers.isNotEmpty() && splitSumPaise == totalPaise

    // Zero-permission Photo Picker for AI Bill Scanner
    val aiPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmapRes = withContext(Dispatchers.IO) { AttachmentHelper.decodeBitmapForAi(context, uri) }
                val attachRes = withContext(Dispatchers.IO) { AttachmentHelper.prepareFromUri(context, uri) }
                attachRes.onSuccess { attachedBillFile = it }
                bitmapRes.onSuccess { bmp -> onScanBillBitmap(bmp) }
                    .onFailure { err -> onError(err.localizedMessage ?: "Unable to read selected image.") }
            }
        }
    }

    // Zero-permission Document Picker for Original Bill (JPG/PNG/PDF)
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) { AttachmentHelper.prepareFromUri(context, uri) }
                res.onSuccess { attachedBillFile = it }
                    .onFailure { onError(it.localizedMessage ?: "Failed to attach file.") }
            }
        }
    }

    fun pickDate(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.createNewBill) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_bill_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        if (adminGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Filled.GroupAdd,
                    title = "Create a Group First",
                    subtitle = "Only group administrators can create and publish shared bills. Create your family or team group first.",
                    actionLabel = strings.createGroup,
                    onAction = onGoToGroups
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Gemini AI Bill Scanner Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.scanBillWithAi, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Extract merchant, total, date & tax for human verification before saving.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        when (val ai = state.aiScanState) {
                            AiScanUiState.Idle -> {
                                Button(
                                    onClick = {
                                        aiPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("scan_bill_ai_button")
                                ) {
                                    Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pick Bill Photo to Scan")
                                }
                            }
                            AiScanUiState.Scanning -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                    Text("Gemini AI is reading bill details...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is AiScanUiState.Error -> {
                                Text(ai.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = onResetAiScan) { Text("Dismiss & Retry") }
                            }
                            is AiScanUiState.ReadyForReview -> {
                                val r = ai.result
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Review Extracted Details (Human Verification Required)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        if (r.merchantName.isNotBlank()) Text("Merchant: ${r.merchantName}", style = MaterialTheme.typography.bodyMedium)
                                        if (r.totalAmountInr.isNotBlank()) Text("Total: ₹${r.totalAmountInr}", style = MaterialTheme.typography.titleSmall)
                                        if (r.billDate.isNotBlank()) Text("Bill Date: ${r.billDate}", style = MaterialTheme.typography.bodySmall)
                                        if (r.taxAmountInr.isNotBlank()) Text("Tax/GST: ₹${r.taxAmountInr}", style = MaterialTheme.typography.bodySmall)
                                        if (r.lineItems.isNotEmpty()) {
                                            Text("Items: ${r.lineItems.take(4).joinToString(" • ")}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(r.confidenceNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = onResetAiScan,
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Discard") }
                                            Button(
                                                onClick = {
                                                    if (r.merchantName.isNotBlank()) {
                                                        merchantName = r.merchantName
                                                        if (title.isBlank()) title = "${r.merchantName} Bill"
                                                    }
                                                    if (r.totalAmountInr.isNotBlank()) totalInrText = r.totalAmountInr
                                                    if (r.category in BILL_CATEGORIES) category = r.category
                                                    if (r.lineItems.isNotEmpty()) {
                                                        description = buildString {
                                                            if (r.taxAmountInr.isNotBlank()) appendLine("Tax/GST: ₹${r.taxAmountInr}")
                                                            append(r.lineItems.joinToString("\n"))
                                                        }
                                                    }
                                                    if (r.billDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                                                        try {
                                                            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(r.billDate)
                                                            if (parsed != null) billDateMillis = parsed.time
                                                        } catch (_: Exception) { }
                                                    }
                                                    aiScannedFlag = true
                                                    onResetAiScan()
                                                },
                                                modifier = Modifier.weight(1f).testTag("apply_ai_scan_button")
                                            ) { Text("Apply to Form") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Group Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Group", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(adminGroups, key = { it.id }) { grp ->
                            FilterChip(
                                selected = grp.id == selectedGroupId,
                                onClick = { selectedGroupId = grp.id },
                                label = { Text(grp.name) }
                            )
                        }
                    }
                }
            }

            // 3. Core Bill Info
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bill Information", style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                label = billCode,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        }

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Bill Title *") },
                            placeholder = { Text("e.g., Monthly Electricity Bill") },
                            modifier = Modifier.fillMaxWidth().testTag("bill_title_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = totalInrText,
                            onValueChange = { totalInrText = it },
                            label = { Text("Total Amount (₹ INR) *") },
                            placeholder = { Text("e.g., 30000") },
                            prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("bill_amount_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = merchantName,
                            onValueChange = { merchantName = it },
                            label = { Text("Merchant / Supplier") },
                            placeholder = { Text("e.g., BESCOM, Prestige RWA") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Category", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(BILL_CATEGORIES) { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickDate(billDateMillis) { billDateMillis = it } },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Bill: ${CurrencyAndSplitUtils.formatDate(billDateMillis)}", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { pickDate(dueDateMillis) { dueDateMillis = it } },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Due: ${CurrencyAndSplitUtils.formatDate(dueDateMillis)}", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description / Line Items") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Optional Notes for Members") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Original Bill Upload
                        OutlinedButton(
                            onClick = { docPicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("upload_original_bill_button")
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                attachedBillFile?.let { "Attached: ${it.fileName} (${it.fileSizeBytes / 1024} KB)" }
                                    ?: "Attach Original Bill (JPG, PNG, PDF)"
                            )
                        }
                    }
                }
            }

            // 4. Member Selection & Split Mode
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Split & Members (${chosenMembers.size}/${groupMembers.size})", style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    selectedMemberUids = if (selectedMemberUids.size == groupMembers.size) {
                                        emptySet()
                                    } else {
                                        groupMembers.map { it.userId }.toSet()
                                    }
                                }
                            ) {
                                Text(if (selectedMemberUids.size == groupMembers.size) "Clear All" else "Select All")
                            }
                        }

                        // Split Type Segmented Buttons
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val modes = listOf(
                                SplitType.EQUAL to strings.equalSplit,
                                SplitType.CUSTOM to strings.customSplit,
                                SplitType.PERCENTAGE to strings.percentageSplit
                            )
                            modes.forEachIndexed { idx, (mode, lbl) ->
                                SegmentedButton(
                                    selected = splitType == mode,
                                    onClick = { splitType = mode },
                                    shape = SegmentedButtonDefaults.itemShape(idx, modes.size)
                                ) {
                                    Text(lbl, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        groupMembers.forEach { member ->
                            val checked = member.userId in selectedMemberUids
                            val share = computedShares[member.userId] ?: 0L
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMemberUids = if (checked) selectedMemberUids - member.userId
                                        else selectedMemberUids + member.userId
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedMemberUids = if (isChecked) selectedMemberUids + member.userId
                                                else selectedMemberUids - member.userId
                                            }
                                        )
                                        Column {
                                            Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(member.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        text = if (checked) CurrencyAndSplitUtils.formatInr(share) else "—",
                                        style = FinancialFigureStyle,
                                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (checked && splitType == SplitType.CUSTOM) {
                                    OutlinedTextField(
                                        value = customAmountsText[member.userId].orEmpty(),
                                        onValueChange = { v -> customAmountsText = customAmountsText + (member.userId to v) },
                                        label = { Text("Custom ₹ for ${member.displayName}") },
                                        prefix = { Text("₹ ") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 4.dp)
                                    )
                                }
                                if (checked && splitType == SplitType.PERCENTAGE) {
                                    OutlinedTextField(
                                        value = customPercentText[member.userId].orEmpty(),
                                        onValueChange = { v -> customPercentText = customPercentText + (member.userId to v) },
                                        label = { Text("Percentage % for ${member.displayName}") },
                                        suffix = { Text("%") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 4.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        // Exact Paise Verification Footer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Calculated Split Total", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = CurrencyAndSplitUtils.formatInr(splitSumPaise),
                                style = FinancialFigureStyle,
                                color = if (splitValid) ext.success else MaterialTheme.colorScheme.error
                            )
                        }
                        if (totalPaise > 0L && splitSumPaise != totalPaise) {
                            Text(
                                text = "Difference: ${CurrencyAndSplitUtils.formatInr(totalPaise - splitSumPaise)}. Shares must add up exactly to ${CurrencyAndSplitUtils.formatInr(totalPaise)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 5. Review & Publish Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (title.isBlank() || !splitValid) {
                                onError("Please enter a title, valid amount, and exact split shares.")
                            } else {
                                confirmAsDraft = true
                                showConfirmDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 54.dp).testTag("save_draft_button")
                    ) {
                        Text(strings.saveDraft)
                    }
                    Button(
                        onClick = {
                            if (title.isBlank() || !splitValid) {
                                onError("Please enter a title, valid amount, and exact split shares.")
                            } else {
                                confirmAsDraft = false
                                showConfirmDialog = true
                            }
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1.3f).defaultMinSize(minHeight = 54.dp).testTag("review_publish_bill_button")
                    ) {
                        Text(strings.reviewAndPublish)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Full Confirmation Dialog before Publishing
    if (showConfirmDialog && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if (confirmAsDraft) "Confirm Draft Bill" else "Confirm & Publish Bill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$title ($billCode)", fontWeight = FontWeight.Bold)
                    Text("Group: ${selectedGroup.name} • Category: $category")
                    Text("Total: ${CurrencyAndSplitUtils.formatInr(totalPaise)} (${splitType.name})")
                    Text("Due Date: ${CurrencyAndSplitUtils.formatDate(dueDateMillis)}")
                    HorizontalDivider()
                    Text("Individual Shares:", fontWeight = FontWeight.SemiBold)
                    chosenMembers.forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(m.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                CurrencyAndSplitUtils.formatInr(computedShares[m.userId] ?: 0L),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onPublishBill(
                            NewBillDraftInput(
                                group = selectedGroup,
                                title = title,
                                billCode = billCode,
                                category = category,
                                merchantName = merchantName,
                                description = description,
                                notes = notes,
                                totalPaise = totalPaise,
                                splitType = splitType,
                                billDateMillis = billDateMillis,
                                dueDateMillis = dueDateMillis,
                                selectedMembers = chosenMembers,
                                sharePaiseByMemberUid = computedShares,
                                percentageBpsByMemberUid = computedBps,
                                saveAsDraft = confirmAsDraft,
                                aiScanned = aiScannedFlag,
                                initialAttachment = attachedBillFile
                            )
                        )
                    },
                    modifier = Modifier.testTag("confirm_publish_button")
                ) {
                    Text(if (confirmAsDraft) strings.saveDraft else strings.publishBill)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Edit") }
            }
        )
    }
}
