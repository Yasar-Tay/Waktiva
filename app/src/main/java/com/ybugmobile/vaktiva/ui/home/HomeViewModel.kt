package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val settingsManager: SettingsManagerInterface,
    private val locationWrapper: LocationWrapper,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel(), DefaultLifecycleObserver {

    val settings = settingsManager.settingsFlow

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPrayerDay: Flow<PrayerDay?> = combine(allPrayerDays, selectedDate) { days, date ->
        days.find { it.date == date }
    }

    private val _timeOffset = MutableStateFlow(Duration.ZERO)
    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isAdhanPlaying = MutableStateFlow(false)
    private val _playingPrayerName = MutableStateFlow<String?>(null)
    
    private var mediaController: MediaController? = null

    val calculationMethods = CALCULATION_METHODS
    private var lastPermissionStatus = isLocationPermissionGranted()

    private val todayPrayerDay: Flow<PrayerDay?> = allPrayerDays.map { days -> days.find { it.date == LocalDate.now() } }

    private val todayPrayerTimes = todayPrayerDay.map { day ->
        if (day == null) return@map null
        PrayerType.entries.map { it to (day.timings[it] ?: LocalTime.MIN) }
    }

    val nextPrayerInfo: Flow<NextPrayer?> = combine(todayPrayerTimes, currentTime, settings) { prayers, now, currentSettings ->
        if (prayers == null) return@combine null
        
        val testEndTimeMillis = currentSettings.testAlarmEndTime
        if (testEndTimeMillis != null) {
            val testEndTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(testEndTimeMillis), ZoneId.systemDefault())
            if (testEndTime.isAfter(now)) {
                return@combine NextPrayer(
                    type = PrayerType.FAJR,
                    time = testEndTime.toLocalTime(),
                    date = testEndTime.toLocalDate(),
                    remainingDuration = Duration.between(now, testEndTime),
                    isTest = true
                )
            }
        }

        val nowTime = now.toLocalTime()
        val nextReal = prayers.firstOrNull { it.second.isAfter(nowTime) } ?: prayers.first()
        val realDateTime = if (nextReal.second.isAfter(nowTime)) now.toLocalDate().atTime(nextReal.second) else now.toLocalDate().plusDays(1).atTime(nextReal.second)

        NextPrayer(nextReal.first, nextReal.second, realDateTime.toLocalDate(), Duration.between(now, realDateTime))
    }

    val currentPrayerInfo: Flow<CurrentPrayer?> = combine(todayPrayerTimes, currentTime) { prayers, now ->
        if (prayers == null) return@combine null
        
        val nowTime = now.toLocalTime()
        val current = prayers.lastOrNull { it.second.isBefore(nowTime) || it.second == nowTime } 
            ?: prayers.last()

        CurrentPrayer(
            type = current.first,
            time = current.second,
            date = now.toLocalDate(),
            elapsedDuration = Duration.between(current.second.atDate(now.toLocalDate()), now)
        )
    }

    init {
        tickerFlow(1000).onEach { 
            _currentTime.value = LocalDateTime.now().plus(_timeOffset.value)
            val s = settings.first()
            s.testAlarmEndTime?.let { end ->
                if (System.currentTimeMillis() > end) {
                    settingsManager.setTestAlarmEndTime(null)
                    settingsManager.clearMutedPrayer()
                }
            }
        }.launchIn(viewModelScope)

        onPermissionsGranted()
        
        combine(allPrayerDays, settings) { days, s -> 
            if (days.isNotEmpty()) {
                alarmScheduler.scheduleNextAlarm(days, s.enablePreAdhanWarning, s.preAdhanWarningMinutes)
            }
        }.launchIn(viewModelScope)

        setupMediaController()
    }

    fun triggerTestAlarm(seconds: Int) {
        val endTimeMillis = System.currentTimeMillis() + (seconds * 1000)
        viewModelScope.launch {
            alarmScheduler.cancelAllAlarms()
            settingsManager.clearMutedPrayer()
            settingsManager.setTestAlarmEndTime(endTimeMillis)
            alarmScheduler.scheduleTestAlarm(seconds)
        }
    }

    fun debugAddHours(minutes: Long) {
        _timeOffset.value = _timeOffset.value.plusMinutes(minutes)
    }

    override fun onResume(owner: LifecycleOwner) {
        val status = isLocationPermissionGranted()
        if (status && !lastPermissionStatus) refresh()
        lastPermissionStatus = status
    }

    private fun isLocationPermissionGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun setupMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, AdhanService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isAdhanPlaying.value = isPlaying
                        _playingPrayerName.value = if (isPlaying) mediaController?.currentMediaItem?.mediaMetadata?.title?.toString()?.replace("Adhan: ", "") else null
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            _isAdhanPlaying.value = false
                            _playingPrayerName.value = null
                        }
                    }
                })
                _isAdhanPlaying.value = mediaController?.isPlaying == true
                _playingPrayerName.value = if (mediaController?.isPlaying == true) mediaController?.currentMediaItem?.mediaMetadata?.title?.toString()?.replace("Adhan: ", "") else null
            } catch (e: Exception) { e.printStackTrace() }
        }, MoreExecutors.directExecutor())
    }

    fun stopAdhan() {
        mediaController?.stop()
        _isAdhanPlaying.value = false
        _playingPrayerName.value = null
    }

    private fun tickerFlow(periodMillis: Long) = flow { while (true) { emit(Unit); delay(periodMillis) } }

    private val _hasSettled = MutableStateFlow(false)

    val state: StateFlow<HomeViewState> = combine(
        combine(selectedDate, currentTime, currentPrayerDay, ::Triple),
        combine(nextPrayerInfo, currentPrayerInfo, isRefreshing, ::Triple),
        combine(settings, _isAdhanPlaying, _playingPrayerName, ::Triple)
    ) { (date, time, prayerDay), (nextPrayer, currentPrayer, refreshing), (currentSettings, playing, prayerName) ->
        
        val isMuted = currentSettings.mutedPrayerName.equals(nextPrayer?.type?.name, ignoreCase = true) &&
                      currentSettings.mutedPrayerDate == nextPrayer?.date?.toString()

        HomeViewState(
            selectedDate = date, currentTime = time, currentPrayerDay = prayerDay,
            currentPrayer = currentPrayer,
            nextPrayer = nextPrayer, isRefreshing = refreshing, isLoading = !hasSettled() && prayerDay == null,
            locationName = currentSettings.locationName, 
            isAdhanPlaying = playing, 
            playingPrayerName = prayerName,
            isMuted = isMuted 
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewState(isLoading = true))

    private fun hasSettled() = _hasSettled.value

    fun stopTestAlarm() {
        viewModelScope.launch {
            settingsManager.setTestAlarmEndTime(null)
            settingsManager.clearMutedPrayer()
            alarmScheduler.cancelAllAlarms()
            val days = allPrayerDays.value
            val s = settings.first()
            if (days.isNotEmpty()) {
                alarmScheduler.scheduleNextAlarm(days, s.enablePreAdhanWarning, s.preAdhanWarningMinutes)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (allPrayerDays.value.none { it.date == date }) viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val s = settings.first()
                val loc = locationWrapper.getCurrentLocation()
                val lat = loc?.latitude ?: s.latitude
                val lng = loc?.longitude ?: s.longitude
                prayerRepository.refreshPrayerTimes(date.year, date.monthValue, lat, lng, s.calculationMethod)
            } finally { _isRefreshing.value = false }
        }
    }

    fun refresh() = viewModelScope.launch {
        _isRefreshing.value = true
        try { fetchPrayerData() } finally { _isRefreshing.value = false; _hasSettled.value = true }
    }

    fun updateCalculationMethod(methodId: Int) = viewModelScope.launch { settingsManager.updateCalculationMethod(methodId); refresh() }

    fun toggleSkipNextPrayerAudio(prayerName: String, prayerDate: LocalDate) = viewModelScope.launch {
        val s = settings.first()
        val currentlyMuted = s.mutedPrayerName.equals(prayerName, ignoreCase = true) &&
                             s.mutedPrayerDate == prayerDate.toString()
        
        if (currentlyMuted) {
            settingsManager.clearMutedPrayer()
        } else {
            settingsManager.muteNextPrayer(prayerName, prayerDate.toString())
        }
    }

    fun onPermissionsGranted() = viewModelScope.launch { try { fetchPrayerData() } finally { _hasSettled.value = true } }

    private suspend fun fetchPrayerData() {
        val loc = locationWrapper.getCurrentLocation()
        val s = settings.first()
        val now = LocalDate.now()
        val nextMonth = now.plusMonths(1)
        val lat = loc?.latitude ?: s.latitude
        val lng = loc?.longitude ?: s.longitude
        if (loc != null) settingsManager.saveLocation(lat, lng, locationWrapper.getAddressFromLocation(lat, lng) ?: "Current Location")
        
        // Fetch Current Month
        prayerRepository.refreshPrayerTimes(now.year, now.monthValue, lat, lng, s.calculationMethod)
        
        // Fetch Next Month to ensure we have ~60 days of data and show Ramadan early if it's coming
        prayerRepository.refreshPrayerTimes(nextMonth.year, nextMonth.monthValue, lat, lng, s.calculationMethod)
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
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
