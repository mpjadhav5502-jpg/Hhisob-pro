package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.RegisterZipBackupManager
import com.example.data.local.DailyRegisterRecordEntity
import com.example.data.local.LocalRegisterRepository
import com.example.data.remote.RegisterOcrExtraction
import com.example.data.remote.RegisterOcrRepository
import com.example.domain.DailyRegisterCalculation
import com.example.domain.DailyRegisterCalculator
import com.example.media.RegisterPhotoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

sealed interface RegisterOcrUiState {
    data object Idle : RegisterOcrUiState
    data object Loading : RegisterOcrUiState
    data class Success(val extraction: RegisterOcrExtraction) : RegisterOcrUiState
    data class Error(val message: String) : RegisterOcrUiState
}

sealed interface ZipBackupUiState {
    data object Idle : ZipBackupUiState
    data class Loading(val message: String) : ZipBackupUiState
    data class Success(val message: String) : ZipBackupUiState
    data class Error(val message: String) : ZipBackupUiState
}

data class RegisterDraftState(
    val draftUuid: String = UUID.randomUUID().toString(),
    val originalPhotoPath: String? = null,
    val processedPhotoPath: String? = null,
    val photoVersion: Int = 0,
    val rotationDegrees: Int = 0,
    val cropLeftNorm: Float = 0f,
    val cropTopNorm: Float = 0f,
    val cropRightNorm: Float = 1f,
    val cropBottomNorm: Float = 1f,
    val isProcessingPhoto: Boolean = false,
    val ocrUiState: RegisterOcrUiState = RegisterOcrUiState.Idle,
    val registerDate: String = DailyRegisterCalculator.formatIsoDate(System.currentTimeMillis()),
    val dateUnclear: Boolean = false,
    val dateRawText: String = "",
    val fullQtyText: String = "",
    val fullUnclear: Boolean = false,
    val fullRawText: String = "",
    val halfQtyText: String = "",
    val halfUnclear: Boolean = false,
    val halfRawText: String = "",
    val rotiQtyText: String = "",
    val rotiUnclear: Boolean = false,
    val rotiRawText: String = "",
    val rawTranscript: String = "",
    val overallWarning: String = "",
    val notes: String = "",
    val wasAiRead: Boolean = false,
    val verificationConfirmed: Boolean = false,
    val isSaving: Boolean = false,
    val formError: String? = null
) {
    val hasUnclearFlag: Boolean
        get() = dateUnclear || fullUnclear || halfUnclear || rotiUnclear

    val hasUnsavedCroppingChanges: Boolean
        get() = rotationDegrees != 0 ||
            cropLeftNorm > 0.01f ||
            cropTopNorm > 0.01f ||
            cropRightNorm < 0.99f ||
            cropBottomNorm < 0.99f
}

data class DailyRegisterDaySummary(
    val registerDate: String,
    val recordsCount: Int,
    val fullMasoorQty: Int,
    val fullMasoorIncome: Int,
    val halfMasoorQty: Int,
    val halfMasoorIncome: Int,
    val rotiQty: Int,
    val rotiIncome: Int,
    val totalQuantity: Int,
    val totalIncome: Int
)

data class MonthlyRegisterTrendPoint(
    val label: String,
    val totalIncome: Int,
    val totalQuantity: Int
)

data class MonthlyRegisterReport(
    val monthTitle: String,
    val monthKey: String,
    val recordsCount: Int,
    val fullMasoorQty: Int,
    val fullMasoorIncome: Int,
    val halfMasoorQty: Int,
    val halfMasoorIncome: Int,
    val rotiQty: Int,
    val rotiIncome: Int,
    val totalQuantity: Int,
    val totalIncome: Int,
    val sixMonthTrend: List<MonthlyRegisterTrendPoint>
)

class DailyRegisterViewModel(
    private val localRepo: LocalRegisterRepository,
    private val ocrRepo: RegisterOcrRepository
) : ViewModel() {

    private val _draftState = MutableStateFlow(RegisterDraftState())
    val draftState: StateFlow<RegisterDraftState> = _draftState.asStateFlow()

    private val _selectedMonthOffset = MutableStateFlow(0)
    val selectedMonthOffset: StateFlow<Int> = _selectedMonthOffset.asStateFlow()

    private val _zipBackupState = MutableStateFlow<ZipBackupUiState>(ZipBackupUiState.Idle)
    val zipBackupState: StateFlow<ZipBackupUiState> = _zipBackupState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedRecordId = MutableStateFlow<Long?>(null)

    val records: StateFlow<List<DailyRegisterRecordEntity>> = localRepo.recordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedRecord: StateFlow<DailyRegisterRecordEntity?> = combine(
        _selectedRecordId,
        records
    ) { id, list ->
        if (id == null) null else list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val liveCalculation: StateFlow<DailyRegisterCalculation> = _draftState
        .map { draft ->
            DailyRegisterCalculator.calculate(
                fullMasoorQty = draft.fullQtyText.toIntOrNull() ?: 0,
                halfMasoorQty = draft.halfQtyText.toIntOrNull() ?: 0,
                rotiQty = draft.rotiQtyText.toIntOrNull() ?: 0
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            DailyRegisterCalculator.calculate(0, 0, 0)
        )

    val dailySummaries: StateFlow<List<DailyRegisterDaySummary>> = records
        .map { list ->
            list.groupBy { it.registerDate }
                .entries
                .sortedByDescending { it.key }
                .map { (date, dayRecords) ->
                    val fullQ = dayRecords.sumOf { it.fullMasoorQty }
                    val halfQ = dayRecords.sumOf { it.halfMasoorQty }
                    val rotiQ = dayRecords.sumOf { it.rotiQty }
                    val calc = DailyRegisterCalculator.calculate(fullQ, halfQ, rotiQ)
                    DailyRegisterDaySummary(
                        registerDate = date,
                        recordsCount = dayRecords.size,
                        fullMasoorQty = calc.fullMasoorQty,
                        fullMasoorIncome = calc.fullMasoorIncome,
                        halfMasoorQty = calc.halfMasoorQty,
                        halfMasoorIncome = calc.halfMasoorIncome,
                        rotiQty = calc.rotiQty,
                        rotiIncome = calc.rotiIncome,
                        totalQuantity = calc.totalQuantity,
                        totalIncome = calc.totalIncome
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val monthlyReport: StateFlow<MonthlyRegisterReport> = combine(
        records,
        _selectedMonthOffset
    ) { list, offset ->
        computeMonthlyReport(list, offset)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        MonthlyRegisterReport(
            monthTitle = DailyRegisterCalculator.formatMonthLabel(0),
            monthKey = DailyRegisterCalculator.getMonthKeyForOffset(0),
            recordsCount = 0,
            fullMasoorQty = 0,
            fullMasoorIncome = 0,
            halfMasoorQty = 0,
            halfMasoorIncome = 0,
            rotiQty = 0,
            rotiIncome = 0,
            totalQuantity = 0,
            totalIncome = 0,
            sixMonthTrend = emptyList()
        )
    )

    fun onGalleryPhotoSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            val newUuid = UUID.randomUUID().toString()
            _draftState.update { it.copy(isProcessingPhoto = true, formError = null) }
            val result = withContext(Dispatchers.IO) {
                RegisterPhotoManager.saveOriginalPhotoFromUri(context, uri, newUuid)
            }
            result.fold(
                onSuccess = { path ->
                    _draftState.value = RegisterDraftState(
                        draftUuid = newUuid,
                        originalPhotoPath = path,
                        processedPhotoPath = path,
                        photoVersion = 1
                    )
                },
                onFailure = { err ->
                    _draftState.update {
                        it.copy(
                            isProcessingPhoto = false,
                            formError = err.message ?: "फोटो लोड करता आला नाही"
                        )
                    }
                }
            )
        }
    }

    fun onCameraPhotoCaptured(context: Context, tempFile: File) {
        viewModelScope.launch {
            val newUuid = UUID.randomUUID().toString()
            _draftState.update { it.copy(isProcessingPhoto = true, formError = null) }
            val result = withContext(Dispatchers.IO) {
                RegisterPhotoManager.saveOriginalPhotoFromFile(context, tempFile, newUuid)
            }
            result.fold(
                onSuccess = { path ->
                    _draftState.value = RegisterDraftState(
                        draftUuid = newUuid,
                        originalPhotoPath = path,
                        processedPhotoPath = path,
                        photoVersion = 1
                    )
                },
                onFailure = { err ->
                    _draftState.update {
                        it.copy(
                            isProcessingPhoto = false,
                            formError = err.message ?: "कॅमेरा फोटो जतन करता आला नाही"
                        )
                    }
                }
            )
        }
    }

    fun updateCropRect(left: Float, top: Float, right: Float, bottom: Float) {
        val safeLeft = left.coerceIn(0f, 0.85f)
        val safeTop = top.coerceIn(0f, 0.85f)
        val safeRight = right.coerceIn(safeLeft + 0.15f, 1f)
        val safeBottom = bottom.coerceIn(safeTop + 0.15f, 1f)
        _draftState.update {
            it.copy(
                cropLeftNorm = safeLeft,
                cropTopNorm = safeTop,
                cropRightNorm = safeRight,
                cropBottomNorm = safeBottom
            )
        }
    }

    fun rotateAndApply(context: Context, deltaDegrees: Int) {
        val draft = _draftState.value
        val orig = draft.originalPhotoPath ?: return
        val newRot = (draft.rotationDegrees + deltaDegrees + 360) % 360
        applyTransform(
            context = context,
            origPath = orig,
            rotationDegrees = newRot,
            left = draft.cropLeftNorm,
            top = draft.cropTopNorm,
            right = draft.cropRightNorm,
            bottom = draft.cropBottomNorm,
            uuid = draft.draftUuid
        )
    }

    fun applyCropAndRotation(context: Context) {
        val draft = _draftState.value
        val orig = draft.originalPhotoPath ?: return
        applyTransform(
            context = context,
            origPath = orig,
            rotationDegrees = draft.rotationDegrees,
            left = draft.cropLeftNorm,
            top = draft.cropTopNorm,
            right = draft.cropRightNorm,
            bottom = draft.cropBottomNorm,
            uuid = draft.draftUuid
        )
    }

    private fun applyTransform(
        context: Context,
        origPath: String,
        rotationDegrees: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        uuid: String
    ) {
        viewModelScope.launch {
            _draftState.update { it.copy(isProcessingPhoto = true, formError = null) }
            val result = withContext(Dispatchers.IO) {
                RegisterPhotoManager.applyCropAndRotation(
                    context = context,
                    sourcePath = origPath,
                    rotationDegrees = rotationDegrees,
                    cropLeftNorm = left,
                    cropTopNorm = top,
                    cropRightNorm = right,
                    cropBottomNorm = bottom,
                    recordUuid = uuid
                )
            }
            result.fold(
                onSuccess = { newProcessedPath ->
                    _draftState.update {
                        it.copy(
                            processedPhotoPath = newProcessedPath,
                            rotationDegrees = rotationDegrees,
                            cropLeftNorm = 0f,
                            cropTopNorm = 0f,
                            cropRightNorm = 1f,
                            cropBottomNorm = 1f,
                            photoVersion = it.photoVersion + 1,
                            isProcessingPhoto = false
                        )
                    }
                    _snackbarMessage.value = "फोटो क्रॉप / रोटेट लागू झाले (मूळ फोटो सुरक्षित आहे)"
                },
                onFailure = { err ->
                    _draftState.update {
                        it.copy(
                            isProcessingPhoto = false,
                            formError = err.message ?: "फोटो संपादित करता आला नाही"
                        )
                    }
                }
            )
        }
    }

    fun resetPhotoToOriginal() {
        _draftState.update { draft ->
            draft.copy(
                processedPhotoPath = draft.originalPhotoPath,
                rotationDegrees = 0,
                cropLeftNorm = 0f,
                cropTopNorm = 0f,
                cropRightNorm = 1f,
                cropBottomNorm = 1f,
                photoVersion = draft.photoVersion + 1
            )
        }
    }

    fun runAiOcr(isNetworkConnected: Boolean) {
        val draft = _draftState.value
        val activePhotoPath = draft.processedPhotoPath ?: draft.originalPhotoPath
        if (activePhotoPath.isNullOrBlank()) {
            _draftState.update {
                it.copy(ocrUiState = RegisterOcrUiState.Error("कृपया आधी रजिस्टर पानाचा फोटो अपलोड करा"))
            }
            return
        }
        if (!isNetworkConnected) {
            _draftState.update {
                it.copy(
                    ocrUiState = RegisterOcrUiState.Error(
                        "AI हस्तलिखित वाचनासाठी इंटरनेट आवश्यक आहे. कृपया इंटरनेट चालू करा किंवा खाली स्वतः अचूक संख्या तपासून भरा."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _draftState.update { it.copy(ocrUiState = RegisterOcrUiState.Loading, formError = null) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    RegisterPhotoManager.prepareBitmapForOcr(activePhotoPath)
                } ?: throw IllegalStateException("फोटो वाचण्यासाठी उघडता आला नाही")

                val result = ocrRepo.extractFromNotebookImage(bitmap)
                bitmap.recycle()

                result.fold(
                    onSuccess = { ext ->
                        _draftState.update { cur ->
                            cur.copy(
                                ocrUiState = RegisterOcrUiState.Success(ext),
                                registerDate = ext.detectedDate ?: cur.registerDate,
                                dateUnclear = ext.dateUnclear || ext.detectedDate.isNullOrBlank(),
                                dateRawText = ext.dateRawText,
                                fullQtyText = ext.fullMasoorQty?.toString() ?: "",
                                fullUnclear = ext.fullMasoorUnclear || ext.fullMasoorQty == null,
                                fullRawText = ext.fullMasoorRawText,
                                halfQtyText = ext.halfMasoorQty?.toString() ?: "",
                                halfUnclear = ext.halfMasoorUnclear || ext.halfMasoorQty == null,
                                halfRawText = ext.halfMasoorRawText,
                                rotiQtyText = ext.rotiQty?.toString() ?: "",
                                rotiUnclear = ext.rotiUnclear || ext.rotiQty == null,
                                rotiRawText = ext.rotiRawText,
                                rawTranscript = ext.rawTranscript,
                                overallWarning = ext.overallWarning,
                                wasAiRead = true,
                                verificationConfirmed = !ext.hasAnyUnclearOrMissing,
                                formError = null
                            )
                        }
                    },
                    onFailure = { err ->
                        _draftState.update {
                            it.copy(
                                ocrUiState = RegisterOcrUiState.Error(
                                    err.message ?: "AI फोटो वाचन अयशस्वी झाले"
                                )
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _draftState.update {
                    it.copy(
                        ocrUiState = RegisterOcrUiState.Error(
                            e.message ?: "फोटो वाचताना त्रुटी आली"
                        )
                    )
                }
            }
        }
    }

    fun onRegisterDateChanged(isoDate: String) {
        _draftState.update {
            it.copy(
                registerDate = isoDate,
                dateUnclear = false,
                formError = null
            )
        }
    }

    fun onFullQtyChanged(raw: String) {
        val clean = DailyRegisterCalculator.normalizeQuantityInput(raw)
        _draftState.update {
            it.copy(
                fullQtyText = clean,
                fullUnclear = false,
                formError = null
            )
        }
    }

    fun adjustFullQty(delta: Int) {
        val cur = _draftState.value.fullQtyText.toIntOrNull() ?: 0
        val next = (cur + delta).coerceIn(0, 99999)
        onFullQtyChanged(next.toString())
    }

    fun onHalfQtyChanged(raw: String) {
        val clean = DailyRegisterCalculator.normalizeQuantityInput(raw)
        _draftState.update {
            it.copy(
                halfQtyText = clean,
                halfUnclear = false,
                formError = null
            )
        }
    }

    fun adjustHalfQty(delta: Int) {
        val cur = _draftState.value.halfQtyText.toIntOrNull() ?: 0
        val next = (cur + delta).coerceIn(0, 99999)
        onHalfQtyChanged(next.toString())
    }

    fun onRotiQtyChanged(raw: String) {
        val clean = DailyRegisterCalculator.normalizeQuantityInput(raw)
        _draftState.update {
            it.copy(
                rotiQtyText = clean,
                rotiUnclear = false,
                formError = null
            )
        }
    }

    fun adjustRotiQty(delta: Int) {
        val cur = _draftState.value.rotiQtyText.toIntOrNull() ?: 0
        val next = (cur + delta).coerceIn(0, 99999)
        onRotiQtyChanged(next.toString())
    }

    fun onNotesChanged(notes: String) {
        _draftState.update { it.copy(notes = notes.take(500), formError = null) }
    }

    fun onVerificationConfirmedChanged(confirmed: Boolean) {
        _draftState.update { it.copy(verificationConfirmed = confirmed, formError = null) }
    }

    fun clearDraft() {
        _draftState.value = RegisterDraftState()
    }

    fun saveCurrentRecord(onSaved: () -> Unit = {}) {
        val draft = _draftState.value
        if (draft.isSaving) return

        val origPath = draft.originalPhotoPath
        if (origPath.isNullOrBlank()) {
            _draftState.update { it.copy(formError = "कृपया आधी रजिस्टर पानाचा फोटो अपलोड करा") }
            return
        }
        if (DailyRegisterCalculator.parseIsoDateToMillis(draft.registerDate) == null) {
            _draftState.update { it.copy(formError = "कृपया वैध तारीख निवडा") }
            return
        }

        val fullQty = draft.fullQtyText.trim().toIntOrNull()
        val halfQty = draft.halfQtyText.trim().toIntOrNull()
        val rotiQty = draft.rotiQtyText.trim().toIntOrNull()

        if (fullQty == null || halfQty == null || rotiQty == null) {
            _draftState.update {
                it.copy(
                    formError = "सर्व पदार्थांचे प्रमाण (० किंवा अचूक संख्या) तपासून भरा. अस्पष्ट संख्या परस्पर धरली जात नाही."
                )
            }
            return
        }
        if (draft.hasUnclearFlag && !draft.verificationConfirmed) {
            _draftState.update {
                it.copy(
                    formError = "काही संख्या अस्पष्ट होत्या — कृपया फोटो पाहून तपासणी पूर्ण झाल्याची टिक करा."
                )
            }
            return
        }

        val unclearSummary = buildList {
            if (draft.dateUnclear) add("तारीख अस्पष्ट होती (${draft.dateRawText})")
            if (draft.fullUnclear) add("फुल मसूर अस्पष्ट (${draft.fullRawText})")
            if (draft.halfUnclear) add("हाफ मसूर अस्पष्ट (${draft.halfRawText})")
            if (draft.rotiUnclear) add("रोटी अस्पष्ट (${draft.rotiRawText})")
        }.joinToString(" | ")

        viewModelScope.launch {
            _draftState.update { it.copy(isSaving = true, formError = null) }
            val result = localRepo.saveNewRecord(
                registerDate = draft.registerDate,
                originalPhotoPath = origPath,
                processedPhotoPath = draft.processedPhotoPath ?: origPath,
                fullMasoorQty = fullQty,
                halfMasoorQty = halfQty,
                rotiQty = rotiQty,
                rawTranscript = draft.rawTranscript,
                unclearSummary = unclearSummary,
                notes = draft.notes,
                wasAiRead = draft.wasAiRead,
                recordUuid = draft.draftUuid
            )
            result.fold(
                onSuccess = { saved ->
                    _draftState.value = RegisterDraftState()
                    _snackbarMessage.value =
                        "नोंद (${DailyRegisterCalculator.formatDisplayDate(saved.registerDate)}) कायमस्वरूपी जतन झाली!"
                    onSaved()
                },
                onFailure = { err ->
                    _draftState.update {
                        it.copy(
                            isSaving = false,
                            formError = err.message ?: "नोंद जतन करता आली नाही"
                        )
                    }
                }
            )
        }
    }

    fun openRecordDetail(record: DailyRegisterRecordEntity) {
        _selectedRecordId.value = record.id
    }

    fun closeRecordDetail() {
        _selectedRecordId.value = null
    }

    fun updateSavedRecord(
        recordId: Long,
        registerDate: String,
        fullQty: Int,
        halfQty: Int,
        rotiQty: Int,
        notes: String
    ) {
        viewModelScope.launch {
            localRepo.updateExistingRecord(recordId, registerDate, fullQty, halfQty, rotiQty, notes)
                .fold(
                    onSuccess = {
                        _snackbarMessage.value = "रजिस्टर नोंद अद्ययावत झाली!"
                    },
                    onFailure = { err ->
                        _snackbarMessage.value = err.message ?: "नोंद अद्ययावत करता आली नाही"
                    }
                )
        }
    }

    fun deleteRecordsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (_selectedRecordId.value in ids) {
                _selectedRecordId.value = null
            }
            localRepo.deleteRecords(ids).fold(
                onSuccess = { count ->
                    _snackbarMessage.value = "$count नोंद(ी) व संबंधित फोटो हटवले."
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.message ?: "नोंद हटवता आली नाही"
                }
            )
        }
    }

    fun shiftMonth(delta: Int) {
        _selectedMonthOffset.update { (it + delta).coerceAtMost(0) }
    }

    fun exportZipBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _zipBackupState.value = ZipBackupUiState.Loading("फोटो व नोंदी ZIP बॅकअपमध्ये जतन होत आहेत...")
            val result = withContext(Dispatchers.IO) {
                val all = localRepo.getAllRecordsOnce()
                RegisterZipBackupManager.exportToUri(context, uri, all)
            }
            result.fold(
                onSuccess = { count ->
                    _zipBackupState.value = ZipBackupUiState.Success(
                        "ऑफलाईन ZIP बॅकअप पूर्ण! $count नोंदी व फोटो सुरक्षित जतन केले."
                    )
                },
                onFailure = { err ->
                    _zipBackupState.value = ZipBackupUiState.Error(
                        err.message ?: "ZIP बॅकअप जतन करता आला नाही"
                    )
                }
            )
        }
    }

    fun createShareableZipBackup(context: Context, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            _zipBackupState.value = ZipBackupUiState.Loading("शेअर करण्यासाठी ZIP फाईल तयार होत आहे...")
            val result = withContext(Dispatchers.IO) {
                val all = localRepo.getAllRecordsOnce()
                RegisterZipBackupManager.exportShareableZip(context, all)
            }
            result.fold(
                onSuccess = { (_, uri) ->
                    _zipBackupState.value = ZipBackupUiState.Success("बॅकअप ZIP शेअरसाठी तयार आहे!")
                    onReady(uri)
                },
                onFailure = { err ->
                    _zipBackupState.value = ZipBackupUiState.Error(
                        err.message ?: "ZIP तयार करता आली नाही"
                    )
                }
            )
        }
    }

    fun restoreZipBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _zipBackupState.value = ZipBackupUiState.Loading("ZIP बॅकअपमधून नोंदी व फोटो रिस्टोअर होत आहेत...")
            val result = withContext(Dispatchers.IO) {
                val photosDir = RegisterPhotoManager.getPhotosDir(context)
                val parsed = RegisterZipBackupManager.restoreFromUri(context, uri, photosDir)
                parsed.fold(
                    onSuccess = { payload ->
                        val merged = localRepo.mergeRestoredRecords(payload.records)
                        merged.map { added ->
                            Triple(payload.records.size, added, payload.restoredPhotosCount)
                        }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            result.fold(
                onSuccess = { (totalInZip, addedCount, photosCount) ->
                    _zipBackupState.value = ZipBackupUiState.Success(
                        "रिस्टोअर पूर्ण! ZIP मधील $totalInZip नोंदींपैकी $addedCount नवीन नोंदी ($photosCount फोटो) जोडल्या. जुन्या नोंदी सुरक्षित राहिल्या."
                    )
                },
                onFailure = { err ->
                    _zipBackupState.value = ZipBackupUiState.Error(
                        err.message ?: "ZIP रिस्टोअर करता आला नाही"
                    )
                }
            )
        }
    }

    fun clearZipBackupState() {
        _zipBackupState.value = ZipBackupUiState.Idle
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun computeMonthlyReport(
        allRecords: List<DailyRegisterRecordEntity>,
        monthOffset: Int
    ): MonthlyRegisterReport {
        val targetMonthKey = DailyRegisterCalculator.getMonthKeyForOffset(monthOffset)
        val monthRecords = allRecords.filter {
            DailyRegisterCalculator.formatMonthKey(it.registerDate) == targetMonthKey
        }
        val fullQ = monthRecords.sumOf { it.fullMasoorQty }
        val halfQ = monthRecords.sumOf { it.halfMasoorQty }
        val rotiQ = monthRecords.sumOf { it.rotiQty }
        val calc = DailyRegisterCalculator.calculate(fullQ, halfQ, rotiQ)

        val shortFormatter = SimpleDateFormat(
            "MMM",
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        )
        val trend = (5 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, monthOffset - back)
            }
            val mKey = DailyRegisterCalculator.formatIsoDate(cal.timeInMillis).substring(0, 7)
            val mRecs = allRecords.filter {
                DailyRegisterCalculator.formatMonthKey(it.registerDate) == mKey
            }
            MonthlyRegisterTrendPoint(
                label = shortFormatter.format(cal.time),
                totalIncome = mRecs.sumOf { it.totalIncome },
                totalQuantity = mRecs.sumOf { it.totalQuantity }
            )
        }

        return MonthlyRegisterReport(
            monthTitle = DailyRegisterCalculator.formatMonthLabel(monthOffset),
            monthKey = targetMonthKey,
            recordsCount = monthRecords.size,
            fullMasoorQty = calc.fullMasoorQty,
            fullMasoorIncome = calc.fullMasoorIncome,
            halfMasoorQty = calc.halfMasoorQty,
            halfMasoorIncome = calc.halfMasoorIncome,
            rotiQty = calc.rotiQty,
            rotiIncome = calc.rotiIncome,
            totalQuantity = calc.totalQuantity,
            totalIncome = calc.totalIncome,
            sixMonthTrend = trend
        )
    }
}
