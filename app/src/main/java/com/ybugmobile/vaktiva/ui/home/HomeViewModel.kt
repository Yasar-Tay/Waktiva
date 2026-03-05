package com.ybugmobile.vaktiva.ui.home

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.HijriUtils
import com.ybugmobile.vaktiva.domain.model.MoonPhase
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.domain.manager.TimeManager
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.data.sensor.CompassManager
import com.ybugmobile.vaktiva.service.AdhanService
import com.ybugmobile.vaktiva.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shredzone.commons.suncalc.SunPosition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Core ViewModel for the Home screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val settingsManager: SettingsManagerInterface,
    private val locationWrapper: LocationWrapper,
    private val alarmScheduler: AlarmScheduler,
    private val timeManager: TimeManager,
    private val compassManager: CompassManager,
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

    val currentTime = timeManager.currentTime

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isAdhanPlaying = MutableStateFlow(false)
    private val _playingPrayerName = MutableStateFlow<String?>(null)
    
    private val _isNetworkAvailable = MutableStateFlow(PermissionUtils.isNetworkAvailable(context))
    private val _isLocationEnabled = MutableStateFlow(PermissionUtils.isLocationEnabled(context))
    private val _isLocationPermissionGranted = MutableStateFlow(PermissionUtils.isLocationPermissionGranted(context))
    private val _hasSystemIssues = MutableStateFlow(false)

    // Weather State
    private val _weatherCondition = MutableStateFlow(WeatherCondition.UNKNOWN)
    private val _temperature = MutableStateFlow<Double?>(null)

    private var mediaController: MediaController? = null

    val calculationMethods = CALCULATION_METHODS
    private var lastPermissionStatus = PermissionUtils.isLocationPermissionGranted(context)

    private val todayPrayerDay: Flow<PrayerDay?> = allPrayerDays.map { days -> days.find { it.date == LocalDate.now() } }

    private val todayPrayerTimes = todayPrayerDay.map { day ->
        if (day == null) return@map null
        PrayerType.entries.map { it to (day.timings[it] ?: LocalTime.MIN) }
    }

    val moonPhase = combine(
        selectedDate,
        currentTime.map { it.withMinute(0).withSecond(0).withNano(0) }.distinctUntilChanged()
    ) { date, hourlyTime ->
        val dateTimeToCalculate = if (date == LocalDate.now()) hourlyTime else date.atStartOfDay()
        prayerRepository.getMoonPhase(dateTimeToCalculate)
    }

    // Solar calculation logic for dynamic lens flare
    private val sunPosition = combine(currentTime, settings) { time, s ->
        val lat = s.latitude ?: 47.491143
        val lng = s.longitude ?: 7.5833342
        SunPosition.compute()
            .on(time)
            .at(lat, lng)
            .timezone(ZoneId.systemDefault())
            .execute()
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
        currentTime.onEach { now ->
            val s = settings.first()
            s.testAlarmEndTime?.let { end ->
                if (System.currentTimeMillis() > end) {
                    settingsManager.setTestAlarmEndTime(null)
                    settingsManager.clearMutedPrayer()
                }
            }
            if (now.second % 30 == 0) {
                updateHealthStatus()
            }
            
            // Poll weather every 60 minutes
            if (now.minute == 0 && now.second == 0) {
                refreshWeather()
            }
        }.launchIn(viewModelScope)

        updateHealthStatus()
        refreshWeather()
        onPermissionsGranted()
        
        combine(allPrayerDays, settings) { days, s -> 
            if (days.isNotEmpty()) {
                alarmScheduler.scheduleNextAlarm(days, s.enablePreAdhanWarning, s.preAdhanWarningMinutes)
            }
        }.launchIn(viewModelScope)

        setupMediaController()
    }

    private fun refreshWeather() {
        viewModelScope.launch {
            val s = settings.first()
            val lat = s.latitude ?: return@launch
            val lng = s.longitude ?: return@launch
            
            prayerRepository.getWeatherData(lat, lng).onSuccess { info ->
                _weatherCondition.value = info.condition
                _temperature.value = info.temperature
            }
        }
    }

    fun debugSetWeather(condition: WeatherCondition) {
        _weatherCondition.value = condition
    }

    private fun updateHealthStatus() {
        viewModelScope.launch {
            val network = withContext(Dispatchers.IO) { PermissionUtils.isNetworkAvailable(context) }
            val locationEnabled = withContext(Dispatchers.IO) { PermissionUtils.isLocationEnabled(context) }
            val locationPermission = withContext(Dispatchers.IO) { PermissionUtils.isLocationPermissionGranted(context) }
            val criticalIssues = withContext(Dispatchers.IO) { checkCriticalSystemIssues() }
            _isNetworkAvailable.value = network
            _isLocationEnabled.value = locationEnabled
            _isLocationPermissionGranted.value = locationPermission
            _hasSystemIssues.value = criticalIssues
        }
    }

    private fun checkCriticalSystemIssues(): Boolean {
        return PermissionUtils.isDoNotDisturbActive(context) || 
               PermissionUtils.areNotificationChannelsMuted(context) ||
               !PermissionUtils.isNotificationPermissionGranted(context)
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
        timeManager.addMinutes(minutes)
    }

    override fun onResume(owner: LifecycleOwner) {
        val status = PermissionUtils.isLocationPermissionGranted(context)
        if (status && !lastPermissionStatus) refresh()
        lastPermissionStatus = status
        updateHealthStatus()
        refreshWeather()
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
                        updatePlayingPrayerName()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            _isAdhanPlaying.value = false
                            _playingPrayerName.value = null
                        }
                    }
                })
                _isAdhanPlaying.value = mediaController?.isPlaying == true
                updatePlayingPrayerName()
            } catch (e: Exception) { e.printStackTrace() }
        }, MoreExecutors.directExecutor())
    }

    private fun updatePlayingPrayerName() {
        val metadata = mediaController?.currentMediaItem?.mediaMetadata
        val name = metadata?.extras?.getString(NotificationHelper.EXTRA_PRAYER_NAME)
        _playingPrayerName.value = name
    }

    fun stopAdhan() {
        mediaController?.stop()
        _isAdhanPlaying.value = false
        _playingPrayerName.value = null
    }

    private val _hasSettled = MutableStateFlow(false)

    val state: StateFlow<HomeViewState> = combine(
        selectedDate, currentTime, currentPrayerDay, moonPhase,
        nextPrayerInfo, currentPrayerInfo, isRefreshing, 
        _isNetworkAvailable, _isLocationEnabled, _isLocationPermissionGranted, _hasSystemIssues,
        sunPosition, compassManager.compassFlow, _weatherCondition, _temperature,
        settings, _isAdhanPlaying, _playingPrayerName, allPrayerDays
    ) { args ->
        val date = args[0] as LocalDate
        val time = args[1] as LocalDateTime
        val prayerDay = args[2] as? PrayerDay
        val moon = args[3] as? MoonPhase
        val next = args[4] as? NextPrayer
        val current = args[5] as? CurrentPrayer
        val refreshing = args[6] as Boolean
        val network = args[7] as Boolean
        val locEnabled = args[8] as Boolean
        val locPerm = args[9] as Boolean
        val issues = args[10] as Boolean
        val sun = args[11] as SunPosition
        val compass = args[12] as CompassData
        val weather = args[13] as WeatherCondition
        val temp = args[14] as? Double
        val currentSettings = args[15] as UserSettings
        val playing = args[16] as Boolean
        val prayerName = args[17] as? String
        val allDaysList = args[18] as List<PrayerDay>
        
        // If the immediate next event is SUNRISE (which has no adhan),
        // we check the mute state for DHUHR instead, as the button targets it.
        val adhanTargetType = if (next?.type == PrayerType.SUNRISE) PrayerType.DHUHR else next?.type
        
        val isMuted = currentSettings.mutedPrayerName.equals(adhanTargetType?.name, ignoreCase = true) &&
                      currentSettings.mutedPrayerDate == next?.date?.toString()

        val effectiveHijri = HijriUtils.getEffectiveHijriDate(
            targetDate = date,
            allPrayerDays = allDaysList
        )

        HomeViewState(
            selectedDate = date, 
            currentTime = time, 
            currentPrayerDay = prayerDay,
            currentPrayer = current,
            nextPrayer = next, 
            moonPhase = moon,
            effectiveHijriDate = effectiveHijri,
            isRefreshing = refreshing, 
            isLoading = !hasSettled() && prayerDay == null,
            locationName = currentSettings.locationName, 
            isAdhanPlaying = playing, 
            playingPrayerName = prayerName,
            isMuted = isMuted,
            isHijriSelected = currentSettings.isHijriSelected,
            isNetworkAvailable = network,
            isLocationEnabled = locEnabled,
            isLocationPermissionGranted = locPerm,
            hasSystemIssues = issues,
            sunAzimuth = sun.azimuth.toFloat(),
            sunAltitude = sun.altitude.toFloat(),
            compassAzimuth = compass.azimuth,
            weatherCondition = weather,
            temperature = temp
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
        viewModelScope.launch {
            if (allPrayerDays.value.none { it.date == date }) {
                try {
                    _isRefreshing.value = true
                    val s = settings.first()
                    val loc = locationWrapper.getCurrentLocation()
                    val lat = loc?.latitude ?: s.latitude
                    val lng = loc?.longitude ?: s.longitude
                    
                    if (lat != null && lng != null) {
                        prayerRepository.refreshPrayerTimes(date.year, date.monthValue, lat, lng, s.calculationMethod)
                    }
                } finally { _isRefreshing.value = false }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _isRefreshing.value = true
        try { fetchPrayerData(forceFullRefresh = true); refreshWeather() } finally { _isRefreshing.value = false; _hasSettled.value = true }
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

    fun toggleCalendarType(isHijri: Boolean) = viewModelScope.launch {
        settingsManager.updateCalendarType(isHijri)
    }

    fun onPermissionsGranted() = viewModelScope.launch { try { fetchPrayerData() } finally { _hasSettled.value = true } }

    private suspend fun fetchPrayerData(forceFullRefresh: Boolean = false) {
        val loc = locationWrapper.getCurrentLocation()
        val s = settings.first()
        val now = LocalDate.now()
        
        val lat = loc?.latitude ?: s.latitude
        val lng = loc?.longitude ?: s.longitude

        if (lat == null || lng == null) return

        if (loc != null) {
            val address = locationWrapper.getAddressFromLocation(lat, lng)
            if (address != null) {
                settingsManager.saveLocation(lat, lng, address)
            } else {
                settingsManager.saveLocation(lat, lng, s.locationName)
            }
        }
        
        if (forceFullRefresh) {
            for (i in 0..2) {
                val fetchDate = now.plusMonths(i.toLong())
                prayerRepository.refreshPrayerTimes(fetchDate.year, fetchDate.monthValue, lat, lng, s.calculationMethod)
            }
        } else {
            prayerRepository.refreshPrayerTimes(now.year, now.monthValue, lat, lng, s.calculationMethod)
            val nextMonth = now.plusMonths(1)
            prayerRepository.refreshPrayerTimes(nextMonth.year, nextMonth.monthValue, lat, lng, s.calculationMethod)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }

    companion object {
        val CALCULATION_METHODS = listOf(
            R.string.method_mwl to 3,
            R.string.method_isna to 2,
            R.string.method_egypt to 5,
            R.string.method_makkah to 4,
            R.string.method_karachi to 1,
            R.string.method_tehran to 7,
            R.string.method_gulf to 8,
            R.string.method_kuwait to 9,
            R.string.method_qatar to 10,
            R.string.method_singapore to 11,
            R.string.method_turkey to 13
        )
    }
}

data class StateQuad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
