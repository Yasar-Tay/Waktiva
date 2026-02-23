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
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.HijriUtils
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.domain.manager.TimeManager
import com.ybugmobile.vaktiva.service.AdhanService
import com.ybugmobile.vaktiva.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        }.launchIn(viewModelScope)

        updateHealthStatus()
        onPermissionsGranted()
        
        combine(allPrayerDays, settings) { days, s -> 
            if (days.isNotEmpty()) {
                alarmScheduler.scheduleNextAlarm(days, s.enablePreAdhanWarning, s.preAdhanWarningMinutes)
            }
        }.launchIn(viewModelScope)

        setupMediaController()
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
        // Critical issues are those that PREVENT notification or alarm functionality
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
        combine(selectedDate, currentTime, currentPrayerDay, moonPhase, ::StateQuad),
        combine(nextPrayerInfo, currentPrayerInfo, isRefreshing, ::Triple),
        combine(settings, _isAdhanPlaying, _playingPrayerName, ::Triple),
        combine(_isNetworkAvailable, _isLocationEnabled, _isLocationPermissionGranted, _hasSystemIssues, ::StateQuad),
        allPrayerDays
    ) { (date, time, prayerDay, moon), (nextPrayer, currentPrayer, refreshing), (currentSettings, playing, prayerName), (network, locationEnabled, locPerm, issues), allDays ->
        
        val isMuted = currentSettings.mutedPrayerName.equals(nextPrayer?.type?.name, ignoreCase = true) &&
                      currentSettings.mutedPrayerDate == nextPrayer?.date?.toString()

        val effectiveHijri = HijriUtils.getEffectiveHijriDate(
            targetDate = date,
            allPrayerDays = allDays
        )

        HomeViewState(
            selectedDate = date, currentTime = time, currentPrayerDay = prayerDay,
            currentPrayer = currentPrayer,
            nextPrayer = nextPrayer, 
            moonPhase = moon,
            effectiveHijriDate = effectiveHijri,
            isRefreshing = refreshing, isLoading = !hasSettled() && prayerDay == null,
            locationName = currentSettings.locationName, 
            isAdhanPlaying = playing, 
            playingPrayerName = prayerName,
            isMuted = isMuted,
            isHijriSelected = currentSettings.isHijriSelected,
            isNetworkAvailable = network,
            isLocationEnabled = locationEnabled,
            isLocationPermissionGranted = locPerm,
            hasSystemIssues = issues
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
        try { fetchPrayerData(forceFullRefresh = true) } finally { _isRefreshing.value = false; _hasSettled.value = true }
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
