package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.DailyRegisterRecordEntity
import com.example.domain.BillingCalculator
import com.example.domain.DailyRegisterCalculator
import com.example.ui.DailyRegisterDaySummary
import com.example.ui.MonthlyRegisterReport
import com.example.ui.MonthlyRegisterTrendPoint
import com.example.ui.ZipBackupUiState
import com.example.ui.components.FullscreenPhotoDialog
import com.example.ui.components.LocalPhotoImage
import com.example.ui.components.RegisterAutomaticReportCard
import com.example.ui.theme.DisplayFontFamily
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRegisterHistorySection(
    records: List<DailyRegisterRecordEntity>,
    onOpenRecord: (DailyRegisterRecordEntity) -> Unit,
    onDeleteRecords: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterIsoDate by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }

    val filtered = remember(records, searchQuery, filterIsoDate) {
        val q = searchQuery.trim().lowercase()
        records.filter { rec ->
            val matchesDate = filterIsoDate == null || rec.registerDate == filterIsoDate
            val matchesQuery = q.isEmpty() ||
                rec.registerDate.contains(q) ||
                DailyRegisterCalculator.formatDisplayDate(rec.registerDate).lowercase().contains(q) ||
                rec.notes.lowercase().contains(q) ||
                rec.rawTranscript.lowercase().contains(q)
            matchesDate && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("तारीख (उदा. 2026-11-25) किंवा टिप्पणी शोधा...") },
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
                .testTag("register_history_search")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterIsoDate == null,
                    onClick = { filterIsoDate = null },
                    label = { Text("सर्व नोंदी (${records.size})") }
                )
                FilterChip(
                    selected = filterIsoDate != null,
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(
                            filterIsoDate?.let { DailyRegisterCalculator.formatDisplayDate(it) }
                                ?: "तारीख निवडा"
                        )
                    }
                )
            }

            if (records.isNotEmpty()) {
                TextButton(
                    onClick = {
                        multiSelectMode = !multiSelectMode
                        selectedIds = emptySet()
                    },
                    modifier = Modifier.testTag("register_multi_select_toggle")
                ) {
                    Text(if (multiSelectMode) "रद्द करा" else "निवडून हटवा")
                }
            }
        }

        if (multiSelectMode) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} नोंदी निवडल्या",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                pendingDeleteIds = selectedIds.toList()
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("register_delete_selected_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("निवडलेले हटवा (${selectedIds.size})")
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (records.isEmpty()) {
                            "अद्याप कोणताही रजिस्टर फोटो जतन केलेला नाही"
                        } else {
                            "या शोधानुसार नोंदी सापडल्या नाहीत"
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
                items(filtered, key = { it.id }) { rec ->
                    val isSelected = rec.id in selectedIds
                    RegisterRecordCard(
                        record = rec,
                        multiSelectMode = multiSelectMode,
                        isSelected = isSelected,
                        onToggleSelect = {
                            selectedIds = if (isSelected) selectedIds - rec.id else selectedIds + rec.id
                        },
                        onOpen = { onOpenRecord(rec) },
                        onRequestDelete = { pendingDeleteIds = listOf(rec.id) }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = filterIsoDate?.let { DailyRegisterCalculator.parseIsoDateToMillis(it) }
            ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            filterIsoDate = DailyRegisterCalculator.formatIsoDate(millis)
                        }
                        showDatePicker = false
                    }
                ) { Text("निवडा") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("रद्द करा") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    val deleteTargets = pendingDeleteIds
    if (!deleteTargets.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = {
                Text(
                    text = if (deleteTargets.size == 1) {
                        "नोंद व फोटो कायमचे हटवायचे?"
                    } else {
                        "${deleteTargets.size} निवडलेल्या नोंदी हटवायच्या?"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "हटवल्यानंतर या रजिस्टर नोंदी आणि त्यांचे फोटो परत मिळणार नाहीत. सर्व नोंदी चुकून हटवल्या जाणार नाहीत — फक्त निवडलेल्या ${deleteTargets.size} नोंदीच हटवल्या जातील."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecords(deleteTargets)
                        pendingDeleteIds = null
                        selectedIds = emptySet()
                        multiSelectMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_register_button")
                ) {
                    Text("हो, हटवा")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) {
                    Text("रद्द करा")
                }
            }
        )
    }
}

@Composable
private fun RegisterRecordCard(
    record: DailyRegisterRecordEntity,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (multiSelectMode) onToggleSelect() else onOpen()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (multiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }

            // Notebook photo thumbnail
            LocalPhotoImage(
                filePath = record.processedPhotoPath.ifBlank { record.originalPhotoPath },
                contentDescription = "${record.registerDate} रजिस्टर फोटो",
                maxDimension = 320,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DailyRegisterCalculator.formatDisplayDate(record.registerDate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = BillingCalculator.formatInr(record.totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "फुल: ${record.fullMasoorQty}  •  हाफ: ${record.halfMasoorQty}  •  रोटी: ${record.rotiQty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (record.wasAiRead) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (record.wasAiRead) "AI वाचन" else "मॅन्युअल",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "एकूण नग: ${record.totalQuantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!multiSelectMode) {
                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier.testTag("delete_register_record_${record.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "नोंद हटवा",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterRecordDetailSheet(
    record: DailyRegisterRecordEntity,
    onDismiss: () -> Unit,
    onUpdateRecord: (id: Long, date: String, fullQty: Int, halfQty: Int, rotiQty: Int, notes: String) -> Unit,
    onDeleteRecord: (Long) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showOriginal by remember { mutableStateOf(true) }
    var showFullscreen by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val activePhoto = if (showOriginal || record.processedPhotoPath.isBlank()) {
        record.originalPhotoPath.ifBlank { record.processedPhotoPath }
    } else {
        record.processedPhotoPath
    }

    val calc = DailyRegisterCalculator.calculate(
        record.fullMasoorQty,
        record.halfMasoorQty,
        record.rotiQty
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = DailyRegisterCalculator.formatDisplayDate(record.registerDate),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "जतन वेळ: ${BillingCalculator.formatDateTime(record.createdAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "बंद करा")
                }
            }

            if (record.originalPhotoPath.isNotBlank() &&
                record.processedPhotoPath.isNotBlank() &&
                record.originalPhotoPath != record.processedPhotoPath
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showOriginal,
                        onClick = { showOriginal = true },
                        label = { Text("मूळ अपलोड फोटो") }
                    )
                    FilterChip(
                        selected = !showOriginal,
                        onClick = { showOriginal = false },
                        label = { Text("क्रॉप केलेला फोटो") }
                    )
                }
            }

            if (activePhoto.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showFullscreen = true }
                ) {
                    LocalPhotoImage(
                        filePath = activePhoto,
                        contentDescription = "जतन केलेला रजिस्टर फोटो",
                        maxDimension = 1280,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("मोठा पहा", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            RegisterAutomaticReportCard(
                calculation = calc,
                dateDisplay = DailyRegisterCalculator.formatDisplayDate(record.registerDate)
            )

            if (record.rawTranscript.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "AI वाचलेला मजकूर:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = record.rawTranscript,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (record.notes.isNotBlank()) {
                Text(
                    text = "टिप्पणी: ${record.notes}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("दुरुस्त करा")
                }

                OutlinedButton(
                    onClick = {
                        val summary = """
                            *मसूूर बिलिंग - दैनिक रजिस्टर अहवाल*
                            तारीख: ${DailyRegisterCalculator.formatDisplayDate(record.registerDate)}
                            १. फुल आखा मसूर: ${calc.fullMasoorQty} × ₹100 = ${BillingCalculator.formatInr(calc.fullMasoorIncome)}
                            २. हाफ पक्का मसूर: ${calc.halfMasoorQty} × ₹50 = ${BillingCalculator.formatInr(calc.halfMasoorIncome)}
                            ३. रोटी: ${calc.rotiQty} × ₹15 = ${BillingCalculator.formatInr(calc.rotiIncome)}
                            एकूण नग: ${calc.totalQuantity}
                            एकूण उत्पन्न: ${BillingCalculator.formatInr(calc.totalIncome)}
                        """.trimIndent()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }
                        context.startActivity(Intent.createChooser(intent, "अहवाल शेअर करा"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("शेअर")
                }
            }

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ही नोंद व फोटो हटवा")
            }
        }
    }

    if (showFullscreen && activePhoto.isNotBlank()) {
        FullscreenPhotoDialog(
            title = DailyRegisterCalculator.formatDisplayDate(record.registerDate),
            primaryPath = record.originalPhotoPath.ifBlank { activePhoto },
            alternatePath = record.processedPhotoPath.takeIf { it.isNotBlank() },
            onDismiss = { showFullscreen = false }
        )
    }

    if (showEditDialog) {
        EditSavedRegisterDialog(
            record = record,
            onDismiss = { showEditDialog = false },
            onConfirm = { newDate, fullQ, halfQ, rotiQ, notes ->
                showEditDialog = false
                onUpdateRecord(record.id, newDate, fullQ, halfQ, rotiQ, notes)
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("नोंद हटवायची का?", fontWeight = FontWeight.Bold) },
            text = {
                Text("${DailyRegisterCalculator.formatDisplayDate(record.registerDate)} ची ही नोंद आणि तिचा फोटो कायमचा हटवला जाईल.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteRecord(record.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("हो, हटवा") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("रद्द करा") }
            }
        )
    }
}

@Composable
private fun EditSavedRegisterDialog(
    record: DailyRegisterRecordEntity,
    onDismiss: () -> Unit,
    onConfirm: (date: String, fullQty: Int, halfQty: Int, rotiQty: Int, notes: String) -> Unit
) {
    var dateText by remember { mutableStateOf(record.registerDate) }
    var fullText by remember { mutableStateOf(record.fullMasoorQty.toString()) }
    var halfText by remember { mutableStateOf(record.halfMasoorQty.toString()) }
    var rotiText by remember { mutableStateOf(record.rotiQty.toString()) }
    var notesText by remember { mutableStateOf(record.notes) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("नोंद दुरुस्त करा", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10); err = null },
                    label = { Text("तारीख (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullText,
                    onValueChange = { fullText = DailyRegisterCalculator.normalizeQuantityInput(it); err = null },
                    label = { Text("फुल आखा मसूर (₹100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = halfText,
                    onValueChange = { halfText = DailyRegisterCalculator.normalizeQuantityInput(it); err = null },
                    label = { Text("हाफ पक्का मसूर (₹50)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rotiText,
                    onValueChange = { rotiText = DailyRegisterCalculator.normalizeQuantityInput(it); err = null },
                    label = { Text("रोटी (₹15)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it.take(500) },
                    label = { Text("टिप्पणी") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val f = fullText.toIntOrNull()
                val h = halfText.toIntOrNull()
                val r = rotiText.toIntOrNull()
                when {
                    DailyRegisterCalculator.parseIsoDateToMillis(dateText) == null ->
                        err = "कृपया YYYY-MM-DD स्वरूपात वैध तारीख टाका"
                    f == null || h == null || r == null ->
                        err = "सर्व संख्या वैध असाव्यात"
                    else -> onConfirm(dateText.trim(), f, h, r, notesText.trim())
                }
            }) {
                Text("अद्ययावत करा")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करा") }
        }
    )
}

@Composable
fun DailyRegisterReportsSection(
    dailySummaries: List<DailyRegisterDaySummary>,
    monthlyReport: MonthlyRegisterReport,
    monthOffset: Int,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTimeIncome = remember(dailySummaries) { dailySummaries.sumOf { it.totalIncome } }
    val allTimeQty = remember(dailySummaries) { dailySummaries.sumOf { it.totalQuantity } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Total Income
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "डिजिटल रजिस्टर एकूण उत्पन्न",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
                Text(
                    text = BillingCalculator.formatInr(allTimeIncome),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("register_all_time_income")
                )
                Text(
                    text = "${dailySummaries.size} दिवसांच्या नोंदी  •  एकूण नग: $allTimeQty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
        }

        // Monthly Report Card
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
                    Text(
                        text = "मासिक रजिस्टर अहवाल",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onShiftMonth(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "मागील महिना")
                        }
                        Text(
                            text = monthlyReport.monthTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { onShiftMonth(1) },
                            enabled = monthOffset < 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "पुढील महिना")
                        }
                    }
                }

                RegisterTrendChart(
                    points = monthlyReport.sixMonthTrend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ReportMetricLine("नोंदी संख्या", "${monthlyReport.recordsCount}")
                ReportMetricLine(
                    "फुल आखा मसूर (${monthlyReport.fullMasoorQty} × ₹100)",
                    BillingCalculator.formatInr(monthlyReport.fullMasoorIncome)
                )
                ReportMetricLine(
                    "हाफ पक्का मसूर (${monthlyReport.halfMasoorQty} × ₹50)",
                    BillingCalculator.formatInr(monthlyReport.halfMasoorIncome)
                )
                ReportMetricLine(
                    "रोटी (${monthlyReport.rotiQty} × ₹15)",
                    BillingCalculator.formatInr(monthlyReport.rotiIncome)
                )
                ReportMetricLine("महिना एकूण नग", "${monthlyReport.totalQuantity}")
                ReportMetricLine(
                    "महिना एकूण उत्पन्न",
                    BillingCalculator.formatInr(monthlyReport.totalIncome),
                    highlight = true
                )
            }
        }

        // Daily Sales Breakdown
        Text(
            text = "दैनिक विक्री अहवाल (तारीखनिहाय)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (dailySummaries.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "रजिस्टर फोटो जतन केल्यावर तारीखनिहाय दैनिक अहवाल येथे दिसेल.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            dailySummaries.forEach { day ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DailyRegisterCalculator.formatDisplayDate(day.registerDate),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = BillingCalculator.formatInr(day.totalIncome),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "फुल: ${day.fullMasoorQty} (₹${day.fullMasoorIncome}) • हाफ: ${day.halfMasoorQty} (₹${day.halfMasoorIncome}) • रोटी: ${day.rotiQty} (₹${day.rotiIncome})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${day.recordsCount} पान(े) • एकूण नग: ${day.totalQuantity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun RegisterTrendChart(
    points: List<MonthlyRegisterTrendPoint>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    if (points.isEmpty() || points.all { it.totalIncome == 0 }) {
        Box(
            modifier = modifier.background(trackColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "रजिस्टर नोंदी जतन केल्यावर ६ महिन्यांचा चार्ट दिसेल",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val maxIncome = max(points.maxOfOrNull { it.totalIncome } ?: 1, 1)
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val slotW = size.width / points.size
            val barW = (slotW * 0.46f).coerceAtMost(34.dp.toPx())
            points.forEachIndexed { idx, pt ->
                val x = idx * slotW + (slotW - barW) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                if (pt.totalIncome > 0) {
                    val h = (pt.totalIncome.toFloat() / maxIncome) * size.height
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pt.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    if (pt.totalIncome > 0) {
                        Text(
                            BillingCalculator.formatInr(pt.totalIncome),
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
private fun ReportMetricLine(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DailyRegisterBackupSection(
    recordsCount: Int,
    zipBackupState: ZipBackupUiState,
    onExportZipToUri: (Context, Uri) -> Unit,
    onShareZipBackup: (Context, (Uri) -> Unit) -> Unit,
    onRestoreZipFromUri: (Context, Uri) -> Unit,
    onOpenCloudBackupSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExportZipToUri(context, uri)
        }
    }

    val restoreZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onRestoreZipFromUri(context, uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "रजिस्टर फोटो व डेटा बॅकअप (ZIP)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "सध्या $recordsCount रजिस्टर नोंदी आणि त्यांचे मूळ फोटो फोनमध्ये कायमस्वरूपी जतन आहेत. खालील बटणाने सर्व फोटो व नोंदींची एक सुरक्षित ZIP बॅकअप फाईल बनवा किंवा जुन्या बॅकअपमधून नोंदी परत आणा (जुन्या नोंदी ओव्हरराईट होत नाहीत).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val isBusy = zipBackupState is ZipBackupUiState.Loading

                Button(
                    onClick = {
                        createZipLauncher.launch("masoor_daily_register_${System.currentTimeMillis()}.zip")
                    },
                    enabled = !isBusy && recordsCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("register_export_zip_button")
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("बॅकअप ZIP सेव्ह करा ($recordsCount नोंदी)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onShareZipBackup(context) { uri ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "रजिस्टर बॅकअप ZIP शेअर करा"))
                        }
                    },
                    enabled = !isBusy && recordsCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("register_share_zip_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ZIP शेअर करा (WhatsApp / Drive)")
                }

                OutlinedButton(
                    onClick = {
                        restoreZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("register_restore_zip_button")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ZIP मधून रिस्टोअर करा")
                }

                when (zipBackupState) {
                    is ZipBackupUiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(zipBackupState.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is ZipBackupUiState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = zipBackupState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is ZipBackupUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = zipBackupState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    ZipBackupUiState.Idle -> Unit
                }
            }
        }

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
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Google क्लाउड सिंक (Settings)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "सेटिंग्जमधील Google Sign-In बॅकअपद्वारे सर्व रजिस्टर आकडे व दैनिक अहवाल क्लाउडवरही सुरक्षित ठेवता येतात.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenCloudBackupSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("सेटिंग्ज क्लाउड बॅकअप उघडा")
                }
            }
        }
    }
}
