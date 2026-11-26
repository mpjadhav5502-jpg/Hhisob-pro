package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BillEntity
import com.example.data.local.ShopSettingsEntity
import com.example.domain.BillingCalculator
import com.example.pdf.BillPdfGenerator
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.DisplayFontFamily
import java.util.Calendar

enum class HistoryDateFilter {
    ALL,
    TODAY,
    THIS_MONTH,
    CUSTOM_DATE
}

enum class HistoryStatusFilter {
    ALL,
    PAID,
    PENDING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    bills: List<BillEntity>,
    settings: ShopSettingsEntity,
    initialOnlyPending: Boolean,
    onClearInitialPending: () -> Unit,
    onOpenBill: (BillEntity) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(HistoryDateFilter.ALL) }
    var customDateMillis by remember { mutableStateOf<Long?>(null) }
    var statusFilter by remember(initialOnlyPending) {
        mutableStateOf(if (initialOnlyPending) HistoryStatusFilter.PENDING else HistoryStatusFilter.ALL)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val filteredBills = remember(bills, searchQuery, dateFilter, customDateMillis, statusFilter) {
        val q = searchQuery.trim().lowercase()
        val nowCal = Calendar.getInstance()
        val todayYr = nowCal.get(Calendar.YEAR)
        val todayDay = nowCal.get(Calendar.DAY_OF_YEAR)
        val thisMonth = nowCal.get(Calendar.MONTH)

        val customCal = customDateMillis?.let {
            Calendar.getInstance().apply { timeInMillis = it }
        }

        bills.filter { bill ->
            val matchesSearch = q.isEmpty() ||
                bill.customerName.lowercase().contains(q) ||
                bill.customerMobile.contains(q) ||
                bill.billNumber.lowercase().contains(q)

            val bCal = Calendar.getInstance().apply { timeInMillis = bill.billTimestampMillis }
            val matchesDate = when (dateFilter) {
                HistoryDateFilter.ALL -> true
                HistoryDateFilter.TODAY ->
                    bCal.get(Calendar.YEAR) == todayYr && bCal.get(Calendar.DAY_OF_YEAR) == todayDay
                HistoryDateFilter.THIS_MONTH ->
                    bCal.get(Calendar.YEAR) == todayYr && bCal.get(Calendar.MONTH) == thisMonth
                HistoryDateFilter.CUSTOM_DATE ->
                    customCal == null || (
                        bCal.get(Calendar.YEAR) == customCal.get(Calendar.YEAR) &&
                            bCal.get(Calendar.DAY_OF_YEAR) == customCal.get(Calendar.DAY_OF_YEAR)
                        )
            }

            val matchesStatus = when (statusFilter) {
                HistoryStatusFilter.ALL -> true
                HistoryStatusFilter.PAID -> bill.pendingAmount == 0
                HistoryStatusFilter.PENDING -> bill.pendingAmount > 0
            }

            matchesSearch && matchesDate && matchesStatus
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "बिल इतिहास (Bill History)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ग्राहक नाव, मोबाईल किंवा बिल क्र. शोधा...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "शोध साफ करा")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        // Date Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = dateFilter == HistoryDateFilter.ALL,
                onClick = { dateFilter = HistoryDateFilter.ALL },
                label = { Text("सर्व तारखा") }
            )
            FilterChip(
                selected = dateFilter == HistoryDateFilter.TODAY,
                onClick = { dateFilter = HistoryDateFilter.TODAY },
                label = { Text("आज") }
            )
            FilterChip(
                selected = dateFilter == HistoryDateFilter.THIS_MONTH,
                onClick = { dateFilter = HistoryDateFilter.THIS_MONTH },
                label = { Text("हा महिना") }
            )
            FilterChip(
                selected = dateFilter == HistoryDateFilter.CUSTOM_DATE,
                onClick = { showDatePicker = true },
                leadingIcon = {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        if (dateFilter == HistoryDateFilter.CUSTOM_DATE && customDateMillis != null) {
                            BillingCalculator.formatDateOnly(customDateMillis!!)
                        } else {
                            "तारीख निवडा"
                        }
                    )
                }
            )
        }

        // Status Filter Row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = statusFilter == HistoryStatusFilter.ALL,
                onClick = {
                    statusFilter = HistoryStatusFilter.ALL
                    onClearInitialPending()
                },
                label = { Text("सर्व (${bills.size})") }
            )
            FilterChip(
                selected = statusFilter == HistoryStatusFilter.PAID,
                onClick = {
                    statusFilter = HistoryStatusFilter.PAID
                    onClearInitialPending()
                },
                label = { Text("पूर्ण जमा") }
            )
            FilterChip(
                selected = statusFilter == HistoryStatusFilter.PENDING,
                onClick = { statusFilter = HistoryStatusFilter.PENDING },
                label = {
                    val pCount = bills.count { it.pendingAmount > 0 }
                    Text("बाकी ($pCount)")
                }
            )
        }

        if (filteredBills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (bills.isEmpty()) {
                            "अद्याप कोणतेही बिल जतन केलेले नाही"
                        } else {
                            "निवडलेल्या फिल्टरनुसार बिले सापडली नाहीत"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBills, key = { it.id }) { bill ->
                    BillHistoryCard(
                        bill = bill,
                        onOpen = { onOpenBill(bill) },
                        onPrint = {
                            BillPdfGenerator.printBill(context, bill, settings).onFailure { err ->
                                onMessage(err.message ?: "प्रिंट करता आली नाही")
                            }
                        },
                        onSharePdf = {
                            BillPdfGenerator.shareBillPdf(context, bill, settings).onFailure { err ->
                                onMessage(err.message ?: "PDF शेअर करता आली नाही")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        customDateMillis = datePickerState.selectedDateMillis
                        if (customDateMillis != null) {
                            dateFilter = HistoryDateFilter.CUSTOM_DATE
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("निवडा")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("रद्द करा")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun BillHistoryCard(
    bill: BillEntity,
    onOpen: () -> Unit,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = bill.billNumber,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    StatusPillBadge(
                        pendingAmount = bill.pendingAmount,
                        totalReceived = bill.totalReceived
                    )
                }

                Text(
                    text = BillingCalculator.formatInr(bill.grandTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = bill.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (bill.customerMobile.isNotBlank()) {
                        Text(
                            text = bill.customerMobile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = BillingCalculator.formatDateTime(bill.billTimestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val itemSummary = buildList {
                if (bill.fullMasoorQty > 0) add("फुल मसूर × ${bill.fullMasoorQty}")
                if (bill.halfMasoorQty > 0) add("हाफ मसूर × ${bill.halfMasoorQty}")
                if (bill.rotiQty > 0) add("रोटी × ${bill.rotiQty}")
            }.joinToString("  •  ")

            if (itemSummary.isNotBlank()) {
                Text(
                    text = itemSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "रोख: ${BillingCalculator.formatInr(bill.cashPaid)} • ऑनलाईन: ${BillingCalculator.formatInr(bill.onlinePaid)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onSharePdf) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "PDF शेअर करा",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onPrint) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "प्रिंट करा",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
