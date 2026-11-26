package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DailyRegisterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRegisterScreen(
    viewModel: DailyRegisterViewModel,
    onShowAppSnackbar: (String) -> Unit,
    onOpenSettingsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by viewModel.draftState.collectAsStateWithLifecycle()
    val calculation by viewModel.liveCalculation.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val selectedRecord by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val dailySummaries by viewModel.dailySummaries.collectAsStateWithLifecycle()
    val monthlyReport by viewModel.monthlyReport.collectAsStateWithLifecycle()
    val monthOffset by viewModel.selectedMonthOffset.collectAsStateWithLifecycle()
    val zipBackupState by viewModel.zipBackupState.collectAsStateWithLifecycle()
    val localMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(localMessage) {
        val msg = localMessage
        if (msg != null) {
            onShowAppSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var activeTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "फोटो टू डिजिटल दैनिक रजिस्टर",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        PrimaryTabRow(
            selectedTabIndex = activeTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("स्कॅन", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_tab_scan")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                text = { Text("नोंदी (${records.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_tab_history")
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                text = { Text("अहवाल", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_tab_reports")
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                text = { Text("बॅकअप", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_tab_backup")
            )
        }

        when (activeTab) {
            0 -> DailyRegisterUploadSection(
                draft = draft,
                calculation = calculation,
                onGalleryPhotoSelected = viewModel::onGalleryPhotoSelected,
                onCameraPhotoCaptured = viewModel::onCameraPhotoCaptured,
                onCropChanged = viewModel::updateCropRect,
                onRotateLeft = { ctx -> viewModel.rotateAndApply(ctx, -90) },
                onRotateRight = { ctx -> viewModel.rotateAndApply(ctx, 90) },
                onApplyCrop = viewModel::applyCropAndRotation,
                onResetOriginal = viewModel::resetPhotoToOriginal,
                onRunAiOcr = viewModel::runAiOcr,
                onDateChange = viewModel::onRegisterDateChanged,
                onFullQtyChange = viewModel::onFullQtyChanged,
                onAdjustFull = viewModel::adjustFullQty,
                onHalfQtyChange = viewModel::onHalfQtyChanged,
                onAdjustHalf = viewModel::adjustHalfQty,
                onRotiQtyChange = viewModel::onRotiQtyChanged,
                onAdjustRoti = viewModel::adjustRotiQty,
                onNotesChange = viewModel::onNotesChanged,
                onVerificationConfirmedChange = viewModel::onVerificationConfirmedChanged,
                onClearDraft = viewModel::clearDraft,
                onSaveRecord = {
                    viewModel.saveCurrentRecord(onSaved = { activeTab = 1 })
                },
                onShowMessage = onShowAppSnackbar,
                modifier = Modifier.weight(1f)
            )
            1 -> DailyRegisterHistorySection(
                records = records,
                onOpenRecord = viewModel::openRecordDetail,
                onDeleteRecords = viewModel::deleteRecordsByIds,
                modifier = Modifier.weight(1f)
            )
            2 -> DailyRegisterReportsSection(
                dailySummaries = dailySummaries,
                monthlyReport = monthlyReport,
                monthOffset = monthOffset,
                onShiftMonth = viewModel::shiftMonth,
                modifier = Modifier.weight(1f)
            )
            3 -> DailyRegisterBackupSection(
                recordsCount = records.size,
                zipBackupState = zipBackupState,
                onExportZipToUri = viewModel::exportZipBackupToUri,
                onShareZipBackup = viewModel::createShareableZipBackup,
                onRestoreZipFromUri = viewModel::restoreZipBackupFromUri,
                onOpenCloudBackupSettings = onOpenSettingsTab,
                modifier = Modifier.weight(1f)
            )
        }
    }

    val activeDetail = selectedRecord
    if (activeDetail != null) {
        RegisterRecordDetailSheet(
            record = activeDetail,
            onDismiss = viewModel::closeRecordDetail,
            onUpdateRecord = viewModel::updateSavedRecord,
            onDeleteRecord = { id ->
                viewModel.deleteRecordsByIds(listOf(id))
            }
        )
    }
}
