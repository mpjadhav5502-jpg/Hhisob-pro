package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.BillingCalculator
import com.example.ui.DashboardStats
import com.example.ui.MonthlyBarPoint
import com.example.ui.theme.DisplayFontFamily
import kotlin.math.max

@Composable
fun DashboardScreen(
    stats: DashboardStats,
    monthOffset: Int,
    onShiftMonth: (Int) -> Unit,
    onOpenPendingBills: () -> Unit,
    registerTotalIncome: Int = 0,
    registerRecordsCount: Int = 0,
    onOpenPhotoRegister: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "व्यवसाय डॅशबोर्ड (Dashboard)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Hero Today's Sales Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "आजची एकूण विक्री (Today's Sales)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                    Text(
                        text = BillingCalculator.formatInr(stats.todaySales),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("today_sales_text")
                    )
                    Text(
                        text = "आजची बिले: ${stats.todayBillsCount}  •  एकूण विक्री: ${BillingCalculator.formatInr(stats.totalAllTimeSales)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // 2x2 Summary KPI Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                title = "एकूण रोख जमा",
                subtitle = "Total Cash",
                value = BillingCalculator.formatInr(stats.totalCashReceived),
                icon = Icons.Default.Payments,
                accentColor = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .weight(1f)
                    .testTag("total_cash_card")
            )
            KpiStatCard(
                title = "एकूण ऑनलाईन जमा",
                subtitle = "Total Online",
                value = BillingCalculator.formatInr(stats.totalOnlineReceived),
                icon = Icons.Default.AccountBalance,
                accentColor = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier
                    .weight(1f)
                    .testTag("total_online_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                title = "एकूण बाकी रक्कम",
                subtitle = "Pending (तपशील पहा)",
                value = BillingCalculator.formatInr(stats.totalPendingPayments),
                icon = Icons.Default.HourglassBottom,
                accentColor = MaterialTheme.colorScheme.error,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                onClick = onOpenPendingBills,
                modifier = Modifier
                    .weight(1f)
                    .testTag("total_pending_card")
            )
            KpiStatCard(
                title = "एकूण बिले",
                subtitle = "Total Bills",
                value = stats.totalBillsCount.toString(),
                icon = Icons.Default.Receipt,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .weight(1f)
                    .testTag("total_bills_card")
            )
        }

        if (onOpenPhotoRegister != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPhotoRegister)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "डिजिटल दैनिक रजिस्टर (फोटो नोंदी)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$registerRecordsCount जतन फोटो नोंदी • अहवाल व फोटो पहा",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = BillingCalculator.formatInr(registerTotalIncome),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Monthly Sales Report Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "मासिक विक्री अहवाल",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onShiftMonth(-1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "मागील महिना"
                            )
                        }
                        Text(
                            text = stats.monthlyReport.monthTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { onShiftMonth(1) },
                            enabled = monthOffset < 0
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "पुढील महिना"
                            )
                        }
                    }
                }

                // Compose Canvas 6-Month Bar Chart
                MonthlyBarChart(
                    points = stats.monthlyReport.sixMonthTrend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                )

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendChip(color = MaterialTheme.colorScheme.tertiary, label = "रोख")
                    LegendChip(color = MaterialTheme.colorScheme.secondary, label = "ऑनलाईन")
                    LegendChip(color = MaterialTheme.colorScheme.error, label = "बाकी")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val report = stats.monthlyReport
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportLine("महिना बिले (Bills)", report.totalBills.toString())
                    ReportLine("महिना एकूण विक्री", BillingCalculator.formatInr(report.totalSales), bold = true)
                    ReportLine("रोख जमा (Cash)", BillingCalculator.formatInr(report.cashReceived))
                    ReportLine("ऑनलाईन जमा (Online)", BillingCalculator.formatInr(report.onlineReceived))
                    ReportLine(
                        "महिना बाकी रक्कम (Pending)",
                        BillingCalculator.formatInr(report.pendingAmount),
                        valueColor = if (report.pendingAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    subtitle: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accentColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthlyBarChart(
    points: List<MonthlyBarPoint>,
    modifier: Modifier = Modifier
) {
    val cashColor = MaterialTheme.colorScheme.tertiary
    val onlineColor = MaterialTheme.colorScheme.secondary
    val pendingColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    if (points.isEmpty() || points.all { it.totalSales == 0 }) {
        Box(
            modifier = modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CurrencyRupee,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "मासिक विक्री चार्ट बिले जतन केल्यानंतर दिसेल",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val maxTotal = max(points.maxOfOrNull { it.totalSales } ?: 1, 1)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val count = points.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
            val maxHeight = size.height

            points.forEachIndexed { index, point ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                // Background track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, maxHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )

                if (point.totalSales > 0) {
                    val totalBarHeight = (point.totalSales.toFloat() / maxTotal) * maxHeight
                    var currentBottom = maxHeight

                    val pendingH = (point.pendingSales.toFloat() / point.totalSales) * totalBarHeight
                    val onlineH = (point.onlineSales.toFloat() / point.totalSales) * totalBarHeight
                    val cashH = (point.cashSales.toFloat() / point.totalSales) * totalBarHeight

                    if (cashH > 0f) {
                        drawRoundRect(
                            color = cashColor,
                            topLeft = Offset(x, currentBottom - cashH),
                            size = Size(barWidth, cashH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        currentBottom -= cashH
                    }
                    if (onlineH > 0f) {
                        drawRoundRect(
                            color = onlineColor,
                            topLeft = Offset(x, currentBottom - onlineH),
                            size = Size(barWidth, onlineH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        currentBottom -= onlineH
                    }
                    if (pendingH > 0f) {
                        drawRoundRect(
                            color = pendingColor,
                            topLeft = Offset(x, currentBottom - pendingH),
                            size = Size(barWidth, pendingH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    if (point.totalSales > 0) {
                        Text(
                            text = BillingCalculator.formatInr(point.totalSales),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = DisplayFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReportLine(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = DisplayFontFamily,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
