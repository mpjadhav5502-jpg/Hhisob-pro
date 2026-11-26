package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BillEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.LocalBillingRepository
import com.example.data.local.LocalRegisterRepository
import com.example.data.local.ShopSettingsEntity
import com.example.data.remote.CloudBackupRepository
import com.example.domain.BillCalculation
import com.example.domain.BillingCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CustomerSummary(
    val customer: CustomerEntity,
    val totalBills: Int,
    val totalSpent: Int,
    val totalPending: Int,
    val bills: List<BillEntity>
)

data class MonthlyBarPoint(
    val label: String,
    val totalSales: Int,
    val cashSales: Int,
    val onlineSales: Int,
    val pendingSales: Int,
    val billCount: Int
)

data class MonthlySalesReport(
    val monthTitle: String,
    val totalBills: Int,
    val totalSales: Int,
    val cashReceived: Int,
    val onlineReceived: Int,
    val pendingAmount: Int,
    val sixMonthTrend: List<MonthlyBarPoint>
)

data class DashboardStats(
    val todaySales: Int = 0,
    val todayBillsCount: Int = 0,
    val totalCashReceived: Int = 0,
    val totalOnlineReceived: Int = 0,
    val totalPendingPayments: Int = 0,
    val totalBillsCount: Int = 0,
    val totalAllTimeSales: Int = 0,
    val monthlyReport: MonthlySalesReport = MonthlySalesReport(
        monthTitle = "",
        totalBills = 0,
        totalSales = 0,
        cashReceived = 0,
        onlineReceived = 0,
        pendingAmount = 0,
        sixMonthTrend = emptyList()
    )
)

data class BillingFormState(
    val customerName: String = "",
    val customerMobile: String = "",
    val fullMasoorQty: Int = 0,
    val halfMasoorQty: Int = 0,
    val rotiQty: Int = 0,
    val cashInputText: String = "",
    val onlineInputText: String = "",
    val onlineConfirmed: Boolean = false,
    val isSaving: Boolean = false,
    val formError: String? = null
)

sealed interface CloudSyncState {
    data object Idle : CloudSyncState
    data class Loading(val message: String) : CloudSyncState
    data class Success(val message: String) : CloudSyncState
    data class Error(val message: String) : CloudSyncState
}

class MasoorBillingViewModel(
    private val localRepo: LocalBillingRepository,
    private val cloudRepo: CloudBackupRepository,
    private val localRegisterRepo: LocalRegisterRepository? = null
) : ViewModel() {

    private val _formState = MutableStateFlow(BillingFormState())
    val formState: StateFlow<BillingFormState> = _formState.asStateFlow()

    private val _selectedMonthOffset = MutableStateFlow(0)
    val selectedMonthOffset: StateFlow<Int> = _selectedMonthOffset.asStateFlow()

    private val _previewBillId = MutableStateFlow<Long?>(null)
    private val _cloudSyncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle)
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val settings: StateFlow<ShopSettingsEntity> = localRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ShopSettingsEntity())

    val bills: StateFlow<List<BillEntity>> = localRepo.billsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val customers: StateFlow<List<CustomerSummary>> = combine(
        localRepo.customersFlow,
        localRepo.billsFlow
    ) { customerList, billList ->
        customerList.map { customer ->
            val matchedBills = billList.filter { bill ->
                bill.customerId == customer.id ||
                    (customer.mobile.isNotBlank() && bill.customerMobile == customer.mobile)
            }
            CustomerSummary(
                customer = customer,
                totalBills = matchedBills.size,
                totalSpent = matchedBills.sumOf { it.grandTotal },
                totalPending = matchedBills.sumOf { it.pendingAmount },
                bills = matchedBills
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val currentCalculation: StateFlow<BillCalculation> = combine(
        _formState,
        settings
    ) { form, currentSettings ->
        val cash = BillingCalculator.parseNonNegativeIntOrNull(form.cashInputText) ?: -1
        val online = BillingCalculator.parseNonNegativeIntOrNull(form.onlineInputText) ?: -1
        BillingCalculator.calculate(
            fullMasoorQty = form.fullMasoorQty,
            fullMasoorPrice = currentSettings.fullMasoorPrice,
            halfMasoorQty = form.halfMasoorQty,
            halfMasoorPrice = currentSettings.halfMasoorPrice,
            rotiQty = form.rotiQty,
            rotiPrice = currentSettings.rotiPrice,
            cashInput = cash,
            onlineInput = online,
            onlineConfirmed = form.onlineConfirmed
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        BillingCalculator.calculate(0, 100, 0, 50, 0, 15, 0, 0, false)
    )

    val previewBill: StateFlow<BillEntity?> = combine(
        _previewBillId,
        bills
    ) { billId, allBills ->
        if (billId == null) null else allBills.firstOrNull { it.id == billId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val dashboardStats: StateFlow<DashboardStats> = combine(
        bills,
        _selectedMonthOffset
    ) { allBills, monthOffset ->
        computeDashboardStats(allBills, monthOffset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardStats())

    init {
        viewModelScope.launch {
            localRepo.ensureDefaultsInitialized()
        }
    }

    fun onCustomerNameChanged(name: String) {
        _formState.update { it.copy(customerName = name.take(80), formError = null) }
    }

    fun onCustomerMobileChanged(mobile: String) {
        val digitsOnly = mobile.filter { it.isDigit() }.take(10)
        _formState.update { it.copy(customerMobile = digitsOnly, formError = null) }
    }

    fun selectCustomer(customer: CustomerEntity) {
        _formState.update {
            it.copy(
                customerName = customer.name,
                customerMobile = customer.mobile,
                formError = null
            )
        }
    }

    fun fillWalkInCustomer() {
        _formState.update {
            it.copy(
                customerName = "रोख ग्राहक",
                customerMobile = "",
                formError = null
            )
        }
    }

    fun adjustFullMasoor(delta: Int) {
        _formState.update {
            it.copy(
                fullMasoorQty = (it.fullMasoorQty + delta).coerceIn(0, 999),
                formError = null
            )
        }
    }

    fun adjustHalfMasoor(delta: Int) {
        _formState.update {
            it.copy(
                halfMasoorQty = (it.halfMasoorQty + delta).coerceIn(0, 999),
                formError = null
            )
        }
    }

    fun adjustRoti(delta: Int) {
        _formState.update {
            it.copy(
                rotiQty = (it.rotiQty + delta).coerceIn(0, 999),
                formError = null
            )
        }
    }

    fun onCashInputChanged(raw: String) {
        val sanitized = raw.filter { it.isDigit() }.take(7)
        _formState.update { it.copy(cashInputText = sanitized, formError = null) }
    }

    fun onOnlineInputChanged(raw: String) {
        val sanitized = raw.filter { it.isDigit() }.take(7)
        val parsed = sanitized.toIntOrNull() ?: 0
        // If online amount changes to 0 or changes value, require fresh manual confirmation
        _formState.update {
            it.copy(
                onlineInputText = sanitized,
                onlineConfirmed = if (parsed == 0) false else it.onlineConfirmed,
                formError = null
            )
        }
    }

    fun onOnlineConfirmedChanged(confirmed: Boolean) {
        _formState.update { it.copy(onlineConfirmed = confirmed, formError = null) }
    }

    fun fillFullCash() {
        val total = currentCalculation.value.grandTotal
        if (total > 0) {
            _formState.update {
                it.copy(
                    cashInputText = total.toString(),
                    onlineInputText = "",
                    onlineConfirmed = false,
                    formError = null
                )
            }
        }
    }

    fun fillFullOnline() {
        val total = currentCalculation.value.grandTotal
        if (total > 0) {
            _formState.update {
                it.copy(
                    cashInputText = "",
                    onlineInputText = total.toString(),
                    // Never auto-confirm online payments; user must manually confirm receipt
                    onlineConfirmed = false,
                    formError = null
                )
            }
        }
    }

    fun resetCurrentBillForm() {
        _formState.value = BillingFormState()
    }

    fun saveCurrentBill() {
        val form = _formState.value
        if (form.isSaving) return // Duplicate submission guard

        val cleanName = form.customerName.trim()
        val cleanMobile = form.customerMobile.trim()
        if (cleanName.isEmpty()) {
            _formState.update { it.copy(formError = "कृपया ग्राहकाचे नाव टाका") }
            return
        }
        if (!BillingCalculator.isValidMobileOrEmpty(cleanMobile)) {
            _formState.update { it.copy(formError = "कृपया १० अंकी वैध मोबाईल नंबर टाका") }
            return
        }

        val cash = BillingCalculator.parseNonNegativeIntOrNull(form.cashInputText)
        val online = BillingCalculator.parseNonNegativeIntOrNull(form.onlineInputText)
        if (cash == null || online == null) {
            _formState.update { it.copy(formError = "कृपया वैध रक्कम टाका") }
            return
        }

        val calc = currentCalculation.value
        if (!calc.canSave) {
            _formState.update { it.copy(formError = calc.validationError ?: "कृपया बिल तपशील तपासा") }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, formError = null) }
            val result = localRepo.saveBill(
                customerNameRaw = cleanName,
                customerMobileRaw = cleanMobile,
                fullMasoorQty = form.fullMasoorQty,
                halfMasoorQty = form.halfMasoorQty,
                rotiQty = form.rotiQty,
                cashInput = cash,
                onlineInput = online,
                onlineConfirmed = form.onlineConfirmed
            )
            result.fold(
                onSuccess = { savedBill ->
                    _formState.value = BillingFormState()
                    _previewBillId.value = savedBill.id
                    _snackbarMessage.value = "बिल ${savedBill.billNumber} जतन झाले!"
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isSaving = false,
                            formError = error.message ?: "बिल जतन करताना त्रुटी आली"
                        )
                    }
                }
            )
        }
    }

    fun openBillPreview(bill: BillEntity) {
        _previewBillId.value = bill.id
    }

    fun closeBillPreview() {
        _previewBillId.value = null
    }

    fun settlePendingPayment(
        billId: Long,
        addCash: Int,
        addOnline: Int,
        onlineConfirmed: Boolean
    ) {
        viewModelScope.launch {
            localRepo.settlePendingPayment(billId, addCash, addOnline, onlineConfirmed).fold(
                onSuccess = { updated ->
                    _snackbarMessage.value = if (updated.pendingAmount == 0) {
                        "बिल ${updated.billNumber} चे पूर्ण पेमेंट जमा झाले!"
                    } else {
                        "पेमेंट जमा झाले! उर्वरित बाकी: ${BillingCalculator.formatInr(updated.pendingAmount)}"
                    }
                },
                onFailure = { error ->
                    _snackbarMessage.value = error.message ?: "पेमेंट जमा करता आले नाही"
                }
            )
        }
    }

    fun saveCustomer(name: String, mobile: String) {
        viewModelScope.launch {
            localRepo.saveCustomer(name, mobile).fold(
                onSuccess = { saved ->
                    _snackbarMessage.value = "ग्राहक '${saved.name}' जतन केले!"
                },
                onFailure = { error ->
                    _snackbarMessage.value = error.message ?: "ग्राहक जतन करता आला नाही"
                }
            )
        }
    }

    fun saveSettings(
        shopName: String,
        shopSubtitle: String,
        shopPhone: String,
        shopAddress: String,
        fullPrice: Int,
        halfPrice: Int,
        rotiPrice: Int
    ) {
        viewModelScope.launch {
            localRepo.updateSettings(
                shopName = shopName,
                shopSubtitle = shopSubtitle,
                shopPhone = shopPhone,
                shopAddress = shopAddress,
                fullMasoorPrice = fullPrice,
                halfMasoorPrice = halfPrice,
                rotiPrice = rotiPrice
            ).fold(
                onSuccess = {
                    _snackbarMessage.value = "सेटिंग्ज आणि किंमती जतन झाल्या!"
                },
                onFailure = { error ->
                    _snackbarMessage.value = error.message ?: "सेटिंग्ज जतन करताना त्रुटी आली"
                }
            )
        }
    }

    fun resetDefaultPrices() {
        viewModelScope.launch {
            localRepo.resetDefaultPrices().fold(
                onSuccess = {
                    _snackbarMessage.value = "किंमती डीफॉल्टवर (₹100 / ₹50 / ₹15) केल्या!"
                },
                onFailure = { error ->
                    _snackbarMessage.value = error.message ?: "किंमती बदलता आल्या नाहीत"
                }
            )
        }
    }

    fun shiftMonth(delta: Int) {
        _selectedMonthOffset.update { (it + delta).coerceAtMost(0) }
    }

    fun backupToCloud(isNetworkConnected: Boolean) {
        if (!isNetworkConnected) {
            _cloudSyncState.value = CloudSyncState.Error("इंटरनेट कनेक्शन उपलब्ध नाही. ऑफलाईन बिले सुरक्षित आहेत.")
            return
        }
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState.Loading("डेटा क्लाउडवर बॅकअप होत आहे...")
            try {
                val (localSettings, localCustomers, localBills) = localRepo.getAllDataForBackup()
                val localRegisterRecords = localRegisterRepo?.getAllRecordsOnce().orEmpty()
                cloudRepo.backupToCloud(
                    settings = localSettings,
                    customers = localCustomers,
                    bills = localBills,
                    registerRecords = localRegisterRecords
                ).fold(
                    onSuccess = { summary ->
                        localRepo.markBackupCompleted(summary.timestampMillis)
                        _cloudSyncState.value = CloudSyncState.Success(
                            "बॅकअप यशस्वी! ${summary.billsCount} बिले, ${summary.customersCount} ग्राहक व ${summary.registerRecordsCount} रजिस्टर नोंदी सुरक्षित झाल्या."
                        )
                    },
                    onFailure = { error ->
                        _cloudSyncState.value = CloudSyncState.Error(
                            error.message ?: "क्लाउड बॅकअप अयशस्वी झाला"
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _cloudSyncState.value = CloudSyncState.Error(e.message ?: "बॅकअप त्रुटी")
            }
        }
    }

    fun restoreFromCloud(isNetworkConnected: Boolean) {
        if (!isNetworkConnected) {
            _cloudSyncState.value = CloudSyncState.Error("रिस्टोअरसाठी इंटरनेट कनेक्शन आवश्यक आहे.")
            return
        }
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState.Loading("क्लाउडवरून डेटा रिस्टोअर होत आहे...")
            try {
                cloudRepo.fetchBackupFromCloud().fold(
                    onSuccess = { payload ->
                        localRepo.restoreFromBackup(
                            remoteSettings = payload.settings,
                            remoteCustomers = payload.customers,
                            remoteBills = payload.bills
                        ).fold(
                            onSuccess = { addedBills ->
                                val addedRegister = localRegisterRepo
                                    ?.mergeRestoredRecords(payload.registerRecords)
                                    ?.getOrDefault(0) ?: 0
                                _cloudSyncState.value = CloudSyncState.Success(
                                    "रिस्टोअर पूर्ण! $addedBills नवीन बिले व $addedRegister नवीन रजिस्टर नोंदी जोडल्या."
                                )
                            },
                            onFailure = { error ->
                                _cloudSyncState.value = CloudSyncState.Error(
                                    error.message ?: "डेटा रिस्टोअर करताना त्रुटी आली"
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        _cloudSyncState.value = CloudSyncState.Error(
                            error.message ?: "क्लाउडवरून डेटा मिळाला नाही"
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _cloudSyncState.value = CloudSyncState.Error(e.message ?: "रिस्टोअर त्रुटी")
            }
        }
    }

    fun resetCloudSyncState() {
        _cloudSyncState.value = CloudSyncState.Idle
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun computeDashboardStats(allBills: List<BillEntity>, monthOffset: Int): DashboardStats {
        val nowCal = Calendar.getInstance()
        val todayYear = nowCal.get(Calendar.YEAR)
        val todayDay = nowCal.get(Calendar.DAY_OF_YEAR)

        val todayBills = allBills.filter { bill ->
            val cal = Calendar.getInstance().apply { timeInMillis = bill.billTimestampMillis }
            cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDay
        }

        val targetMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val targetYear = targetMonthCal.get(Calendar.YEAR)
        val targetMonth = targetMonthCal.get(Calendar.MONTH)
        val monthFormatter = SimpleDateFormat(
            "MMMM yyyy",
            Locale.Builder().setLanguage("mr").setRegion("IN").build()
        )
        val shortMonthFormatter = SimpleDateFormat(
            "MMM",
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        )

        val selectedMonthBills = allBills.filter { bill ->
            val cal = Calendar.getInstance().apply { timeInMillis = bill.billTimestampMillis }
            cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) == targetMonth
        }

        val trend = (5 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = targetMonthCal.timeInMillis
                add(Calendar.MONTH, -back)
            }
            val yr = cal.get(Calendar.YEAR)
            val mo = cal.get(Calendar.MONTH)
            val mBills = allBills.filter { bill ->
                val bCal = Calendar.getInstance().apply { timeInMillis = bill.billTimestampMillis }
                bCal.get(Calendar.YEAR) == yr && bCal.get(Calendar.MONTH) == mo
            }
            MonthlyBarPoint(
                label = shortMonthFormatter.format(cal.time),
                totalSales = mBills.sumOf { it.grandTotal },
                cashSales = mBills.sumOf { it.cashPaid },
                onlineSales = mBills.sumOf { it.onlinePaid },
                pendingSales = mBills.sumOf { it.pendingAmount },
                billCount = mBills.size
            )
        }

        return DashboardStats(
            todaySales = todayBills.sumOf { it.grandTotal },
            todayBillsCount = todayBills.size,
            totalCashReceived = allBills.sumOf { it.cashPaid },
            totalOnlineReceived = allBills.sumOf { it.onlinePaid },
            totalPendingPayments = allBills.sumOf { it.pendingAmount },
            totalBillsCount = allBills.size,
            totalAllTimeSales = allBills.sumOf { it.grandTotal },
            monthlyReport = MonthlySalesReport(
                monthTitle = monthFormatter.format(targetMonthCal.time),
                totalBills = selectedMonthBills.size,
                totalSales = selectedMonthBills.sumOf { it.grandTotal },
                cashReceived = selectedMonthBills.sumOf { it.cashPaid },
                onlineReceived = selectedMonthBills.sumOf { it.onlinePaid },
                pendingAmount = selectedMonthBills.sumOf { it.pendingAmount },
                sixMonthTrend = trend
            )
        )
    }
}
