package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.BillEntity
import com.example.domain.BillingCalculator
import com.example.ui.theme.DisplayFontFamily

@Composable
fun StatusPillBadge(
    pendingAmount: Int,
    totalReceived: Int,
    modifier: Modifier = Modifier
) {
    val (label, containerColor, contentColor) = when {
        pendingAmount == 0 -> Triple(
            "पूर्ण जमा",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        totalReceived > 0 -> Triple(
            "बाकी ${BillingCalculator.formatInr(pendingAmount)}",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Triple(
            "बाकी ${BillingCalculator.formatInr(pendingAmount)}",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ProductStepperRow(
    titleMarathi: String,
    subtitleEnglish: String,
    unitPrice: Int,
    quantity: Int,
    lineTotal: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    minusTestTag: String,
    plusTestTag: String,
    qtyTestTag: String,
    modifier: Modifier = Modifier
) {
    val isActive = quantity > 0
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleMarathi,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitleEnglish,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${BillingCalculator.formatInr(unitPrice)} / नग",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (isActive) {
                        Text(
                            text = "= ${BillingCalculator.formatInr(lineTotal)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = onDecrement,
                    enabled = quantity > 0,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(minusTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "$titleMarathi कमी करा"
                    )
                }

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(qtyTestTag)
                    )
                }

                FilledIconButton(
                    onClick = onIncrement,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(plusTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "$titleMarathi वाढवा"
                    )
                }
            }
        }
    }
}

@Composable
fun SettlePendingDialog(
    bill: BillEntity,
    onDismiss: () -> Unit,
    onConfirmSettle: (addCash: Int, addOnline: Int, onlineConfirmed: Boolean) -> Unit
) {
    var cashText by remember { mutableStateOf("") }
    var onlineText by remember { mutableStateOf("") }
    var onlineConfirmed by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val cashVal = cashText.toIntOrNull() ?: 0
    val onlineVal = onlineText.toIntOrNull() ?: 0
    val totalPay = cashVal + onlineVal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "बाकी रक्कम जमा करा (${bill.billNumber})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ग्राहक: ${bill.customerName} • बाकी: ${BillingCalculator.formatInr(bill.pendingAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = cashText,
                    onValueChange = {
                        cashText = it.filter { ch -> ch.isDigit() }.take(7)
                        localError = null
                    },
                    label = { Text("रोख रक्कम (Cash ₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = onlineText,
                    onValueChange = {
                        onlineText = it.filter { ch -> ch.isDigit() }.take(7)
                        if ((onlineText.toIntOrNull() ?: 0) == 0) onlineConfirmed = false
                        localError = null
                    },
                    label = { Text("ऑनलाईन रक्कम (Online ₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onlineVal > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = onlineConfirmed,
                                onCheckedChange = {
                                    onlineConfirmed = it
                                    localError = null
                                }
                            )
                            Text(
                                text = "ऑनलाईन पेमेंट खात्यात जमा झाले (Confirmed)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            cashText = bill.pendingAmount.toString()
                            onlineText = ""
                            onlineConfirmed = false
                            localError = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("पूर्ण रोख")
                    }
                    OutlinedButton(
                        onClick = {
                            cashText = ""
                            onlineText = bill.pendingAmount.toString()
                            onlineConfirmed = false
                            localError = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("पूर्ण ऑनलाईन")
                    }
                }
                if (localError != null) {
                    Text(
                        text = localError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        totalPay <= 0 -> localError = "कृपया जमा रक्कम टाका"
                        totalPay > bill.pendingAmount -> localError = "रक्कम बाकी रकमेपेक्षा जास्त आहे"
                        onlineVal > 0 && !onlineConfirmed -> localError = "कृपया ऑनलाईन पेमेंट जमा झाल्याची खात्री करा"
                        else -> onConfirmSettle(cashVal, onlineVal, onlineConfirmed)
                    }
                }
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("जमा करा")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("रद्द करा")
            }
        }
    )
}
