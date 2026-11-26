package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CustomerEntity
import com.example.data.local.ShopSettingsEntity
import com.example.domain.BillCalculation
import com.example.domain.BillingCalculator
import com.example.ui.BillingFormState
import com.example.ui.CustomerSummary
import com.example.ui.components.ProductStepperRow
import com.example.ui.theme.DisplayFontFamily
import kotlinx.coroutines.delay

@Composable
fun BillingScreen(
    formState: BillingFormState,
    calculation: BillCalculation,
    settings: ShopSettingsEntity,
    customers: List<CustomerSummary>,
    onNameChange: (String) -> Unit,
    onMobileChange: (String) -> Unit,
    onSelectCustomer: (CustomerEntity) -> Unit,
    onWalkInClick: () -> Unit,
    onAdjustFull: (Int) -> Unit,
    onAdjustHalf: (Int) -> Unit,
    onAdjustRoti: (Int) -> Unit,
    onCashChange: (String) -> Unit,
    onOnlineChange: (String) -> Unit,
    onOnlineConfirmedChange: (Boolean) -> Unit,
    onFillFullCash: () -> Unit,
    onFillFullOnline: () -> Unit,
    onResetForm: () -> Unit,
    onSaveBill: () -> Unit,
    onOpenPhotoRegister: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    var showCustomerPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Header Card with Shop Name, Auto Bill No & Date/Time
        Surface(
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_masoor_banner),
                    contentDescription = "हॉटेल आखा मसूर बॅनर",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF431407).copy(alpha = 0.94f),
                                    Color(0xFF7C2D12).copy(alpha = 0.84f),
                                    Color(0xFF7C2D12).copy(alpha = 0.25f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.shopName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (settings.shopSubtitle.isNotBlank()) {
                                Text(
                                    text = settings.shopSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFEDD5)
                                )
                            }
                        }
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = BillingCalculator.formatBillNumber(settings.nextBillNumber),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF431407),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                                    .testTag("auto_bill_number")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFFFFEDD5),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = BillingCalculator.formatDateTime(currentTimeMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFEDD5),
                            modifier = Modifier.testTag("bill_date_time")
                        )
                    }
                }
            }
        }

        if (onOpenPhotoRegister != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenPhotoRegister)
                    .testTag("billing_open_photo_register_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "नोटबुक फोटो → डिजिटल रजिस्टर",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "हाताने लिहिलेल्या रजिस्टरचा फोटो अपलोड करून दैनिक विक्री काढा",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "फोटो स्कॅन",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 2. Customer Details Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ग्राहक माहिती",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = onWalkInClick,
                            label = { Text("रोख ग्राहक", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("walkin_chip")
                        )
                        if (customers.isNotEmpty()) {
                            AssistChip(
                                onClick = { showCustomerPicker = true },
                                label = { Text("जुने ग्राहक", style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PersonSearch,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("pick_customer_chip")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = formState.customerName,
                    onValueChange = onNameChange,
                    label = { Text("ग्राहकाचे नाव *") },
                    placeholder = { Text("उदा. रमेश पाटील") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = {
                        if (formState.customerName.isNotEmpty()) {
                            IconButton(onClick = { onNameChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "नाव पुसा")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )

                val mobileInvalid = formState.customerMobile.isNotEmpty() &&
                    !BillingCalculator.isValidMobileOrEmpty(formState.customerMobile)

                OutlinedTextField(
                    value = formState.customerMobile,
                    onValueChange = onMobileChange,
                    label = { Text("मोबाईल नंबर (१० अंकी)") },
                    placeholder = { Text("उदा. 9876543210") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    isError = mobileInvalid,
                    supportingText = if (mobileInvalid) {
                        { Text("६ ते ९ ने सुरू होणारा १० अंकी मोबाईल नंबर टाका") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_mobile_input")
                )
            }
        }

        // 3. Automatic Product Billing Card
        Card(
            shape = RoundedCornerShape(20.dp),
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
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "पदार्थ निवडा (Auto Billing)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (calculation.grandTotal > 0) {
                        TextButton(onClick = onResetForm) {
                            Text("साफ करा")
                        }
                    }
                }

                ProductStepperRow(
                    titleMarathi = "फुल आखा मसूर",
                    subtitleEnglish = "Full Akha Masoor",
                    unitPrice = settings.fullMasoorPrice,
                    quantity = formState.fullMasoorQty,
                    lineTotal = calculation.fullMasoorTotal,
                    onDecrement = { onAdjustFull(-1) },
                    onIncrement = { onAdjustFull(1) },
                    minusTestTag = "full_masoor_minus",
                    plusTestTag = "full_masoor_plus",
                    qtyTestTag = "full_masoor_qty"
                )

                ProductStepperRow(
                    titleMarathi = "हाफ पक्का मसूर",
                    subtitleEnglish = "Half Pakka Masoor",
                    unitPrice = settings.halfMasoorPrice,
                    quantity = formState.halfMasoorQty,
                    lineTotal = calculation.halfMasoorTotal,
                    onDecrement = { onAdjustHalf(-1) },
                    onIncrement = { onAdjustHalf(1) },
                    minusTestTag = "half_masoor_minus",
                    plusTestTag = "half_masoor_plus",
                    qtyTestTag = "half_masoor_qty"
                )

                ProductStepperRow(
                    titleMarathi = "रोटी",
                    subtitleEnglish = "Roti",
                    unitPrice = settings.rotiPrice,
                    quantity = formState.rotiQty,
                    lineTotal = calculation.rotiTotal,
                    onDecrement = { onAdjustRoti(-1) },
                    onIncrement = { onAdjustRoti(1) },
                    minusTestTag = "roti_minus",
                    plusTestTag = "roti_plus",
                    qtyTestTag = "roti_qty"
                )

                // Grand Total Highlight
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "एकूण बिल (Grand Total)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "नग: ${formState.fullMasoorQty + formState.halfMasoorQty + formState.rotiQty}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = BillingCalculator.formatInr(calculation.grandTotal),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("grand_total_text")
                        )
                    }
                }
            }
        }

        // 4. Payment System Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "पेमेंट तपशील (Payment)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (calculation.grandTotal > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onFillFullCash,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("full_cash_button")
                        ) {
                            Text("पूर्ण रोख (${BillingCalculator.formatInr(calculation.grandTotal)})")
                        }
                        OutlinedButton(
                            onClick = onFillFullOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("full_online_button")
                        ) {
                            Text("पूर्ण ऑनलाईन")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = formState.cashInputText,
                        onValueChange = onCashChange,
                        label = { Text("रोख (Cash ₹)") },
                        placeholder = { Text("0") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cash_payment_input")
                    )

                    OutlinedTextField(
                        value = formState.onlineInputText,
                        onValueChange = onOnlineChange,
                        label = { Text("ऑनलाईन (Online ₹)") },
                        placeholder = { Text("0") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("online_payment_input")
                    )
                }

                // Mandatory Manual Confirmation for Online Payments (Reliability Rule #10)
                val enteredOnline = formState.onlineInputText.toIntOrNull() ?: 0
                AnimatedVisibility(visible = enteredOnline > 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (formState.onlineConfirmed) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        border = BorderStroke(
                            1.dp,
                            if (formState.onlineConfirmed) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOnlineConfirmedChange(!formState.onlineConfirmed) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = formState.onlineConfirmed,
                                onCheckedChange = onOnlineConfirmedChange,
                                modifier = Modifier.testTag("online_confirm_checkbox")
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ऑनलाईन पेमेंट खात्यात जमा झाले (Confirmed)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (formState.onlineConfirmed) {
                                        "खात्री केली: ${BillingCalculator.formatInr(enteredOnline)} जमा धरले आहेत"
                                    } else {
                                        "पेमेंट अॅपमध्ये पैसे आल्याची खात्री करून येथे टिक करा"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (formState.onlineConfirmed) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.VerifiedUser
                                },
                                contentDescription = null,
                                tint = if (formState.onlineConfirmed) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        }
                    }
                }

                // Auto-calculated Received & Pending Summary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "एकूण जमा रक्कम (Total Received)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = BillingCalculator.formatInr(calculation.totalReceived),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("total_received_text")
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "बाकी रक्कम (Remaining Balance)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (calculation.pendingAmount > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                            Text(
                                text = BillingCalculator.formatInr(calculation.pendingAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (calculation.pendingAmount > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.testTag("pending_balance_text")
                            )
                        }
                    }
                }

                val activeError = formState.formError ?: if (
                    calculation.grandTotal > 0 && (calculation.isOverpaid || calculation.hasUnconfirmedOnlineEntry)
                ) {
                    calculation.validationError
                } else {
                    null
                }

                if (activeError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 5. Primary Generate & Save Bill CTA
        Button(
            onClick = onSaveBill,
            enabled = !formState.isSaving && calculation.grandTotal > 0,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("save_bill_button")
        ) {
            if (formState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (calculation.grandTotal > 0) {
                        "बिल जतन करा (${BillingCalculator.formatInr(calculation.grandTotal)})"
                    } else {
                        "पदार्थ निवडून बिल तयार करा"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelect = {
                onSelectCustomer(it.customer)
                showCustomerPicker = false
            }
        )
    }
}

@Composable
private fun CustomerPickerDialog(
    customers: List<CustomerSummary>,
    onDismiss: () -> Unit,
    onSelect: (CustomerSummary) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(customers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) customers
        else customers.filter {
            it.customer.name.lowercase().contains(q) || it.customer.mobile.contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ग्राहक निवडा", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("नाव किंवा मोबाईल शोधा...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.customer.id }) { summary ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(summary) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = summary.customer.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (summary.customer.mobile.isNotBlank()) {
                                        Text(
                                            text = summary.customer.mobile,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (summary.totalPending > 0) {
                                    Text(
                                        text = "बाकी ${BillingCalculator.formatInr(summary.totalPending)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("बंद करा") }
        }
    )
}
