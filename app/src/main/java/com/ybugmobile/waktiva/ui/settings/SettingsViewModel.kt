package com.ybugmobile.waktiva.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.waktiva.data.backup.InvalidPrayerLogBackupException
import com.ybugmobile.waktiva.data.backup.PrayerLogBackupCodec
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerLogRepository
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.manager.TimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val prayerRepository: PrayerRepository,
    private val prayerLogRepository: PrayerLogRepository,
    private val timeManager: TimeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed interface UiEvent {
        data object PrayerHistoryDeleted : UiEvent
        data object PrayerHistoryDeleteFailed : UiEvent
        data object PrayerLogExported : UiEvent
        data object PrayerLogExportFailed : UiEvent
        /** [added] prayers from the file weren't in the log yet. */
        data class PrayerLogImported(val added: Int) : UiEvent
        data object PrayerLogImportInvalid : UiEvent
        data object PrayerLogImportFailed : UiEvent
        data object PrayerLogCleared : UiEvent
    }

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents = _uiEvents.asSharedFlow()

    val settings = settingsManager.settingsFlow
    val currentTime = timeManager.currentTime

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMadhab(madhab: Int) {
        viewModelScope.launch {
            settingsManager.updateMadhab(madhab)
            val s = settingsManager.settingsFlow.first()
            val lat = s.latitude ?: return@launch
            val lng = s.longitude ?: return@launch
            prayerRepository.recalculatePrayerTimesLocally(s.calculationMethod, madhab, lat, lng)
        }
    }

    fun setCalculationMethod(method: Int) {
        viewModelScope.launch {
            settingsManager.updateCalculationMethod(method)
            val s = settingsManager.settingsFlow.first()
            val lat = s.latitude ?: return@launch
            val lng = s.longitude ?: return@launch
            prayerRepository.recalculatePrayerTimesLocally(method, s.madhab, lat, lng)
        }
    }

    fun updateLanguage(language: String, onUpdated: () -> Unit = {}) {
        viewModelScope.launch {
            settingsManager.updateLanguage(language)
            onUpdated()
        }
    }

    fun setPlayAdhanAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePlayAdhanAudio(enabled)
        }
    }

    fun setPlayAdhanDua(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePlayAdhanDua(enabled)
        }
    }

    fun setPrayerAdhanEnabled(type: PrayerType, enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePrayerAdhanEnabled(type, enabled)
        }
    }

    fun setSetupComplete(complete: Boolean) {
        viewModelScope.launch {
            settingsManager.setSetupComplete(complete)
        }
    }

    fun deletePastData() {
        viewModelScope.launch {
            try {
                val today = LocalDate.now().toString()
                prayerRepository.deletePastData(today)
                _uiEvents.emit(UiEvent.PrayerHistoryDeleted)
            } catch (_: Exception) {
                _uiEvents.emit(UiEvent.PrayerHistoryDeleteFailed)
            }
        }
    }

    /** Writes the whole prayer log to [uri], a file the user picked to save it in. */
    fun exportPrayerLog(uri: Uri) {
        viewModelScope.launch {
            val event = try {
                val json = PrayerLogBackupCodec.encode(prayerLogRepository.backup())
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                UiEvent.PrayerLogExported
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "Prayer log export failed", e)
                UiEvent.PrayerLogExportFailed
            }
            _uiEvents.emit(event)
        }
    }

    /** Adds the prayers in the file at [uri] to the prayer log, keeping the ones already in it. */
    fun importPrayerLog(uri: Uri) {
        viewModelScope.launch {
            val event = try {
                val json = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot read $uri")
                    stream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                UiEvent.PrayerLogImported(prayerLogRepository.restore(PrayerLogBackupCodec.decode(json)))
            } catch (e: InvalidPrayerLogBackupException) {
                Log.w("SettingsViewModel", "Not a prayer log", e)
                UiEvent.PrayerLogImportInvalid
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "Prayer log import failed", e)
                UiEvent.PrayerLogImportFailed
            }
            _uiEvents.emit(event)
        }
    }

    fun clearPrayerLog() {
        viewModelScope.launch {
            prayerLogRepository.clear()
            _uiEvents.emit(UiEvent.PrayerLogCleared)
        }
    }

    fun setShowWeatherEffects(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateShowWeatherEffects(enabled)
        }
    }

    fun setDayCircleStyle(style: DayCircleStyle) {
        viewModelScope.launch {
            settingsManager.updateDayCircleStyle(style)
        }
    }

    fun setPrayerLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePrayerLogEnabled(enabled)
        }
    }

    fun setPrayerLogReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePrayerLogReminder(enabled)
        }
    }

    fun setPrayerLogReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsManager.updatePrayerLogReminderMinutes(minutes)
        }
    }

    fun setPrayerLogGameNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePrayerLogGameNotifications(enabled)
        }
    }

    fun setSilentPrayerNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateShowSilentPrayerNotification(enabled)
        }
    }
}
