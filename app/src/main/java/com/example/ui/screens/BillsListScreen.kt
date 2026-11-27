package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.BillStatus
import com.example.ui.components.BillSummaryCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.viewmodel.BillCircleUiState

private enum class BillFilter { ALL, PENDING, DISPUTED, CLEARED }

@Composable
fun BillsListScreen(
    state: BillCircleUiState,
    onCreateBill: () -> Unit,
    onOpenBill: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(BillFilter.ALL) }

    val filteredBills = remember(state.bills, searchQuery, filter) {
        state.bills.filter { bill ->
            val matchesFilter = when (filter) {
                BillFilter.ALL -> true
                BillFilter.PENDING -> bill.status != BillStatus.CLEARED.name && bill.status != BillStatus.DISPUTED.name
                BillFilter.DISPUTED -> bill.status == BillStatus.DISPUTED.name
                BillFilter.CLEARED -> bill.status == BillStatus.CLEARED.name
            }
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                bill.title.lowercase().contains(q) ||
                bill.billCode.lowercase().contains(q) ||
                bill.groupName.lowercase().contains(q) ||
                bill.category.lowercase().contains(q) ||
                bill.merchantName.orEmpty().lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateBill,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(strings.createNewBill) },
                modifier = Modifier.testTag("bills_create_fab")
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(strings.billsTitle, style = MaterialTheme.typography.headlineMedium)
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("bills_search_input"),
                    placeholder = { Text("Search by title, Bill ID, group, merchant...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chips = listOf(
                        BillFilter.ALL to strings.all,
                        BillFilter.PENDING to strings.pending,
                        BillFilter.DISPUTED to strings.disputed,
                        BillFilter.CLEARED to strings.cleared
                    )
                    items(chips) { (f, label) ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(label) },
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        )
                    }
                }
            }
            if (filteredBills.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = strings.noBillsYet,
                        subtitle = strings.createFirstBillHint,
                        actionLabel = strings.createNewBill,
                        onAction = onCreateBill
                    )
                }
            } else {
                items(filteredBills, key = { it.id }) { bill ->
                    BillSummaryCard(bill = bill, onClick = { onOpenBill(bill.id) })
                }
            }
            item { Spacer(Modifier.height(84.dp)) }
        }
    }
}
