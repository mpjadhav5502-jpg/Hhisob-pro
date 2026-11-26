package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.BillingCalculator
import com.example.domain.DailyRegisterCalculation
import com.example.domain.DailyRegisterCalculator
import com.example.media.RegisterPhotoManager
import com.example.ui.RegisterDraftState
import com.example.ui.RegisterOcrUiState
import com.example.ui.components.CropablePhotoEditor
import com.example.ui.components.FullscreenPhotoDialog
import com.example.ui.components.RegisterAutomaticReportCard
import com.example.ui.theme.DisplayFontFamily
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRegisterUploadSection(
    draft: RegisterDraftState,
    calculation: DailyRegisterCalculation,
    onGalleryPhotoSelected: (Context, Uri) -> Unit,
    onCameraPhotoCaptured: (Context, File) -> Unit,
    onCropChanged: (Float, Float, Float, Float) -> Unit,
    onRotateLeft: (Context) -> Unit,
    onRotateRight: (Context) -> Unit,
    onApplyCrop: (Context) -> Unit,
    onResetOriginal: () -> Unit,
    onRunAiOcr: (Boolean) -> Unit,
    onDateChange: (String) -> Unit,
    onFullQtyChange: (String) -> Unit,
    onAdjustFull: (Int) -> Unit,
    onHalfQtyChange: (String) -> Unit,
    onAdjustHalf: (Int) -> Unit,
    onRotiQtyChange: (String) -> Unit,
    onAdjustRoti: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onVerificationConfirmedChange: (Boolean) -> Unit,
    onClearDraft: () -> Unit,
    onSaveRecord: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOnline = isNetworkAvailable(context)

    var pendingCameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFullscreenPhoto by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onGalleryPhotoSelected(context, uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraFilePath
        if (success && !path.isNullOrBlank()) {
            onCameraPhotoCaptured(context, File(path))
        }
        pendingCameraFilePath = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = RegisterPhotoManager.createCameraCaptureUri(context)
            pendingCameraFilePath = file.absolutePath
            takePictureLauncher.launch(uri)
        } else {
            onShowMessage("कॅमेरा फोटो काढण्यासाठी परवानगी आवश्यक आहे. तुम्ही गॅलरी मधूनही फोटो निवडू शकता.")
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val (file, uri) = RegisterPhotoManager.createCameraCaptureUri(context)
            pendingCameraFilePath = file.absolutePath
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Internet requirement disclosure banner for AI OCR
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isOnline) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOnline) {
                            "AI हस्तलिखित वाचन तयार आहे (ऑनलाईन)"
                        } else {
                            "ऑफलाईन स्थिती — AI OCR साठी इंटरनेट आवश्यक"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "जुने फोटो व नोंदलेले अहवाल नेहमी ऑफलाईन उपलब्ध राहतात. फोटोतून AI द्वारे वाचनासाठी इंटरनेट आवश्यक असते.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Large Photo Upload Card (Camera & Gallery)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "दैनिक रजिस्टर फोटो अपलोड",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (draft.originalPhotoPath != null) {
                        TextButton(onClick = onClearDraft) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("नवीन फोटो")
                        }
                    }
                }

                Text(
                    text = "हाताने लिहिलेल्या रजिस्टर पानाचा फोटो काढा किंवा गॅलरीतून निवडा (JPG, JPEG, PNG).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = ::launchCamera,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("register_camera_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("कॅमेरा फोटो", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("register_gallery_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("गॅलरी (JPG/PNG)", fontWeight = FontWeight.Bold)
                    }
                }

                // Crop, Rotate, Preview Editor once photo is loaded
                val activePath = draft.processedPhotoPath ?: draft.originalPhotoPath
                if (activePath != null) {
                    CropablePhotoEditor(
                        photoPath = activePath,
                        photoVersion = draft.photoVersion,
                        rotationDegrees = draft.rotationDegrees,
                        cropLeft = draft.cropLeftNorm,
                        cropTop = draft.cropTopNorm,
                        cropRight = draft.cropRightNorm,
                        cropBottom = draft.cropBottomNorm,
                        isProcessing = draft.isProcessingPhoto,
                        hasPendingCropChanges = draft.hasUnsavedCroppingChanges,
                        onCropChanged = onCropChanged,
                        onRotateLeft = { onRotateLeft(context) },
                        onRotateRight = { onRotateRight(context) },
                        onApplyCrop = { onApplyCrop(context) },
                        onResetOriginal = onResetOriginal,
                        onOpenFullscreen = { showFullscreenPhoto = true }
                    )

                    // Real AI OCR Trigger Button
                    val isOcrLoading = draft.ocrUiState is RegisterOcrUiState.Loading
                    Button(
                        onClick = { onRunAiOcr(isNetworkAvailable(context)) },
                        enabled = !isOcrLoading && !draft.isProcessingPhoto,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("register_ai_read_button")
                    ) {
                        if (isOcrLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("AI हस्तलिखित वाचत आहे...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (draft.wasAiRead) {
                                    "पुन्हा AI द्वारे फोटो वाचा"
                                } else {
                                    "AI द्वारे फोटो वाचा (मराठी / हिंदी / English)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // OCR status feedback
                    when (val ocrState = draft.ocrUiState) {
                        is RegisterOcrUiState.Error -> {
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
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = ocrState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        is RegisterOcrUiState.Success -> {
                            val hasUnclear = draft.hasUnclearFlag
                            Surface(
                                color = if (hasUnclear) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasUnclear) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (hasUnclear) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = buildString {
                                            if (hasUnclear) {
                                                append("पिवळ्या रंगातील अस्पष्ट माहिती फोटो पाहून नक्की तपासा — अंदाजाने आकडे भरलेले नाहीत.")
                                            } else {
                                                append("AI ने रजिस्टर वाचले आहे! जतन करण्यापूर्वी कृपया आकडे तपासून खात्री करा.")
                                            }
                                            if (draft.overallWarning.isNotBlank()) {
                                                append(" (${draft.overallWarning})")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 3. Extracted Data Verification & Correction Card (only once a photo is uploaded)
        if (draft.originalPhotoPath != null) {
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
                    Text(
                        text = "तपासणी व दुरुस्ती (Verification)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Date selector with unclear highlight
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (draft.dateUnclear) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = if (draft.dateUnclear) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showDatePicker = true }
                            .testTag("register_date_picker_button")
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
                                    text = if (draft.dateUnclear) {
                                        "रजिस्टर तारीख (अस्पष्ट — कृपया तपासा) *"
                                    } else {
                                        "रजिस्टर तारीख (Date) *"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (draft.dateUnclear) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = "${DailyRegisterCalculator.formatDisplayDate(draft.registerDate)} (${draft.registerDate})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                if (draft.dateRawText.isNotBlank()) {
                                    Text(
                                        text = "पानावरील तारीख वाचन: \"${draft.dateRawText}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "तारीख निवडा",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 3 Fixed-Rate Products
                    RegisterQtyVerificationRow(
                        title = "१. फुल आखा मसूर (Full Akha Masoor)",
                        rate = DailyRegisterCalculator.FIXED_FULL_MASOOR_RATE,
                        qtyText = draft.fullQtyText,
                        lineIncome = calculation.fullMasoorIncome,
                        isUnclear = draft.fullUnclear,
                        rawSnippet = draft.fullRawText,
                        onQtyChange = onFullQtyChange,
                        onDecrement = { onAdjustFull(-1) },
                        onIncrement = { onAdjustFull(1) },
                        inputTestTag = "register_full_qty_input",
                        plusTestTag = "register_full_plus",
                        minusTestTag = "register_full_minus"
                    )

                    RegisterQtyVerificationRow(
                        title = "२. हाफ पक्का मसूर (Half Pakka Masoor)",
                        rate = DailyRegisterCalculator.FIXED_HALF_MASOOR_RATE,
                        qtyText = draft.halfQtyText,
                        lineIncome = calculation.halfMasoorIncome,
                        isUnclear = draft.halfUnclear,
                        rawSnippet = draft.halfRawText,
                        onQtyChange = onHalfQtyChange,
                        onDecrement = { onAdjustHalf(-1) },
                        onIncrement = { onAdjustHalf(1) },
                        inputTestTag = "register_half_qty_input",
                        plusTestTag = "register_half_plus",
                        minusTestTag = "register_half_minus"
                    )

                    RegisterQtyVerificationRow(
                        title = "३. रोटी (Roti)",
                        rate = DailyRegisterCalculator.FIXED_ROTI_RATE,
                        qtyText = draft.rotiQtyText,
                        lineIncome = calculation.rotiIncome,
                        isUnclear = draft.rotiUnclear,
                        rawSnippet = draft.rotiRawText,
                        onQtyChange = onRotiQtyChange,
                        onDecrement = { onAdjustRoti(-1) },
                        onIncrement = { onAdjustRoti(1) },
                        inputTestTag = "register_roti_qty_input",
                        plusTestTag = "register_roti_plus",
                        minusTestTag = "register_roti_minus"
                    )

                    if (draft.rawTranscript.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "AI ने पानावर वाचलेला मूळ मजकूर (Transcript):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = draft.rawTranscript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = onNotesChange,
                        label = { Text("नोंद / टिप्पणी (ऐच्छिक)") },
                        placeholder = { Text("उदा. सणावार गर्दी, सकाळ/संध्याकाळ पान") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_notes_input")
                    )
                }
            }

            // 4. Automatic Report Card
            RegisterAutomaticReportCard(
                calculation = calculation,
                dateDisplay = DailyRegisterCalculator.formatDisplayDate(draft.registerDate)
            )

            // 5. Explicit confirmation checkbox if any field was unclear/missing
            AnimatedVisibility(visible = draft.hasUnclearFlag) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (draft.verificationConfirmed) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    border = BorderStroke(
                        1.5.dp,
                        if (draft.verificationConfirmed) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onVerificationConfirmedChange(!draft.verificationConfirmed) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = draft.verificationConfirmed,
                            onCheckedChange = onVerificationConfirmedChange,
                            modifier = Modifier.testTag("register_verify_checkbox")
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "मी मूळ रजिस्टर फोटो पाहून अस्पष्ट संख्या स्वतः तपासल्या आहेत व त्या अचूक आहेत.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (draft.formError != null) {
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
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = draft.formError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 6. Save Permanently CTA
            Button(
                onClick = onSaveRecord,
                enabled = !draft.isSaving,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("save_register_record_button")
            ) {
                if (draft.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "रजिस्टर नोंद जतन करा (${BillingCalculator.formatInr(calculation.totalIncome)})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }

    if (showDatePicker) {
        val initialMillis = DailyRegisterCalculator.parseIsoDateToMillis(draft.registerDate)
            ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(DailyRegisterCalculator.formatIsoDate(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("निवडा")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("रद्द करा") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showFullscreenPhoto && draft.originalPhotoPath != null) {
        FullscreenPhotoDialog(
            title = "रजिस्टर पानाचा फोटो",
            primaryPath = draft.originalPhotoPath,
            alternatePath = draft.processedPhotoPath,
            onDismiss = { showFullscreenPhoto = false }
        )
    }
}

@Composable
private fun RegisterQtyVerificationRow(
    title: String,
    rate: Int,
    qtyText: String,
    lineIncome: Int,
    isUnclear: Boolean,
    rawSnippet: String,
    onQtyChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    inputTestTag: String,
    plusTestTag: String,
    minusTestTag: String
) {
    val highlightUnclear = isUnclear || qtyText.isBlank()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlightUnclear) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (highlightUnclear) 1.5.dp else 1.dp,
            color = if (highlightUnclear) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ठरलेला दर: ₹$rate / नग  •  उत्पन्न: ${BillingCalculator.formatInr(lineIncome)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = DisplayFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (highlightUnclear) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isUnclear) "अस्पष्ट — तपासा" else "संख्या भरा",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (rawSnippet.isNotBlank()) {
                Text(
                    text = "AI वाचन: \"$rawSnippet\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = onDecrement,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(minusTestTag)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "$title कमी करा")
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = onQtyChange,
                    placeholder = { Text("?") },
                    label = { Text("नग / प्लेट") },
                    singleLine = true,
                    isError = highlightUnclear,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(inputTestTag)
                )

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
                    Icon(Icons.Default.Add, contentDescription = "$title वाढवा")
                }
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
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
