package com.example.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.local.ShopSettingsEntity
import com.example.data.remote.onGoogleSignInClicked
import com.example.data.remote.signOutFromGoogle
import com.example.domain.BillingCalculator
import com.example.ui.CloudSyncState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

@Composable
fun SettingsScreen(
    settings: ShopSettingsEntity,
    cloudSyncState: CloudSyncState,
    onSaveSettings: (
        shopName: String,
        shopSubtitle: String,
        shopPhone: String,
        shopAddress: String,
        fullPrice: Int,
        halfPrice: Int,
        rotiPrice: Int
    ) -> Unit,
    onResetDefaultPrices: () -> Unit,
    onBackupToCloud: (Boolean) -> Unit,
    onRestoreFromCloud: (Boolean) -> Unit,
    onClearCloudStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var shopName by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var shopSubtitle by remember(settings.shopSubtitle) { mutableStateOf(settings.shopSubtitle) }
    var shopPhone by remember(settings.shopPhone) { mutableStateOf(settings.shopPhone) }
    var shopAddress by remember(settings.shopAddress) { mutableStateOf(settings.shopAddress) }
    var fullPriceText by remember(settings.fullMasoorPrice) {
        mutableStateOf(settings.fullMasoorPrice.toString())
    }
    var halfPriceText by remember(settings.halfMasoorPrice) {
        mutableStateOf(settings.halfMasoorPrice.toString())
    }
    var rotiPriceText by remember(settings.rotiPrice) {
        mutableStateOf(settings.rotiPrice.toString())
    }
    var localError by remember { mutableStateOf<String?>(null) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val isOnline = isInternetConnected(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "सेटिंग्ज व बॅकअप (Settings)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // 1. Offline-First Guarantee Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OfflinePin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "ऑफलाईन बिलिंग सक्रिय आहे",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "सर्व बिले व ग्राहक फोनमध्ये कायमस्वरूपी जतन होतात. इंटरनेटशिवाय बिलिंग अखंड सुरू राहते.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // 2. Shop Info & Product Prices Card
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "हॉटेल माहिती व पदार्थ दर",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it.take(80); localError = null },
                    label = { Text("हॉटेल / दुकानाचे नाव *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_shop_name_input")
                )
                OutlinedTextField(
                    value = shopSubtitle,
                    onValueChange = { shopSubtitle = it.take(120) },
                    label = { Text("उपशीर्षक / टॅगलाईन") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = shopPhone,
                        onValueChange = { shopPhone = it.take(20) },
                        label = { Text("फोन नंबर") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = shopAddress,
                        onValueChange = { shopAddress = it.take(160) },
                        label = { Text("पत्ता / शहर") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "पदार्थांच्या किंमती (येथूनच बदलता येतात)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onResetDefaultPrices) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("डीफॉल्ट")
                    }
                }

                OutlinedTextField(
                    value = fullPriceText,
                    onValueChange = {
                        fullPriceText = it.filter { ch -> ch.isDigit() }.take(6)
                        localError = null
                    },
                    label = { Text("फुल आखा मसूर दर (Full Akha Masoor ₹)") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_full_price_input")
                )

                OutlinedTextField(
                    value = halfPriceText,
                    onValueChange = {
                        halfPriceText = it.filter { ch -> ch.isDigit() }.take(6)
                        localError = null
                    },
                    label = { Text("हाफ पक्का मसूर दर (Half Pakka Masoor ₹)") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_half_price_input")
                )

                OutlinedTextField(
                    value = rotiPriceText,
                    onValueChange = {
                        rotiPriceText = it.filter { ch -> ch.isDigit() }.take(6)
                        localError = null
                    },
                    label = { Text("रोटी दर (Roti ₹)") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_roti_price_input")
                )

                if (localError != null) {
                    Text(
                        text = localError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val f = fullPriceText.toIntOrNull()
                        val h = halfPriceText.toIntOrNull()
                        val r = rotiPriceText.toIntOrNull()
                        when {
                            shopName.trim().isEmpty() -> localError = "कृपया हॉटेलचे नाव टाका"
                            f == null || f <= 0 -> localError = "फुल मसूर दर ₹1 पेक्षा जास्त असावा"
                            h == null || h <= 0 -> localError = "हाफ मसूर दर ₹1 पेक्षा जास्त असावा"
                            r == null || r <= 0 -> localError = "रोटी दर ₹1 पेक्षा जास्त असावा"
                            else -> onSaveSettings(
                                shopName,
                                shopSubtitle,
                                shopPhone,
                                shopAddress,
                                f,
                                h,
                                r
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("सेटिंग्ज आणि किंमती जतन करा", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Online Cloud Backup & Restore Card (Firebase Auth + Firestore)
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
                            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ऑनलाईन बॅकअप व रिस्टोअर",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = if (isOnline) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isOnline) "ऑनलाईन" else "ऑफलाईन",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (settings.lastBackupMillis > 0L) {
                    Text(
                        text = "शेवटचा बॅकअप: ${BillingCalculator.formatDateTime(settings.lastBackupMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentUser == null) {
                    Text(
                        text = "इंटरनेट उपलब्ध असताना तुमचा सर्व बिल डेटा क्लाउडवर सुरक्षित ठेवण्यासाठी Google खात्याने साइन इन करा.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            isSigningIn = true
                            authError = null
                            onClearCloudStatus()
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isSigningIn = false
                                    currentUser = Firebase.auth.currentUser
                                },
                                onAuthError = { msg ->
                                    isSigningIn = false
                                    authError = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isSigningIn = false
                                }
                            )
                        },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign in with Google (बॅकअपसाठी)", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (authError != null) {
                        Text(
                            text = authError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google खाते जोडलेले आहे",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUser?.email ?: currentUser?.displayName ?: "Signed in",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = {
                                    signOutFromGoogle(credentialManager, scope) {
                                        currentUser = null
                                        onClearCloudStatus()
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("साइन आउट")
                            }
                        }
                    }

                    val isSyncing = cloudSyncState is CloudSyncState.Loading
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onBackupToCloud(isInternetConnected(context)) },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cloud_backup_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("बॅकअप घ्या")
                        }
                        OutlinedButton(
                            onClick = { onRestoreFromCloud(isInternetConnected(context)) },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cloud_restore_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("रिस्टोअर करा")
                        }
                    }
                }

                when (cloudSyncState) {
                    is CloudSyncState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(cloudSyncState.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is CloudSyncState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cloudSyncState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is CloudSyncState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cloudSyncState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    CloudSyncState.Idle -> Unit
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun isInternetConnected(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        false
    }
}
