package com.ybugmobile.vaktiva.ui.home

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val settingsManager: SettingsManager,
    private val locationWrapper: LocationWrapper,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPrayerDay: Flow<PrayerDay?> = combine(allPrayerDays, selectedDate) { days, date ->
        days.find { it.date == date }
    }

    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isAdhanPlaying = MutableStateFlow(false)
    private val _playingPrayerName = MutableStateFlow<String?>(null)

    private var mediaController: MediaController? = null

    val calculationMethods = CALCULATION_METHODS

    init {
        // Update current time every second
        tickerFlow(1000).onEach {
            _currentTime.value = LocalDateTime.now()
        }.launchIn(viewModelScope)

        // Automatically fetch location and data on launch
        onPermissionsGranted()
        
        // Observe all prayer days and reschedule alarms when they change
        allPrayerDays
            .filter { it.isNotEmpty() }
            .onEach { days ->
                alarmScheduler.scheduleNextAlarm(days)
            }
            .launchIn(viewModelScope)

        setupMediaController()
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, AdhanService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isAdhanPlaying.value = isPlaying
                        if (isPlaying) {
                            _playingPrayerName.value = mediaController?.currentMediaItem?.mediaMetadata?.title?.toString()
                        } else {
                            _playingPrayerName.value = null
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                            _isAdhanPlaying.value = false
                            _playingPrayerName.value = null
                        }
                    }
                })
                _isAdhanPlaying.value = mediaController?.isPlaying == true
                _playingPrayerName.value = mediaController?.currentMediaItem?.mediaMetadata?.title?.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    fun stopAdhan() {
        mediaController?.stop()
    }

    private fun tickerFlow(periodMillis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    private val todayPrayerDay: Flow<PrayerDay?> = allPrayerDays.map { days ->
        val today = LocalDate.now()
        days.find { it.date == today }
    }

    private val todayPrayerTimes = todayPrayerDay.map { day ->
        if (day == null) return@map null

        listOf(
            PrayerType.FAJR to (day.timings[PrayerType.FAJR] ?: LocalTime.MIN),
            PrayerType.SUNRISE to (day.timings[PrayerType.SUNRISE] ?: LocalTime.MIN),
            PrayerType.DHUHR to (day.timings[PrayerType.DHUHR] ?: LocalTime.MIN),
            PrayerType.ASR to (day.timings[PrayerType.ASR] ?: LocalTime.MIN),
            PrayerType.MAGHRIB to (day.timings[PrayerType.MAGHRIB] ?: LocalTime.MIN),
            PrayerType.ISHA to (day.timings[PrayerType.ISHA] ?: LocalTime.MIN)
        )
    }

    val nextPrayerInfo: Flow<NextPrayer?> = combine(todayPrayerTimes, currentTime) { prayers, now ->
        if (prayers == null) return@combine null
        val currentTime = now.toLocalTime()
        val next = prayers.firstOrNull { it.second.isAfter(currentTime) }
            ?: prayers.first()

        val remainingDuration = if (next.second.isAfter(currentTime)) {
            Duration.between(currentTime, next.second)
        } else {
            Duration.between(currentTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIN, next.second))
        }

        NextPrayer(
            type = next.first,
            time = next.second,
            remainingDuration = remainingDuration
        )
    }

    val state: StateFlow<HomeViewState> = combine(
        combine(selectedDate, currentTime, currentPrayerDay, ::Triple),
        combine(nextPrayerInfo, isRefreshing, settings, ::Triple),
        combine(_isAdhanPlaying, _playingPrayerName, ::Pair)
    ) { (date, time, prayerDay), (nextPrayer, refreshing, currentSettings), (playing, prayerName) ->
        HomeViewState(
            selectedDate = date,
            currentTime = time,
            currentPrayerDay = prayerDay,
            nextPrayer = nextPrayer,
            isRefreshing = refreshing,
            locationName = currentSettings.locationName,
            isAdhanPlaying = playing,
            playingPrayerName = prayerName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewState())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        
        // Check if data exists for this date, if not fetch it
        val exists = allPrayerDays.value.any { it.date == date }
        
        if (!exists) {
            viewModelScope.launch {
                try {
                    _isRefreshing.value = true
                    val currentSettings = settings.first()
                    val location = locationWrapper.getCurrentLocation()
                    val lat = location?.latitude ?: currentSettings.latitude
                    val lng = location?.longitude ?: currentSettings.longitude

                    prayerRepository.refreshPrayerTimes(
                        year = date.year,
                        month = date.monthValue,
                        latitude = lat,
                        longitude = lng,
                        method = currentSettings.calculationMethod
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetchPrayerData()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateCalculationMethod(methodId: Int) {
        viewModelScope.launch {
            settingsManager.updateCalculationMethod(methodId)
            refresh()
        }
    }

    fun skipNextPrayerAudio(prayerName: String) {
        viewModelScope.launch {
            settingsManager.muteNextPrayer(prayerName, LocalDate.now().toString())
        }
    }

    fun onPermissionsGranted() {
        viewModelScope.launch {
            try {
                fetchPrayerData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchPrayerData() {
        val location = locationWrapper.getCurrentLocation()
        val currentSettings = settings.first()
        val now = LocalDate.now()

        if (location != null) {
            val addressName = locationWrapper.getAddressFromLocation(location.latitude, location.longitude)
                ?: "Current Location"

            settingsManager.saveLocation(
                lat = location.latitude,
                lng = location.longitude,
                name = addressName
            )

            // Fetch current and next month to ensure 14+ days of data
            prayerRepository.refreshPrayerTimes(
                year = now.year,
                month = now.monthValue,
                latitude = location.latitude,
                longitude = location.longitude,
                method = currentSettings.calculationMethod
            )

            // If late in the month, fetch next month too
            if (now.dayOfMonth > 20) {
                val nextMonth = now.plusMonths(1)
                prayerRepository.refreshPrayerTimes(
                    year = nextMonth.year,
                    month = nextMonth.monthValue,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    method = currentSettings.calculationMethod
                )
            }
        } else {
            // Fallback using stored settings if GPS is unavailable
            prayerRepository.refreshPrayerTimes(
                year = now.year,
                month = now.monthValue,
                latitude = currentSettings.latitude,
                longitude = currentSettings.longitude,
                method = currentSettings.calculationMethod
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.let {
            it.release()
            mediaController = null
        }
    }

    companion object {
        val CALCULATION_METHODS = listOf(
            "Muslim World League" to 3,
            "Islamic Society of North America (ISNA)" to 2,
            "Egyptian General Authority of Survey" to 5,
            "Umm al-Qura University, Makkah" to 4,
            "University of Islamic Sciences, Karachi" to 1,
            "Institute of Geophysics, University of Tehran" to 7,
            "Gulf Region" to 8,
            "Kuwait" to 9,
            "Qatar" to 10,
            "Majlis Ugama Islam Singapura, Singapore" to 11,
            "Turkey" to 13
        )
    }
}
