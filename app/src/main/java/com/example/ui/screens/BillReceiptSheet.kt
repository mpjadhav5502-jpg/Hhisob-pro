package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.BillEntity
import com.example.data.local.ShopSettingsEntity
import com.example.domain.BillingCalculator
import com.example.pdf.BillPdfGenerator
import com.example.ui.components.SettlePendingDialog
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.DisplayFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillReceiptSheet(
    bill: BillEntity,
    settings: ShopSettingsEntity,
    onDismiss: () -> Unit,
    onSettlePayment: (billId: Long, addCash: Int, addOnline: Int, onlineConfirmed: Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSettleDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "बिल पावती (Receipt)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "पावती बंद करा")
                }
            }

            // Receipt Paper Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Terracotta Shop Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = bill.shopNameSnapshot,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center
                            )
                            if (settings.shopSubtitle.isNotBlank()) {
                                Text(
                                    text = settings.shopSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                            val contact = listOfNotNull(
                                settings.shopPhone.takeIf { it.isNotBlank() }?.let { "मो.: $it" },
                                settings.shopAddress.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (contact.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = contact,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "बिल क्र.: ${bill.billNumber}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = BillingCalculator.formatDateTime(bill.billTimestampMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPillBadge(
                                pendingAmount = bill.pendingAmount,
                                totalReceived = bill.totalReceived
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ग्राहक",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = bill.customerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (bill.customerMobile.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "मोबाईल",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = bill.customerMobile,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = DisplayFontFamily
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Items Table Header
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "पदार्थ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1.8f)
                            )
                            Text(
                                text = "नग",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.6f)
                            )
                            Text(
                                text = "दर",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = "रक्कम",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        if (bill.fullMasoorQty > 0) {
                            ReceiptItemRow(
                                name = "फुल आखा मसूर",
                                qty = bill.fullMasoorQty,
                                price = bill.fullMasoorPrice
                            )
                        }
                        if (bill.halfMasoorQty > 0) {
                            ReceiptItemRow(
                                name = "हाफ पक्का मसूर",
                                qty = bill.halfMasoorQty,
                                price = bill.halfMasoorPrice
                            )
                        }
                        if (bill.rotiQty > 0) {
                            ReceiptItemRow(
                                name = "रोटी",
                                qty = bill.rotiQty,
                                price = bill.rotiPrice
                            )
                        }

                        HorizontalDivider(
                            thickness = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "एकूण बिल (Grand Total)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = BillingCalculator.formatInr(bill.grandTotal),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PaymentDetailRow("रोख जमा (Cash)", bill.cashPaid)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "ऑनलाईन जमा (Online)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (bill.onlinePaid > 0 && bill.onlineConfirmed) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "खात्री केलेली",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = BillingCalculator.formatInr(bill.onlinePaid),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = DisplayFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                PaymentDetailRow("एकूण जमा (Total Received)", bill.totalReceived, bold = true)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "बाकी रक्कम (Pending)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (bill.pendingAmount > 0) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                    Text(
                                        text = BillingCalculator.formatInr(bill.pendingAmount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = DisplayFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (bill.pendingAmount > 0) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "धन्यवाद! पुन्हा भेट द्या.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        BillPdfGenerator.createBillPdfFile(context, bill, settings).fold(
                            onSuccess = { file ->
                                onMessage("PDF जतन झाली: ${file.name}")
                            },
                            onFailure = { err ->
                                onMessage(err.message ?: "PDF तयार करता आली नाही")
                            }
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF सेव्ह")
                }
                Button(
                    onClick = {
                        BillPdfGenerator.shareBillPdf(context, bill, settings).onFailure { err ->
                            onMessage(err.message ?: "PDF शेअर करता आली नाही")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("share_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF शेअर")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        BillPdfGenerator.printBill(context, bill, settings).onFailure { err ->
                            onMessage(err.message ?: "प्रिंट सुरू करता आली नाही")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("print_bill_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("प्रिंट करा")
                }

                if (bill.pendingAmount > 0) {
                    Button(
                        onClick = { showSettleDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("settle_pending_button")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("बाकी जमा")
                    }
                }
            }
        }
    }

    if (showSettleDialog) {
        SettlePendingDialog(
            bill = bill,
            onDismiss = { showSettleDialog = false },
            onConfirmSettle = { cash, online, confirmed ->
                showSettleDialog = false
                onSettlePayment(bill.id, cash, online, confirmed)
            }
        )
    }
}

@Composable
private fun ReceiptItemRow(name: String, qty: Int, price: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.8f)
        )
        Text(
            text = qty.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = DisplayFontFamily,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
        Text(
            text = BillingCalculator.formatInr(price),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = DisplayFontFamily,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = BillingCalculator.formatInr(qty * price),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f)
        )
    }
}

@Composable
private fun PaymentDetailRow(label: String, amount: Int, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = BillingCalculator.formatInr(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = DisplayFontFamily,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
