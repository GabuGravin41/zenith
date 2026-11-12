package com.zenith.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenith.data.AiService
import com.zenith.data.local.AppDatabase
import com.zenith.data.local.ProviderCategory
import com.zenith.data.local.TransactionEntity
import com.zenith.data.local.SourceBalance
import com.zenith.data.local.MediaFilterEntity
import com.zenith.data.SettingsManager
import com.zenith.domain.InsightsEngine
import com.zenith.domain.MediaScanner
import com.zenith.domain.ZenithInsight
import com.zenith.domain.model.MediaFile
import com.zenith.domain.model.MediaType
import com.zenith.worker.SmsScanWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val settingsManager = SettingsManager(application)

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    private val _mediaFiles = MutableStateFlow<Map<MediaType, List<MediaFile>>>(emptyMap())
    val mediaFiles: StateFlow<Map<MediaType, List<MediaFile>>> = _mediaFiles.asStateFlow()

    val latestBalance: StateFlow<Double?> = db.transactionDao().getLatestBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sourceBalances: StateFlow<Map<String, Double>> = db.transactionDao().getLatestBalancesBySource()
        .map { list -> list.associate { it.source to it.balance } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val aiApiKey: StateFlow<String?> = settingsManager.aiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val aiProviderUrl: StateFlow<String?> = settingsManager.aiProviderUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://openrouter.ai/api/v1")

    private val _mediaFilters = MutableStateFlow<List<MediaFilterEntity>>(emptyList())
    val mediaFilters: StateFlow<List<MediaFilterEntity>> = _mediaFilters.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isPrivacyModeEnabled = MutableStateFlow(true)
    val isPrivacyModeEnabled: StateFlow<Boolean> = _isPrivacyModeEnabled.asStateFlow()

    val totalAccruedFees: StateFlow<Double> = db.transactionDao().getTotalAccruedFees()
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val merchantFeeSummaries = db.transactionDao().getFeesByMerchant()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingReviewTransactions: StateFlow<List<TransactionEntity>> = db.transactionDao().getTransactionsNeedingReview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _timeHorizonDays = MutableStateFlow<Int?>(null) // null = all time, 7 = 7 days, 30 = 30 days, etc.
    val timeHorizonDays: StateFlow<Int?> = _timeHorizonDays.asStateFlow()

    fun setTimeHorizon(days: Int?) {
        _timeHorizonDays.value = days
    }

    fun reclassifyTransaction(transactionId: String, description: String, newCategory: String, rememberForFuture: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Update this specific transaction
            db.transactionDao().updateTransactionCategory(transactionId, newCategory)
            if (rememberForFuture) {
                // 2. Remember category for this description going forward
                db.providerCategoryDao().insertProviderCategory(ProviderCategory(description, newCategory))
                // 3. Update ALL existing transactions with the same description
                db.transactionDao().updateCategoryByDescription(description, newCategory)
                // 4. KEY FIX: resolve ALL other pending-review items with same description in one shot
                //    so the same merchant/person never re-appears in the clarification queue
                db.transactionDao().resolveAllByDescription(description, newCategory)
            }
        }
    }

    val insights: StateFlow<List<ZenithInsight>> = combine(
        _transactions, 
        _mediaFiles
    ) { tx: List<TransactionEntity>, media: Map<MediaType, List<MediaFile>> ->
        InsightsEngine.generateInsights(tx, media)
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeTransactions()
        observeFilters()
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            db.transactionDao().getAllTransactions().collect {
                _transactions.value = it
            }
        }
    }

    private fun observeFilters() {
        viewModelScope.launch {
            db.mediaFilterDao().getAllFilters().collect {
                _mediaFilters.value = it
            }
        }
    }

    fun addFilter(pattern: String, type: String) {
        viewModelScope.launch {
            db.mediaFilterDao().insertFilter(com.zenith.data.local.MediaFilterEntity(pattern = pattern, type = type))
        }
    }

    fun deleteFilter(filter: com.zenith.data.local.MediaFilterEntity) {
        viewModelScope.launch {
            db.mediaFilterDao().deleteFilter(filter)
        }
    }

    fun toggleFilter(filter: com.zenith.data.local.MediaFilterEntity) {
        viewModelScope.launch {
            db.mediaFilterDao().setFilterEnabled(filter.id, !filter.isEnabled)
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            _isScanning.value = true
            val result = withContext(Dispatchers.IO) {
                MediaScanner.scanWhatsAppMedia()
            }
            _mediaFiles.value = result
            _isScanning.value = false
        }
    }

    fun deleteMedia(paths: List<String>) {
        viewModelScope.launch {
            // 1. Optimistically remove from UI state immediately so delete feels instant
            val pathSet = paths.toSet()
            _mediaFiles.value = _mediaFiles.value.mapValues { (_, files) ->
                files.filter { it.path !in pathSet }
            }
            // 2. Actually delete the files on disk
            withContext(Dispatchers.IO) {
                MediaScanner.deleteFiles(paths)
            }
            // 3. Re-scan in background to sync real state
            val result = withContext(Dispatchers.IO) { MediaScanner.scanWhatsAppMedia() }
            _mediaFiles.value = result
        }
    }

    fun manualScanSms(fullScan: Boolean = false) {
        viewModelScope.launch {
            _isScanning.value = true
            val data = androidx.work.Data.Builder()
                .putBoolean("full_scan", fullScan)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<SmsScanWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "manual_sms_scan",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            kotlinx.coroutines.delay(2000)
            _isScanning.value = false
        }
    }

    fun updateProviderCategory(providerName: String, newCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.providerCategoryDao().insertProviderCategory(ProviderCategory(providerName, newCategory))
            db.transactionDao().updateCategoryByDescription(providerName, newCategory)
        }
    }

    fun saveAiApiKey(key: String) {
        viewModelScope.launch { settingsManager.saveAiApiKey(key) }
    }

    fun saveAiProviderUrl(url: String) {
        viewModelScope.launch { settingsManager.saveAiProviderUrl(url) }
    }

    fun togglePrivacyMode() {
        _isPrivacyModeEnabled.value = !_isPrivacyModeEnabled.value
    }

    fun toggleSuspicious(transactionId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            db.transactionDao().updateSuspiciousStatus(transactionId, !currentStatus)
        }
    }

    // ── AI Chat ──────────────────────────────────────────────────────────────

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    /**
     * Sends a message to the AI. Returns the response text via a callback.
     * Uses DeepSeek V3 with Gemini 2.5 Flash as an automatic fallback.
     */
    fun sendMessage(
        userMessage: String,
        onResponse: (String) -> Unit
    ) {
        viewModelScope.launch {
            val key = aiApiKey.value ?: ""
            if (key.isBlank()) {
                onResponse("⚠️ No API key configured. Please add your OpenRouter key in Settings.")
                return@launch
            }
            _isAiLoading.value = true
            try {
                val response = AiService.chat(
                    userMessage = userMessage,
                    apiKey = key,
                    transactions = _transactions.value,
                    insights = insights.value,
                    latestBalance = latestBalance.value
                )
                onResponse(response)
            } catch (e: Exception) {
                onResponse("⚠️ Could not reach the AI. Check your connection and try again.")
            } finally {
                _isAiLoading.value = false
            }
        }
    }
}
