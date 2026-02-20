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
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.domain.manager.TimeManager
import com.ybugmobile.vaktiva.service.AdhanService
import com.ybugmobile.vaktiva.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Core ViewModel for the Home screen, responsible for orchestrating prayer data,
 * handling media playback controls for the Adhan, and managing screen state.
 *
 * It acts as a bridge between the [PrayerRepository], [SettingsManagerInterface],
 * and the UI layer, providing a unified [HomeViewState].
 *
 * Features:
 * - Real-time countdown to the next prayer.
 * - Adhan playback synchronization via [MediaController].
 * - Automated prayer time refreshing and alarm scheduling.
 * - System health monitoring (GPS, DND, Notification settings).
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

    /** Current user preferences and app settings stream. */
    val settings = settingsManager.settingsFlow

    /** The date currently being viewed on the home screen (defaults to today). */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    /** All cached prayer days observed from the database. */
    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Prayer data specifically for the [selectedDate]. */
    val currentPrayerDay: Flow<PrayerDay?> = combine(allPrayerDays, selectedDate) { days, date ->
        days.find { it.date == date }
    }

    /** Real-time clock stream for UI updates and countdowns. */
    val currentTime = timeManager.currentTime

    /** Loading state for pull-to-refresh or manual data fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isAdhanPlaying = MutableStateFlow(false)
    private val _playingPrayerName = MutableStateFlow<String?>(null)
    
    private val _isNetworkAvailable = MutableStateFlow(PermissionUtils.isNetworkAvailable(context))
    private val _hasSystemIssues = MutableStateFlow(checkSystemIssues())

    private var mediaController: MediaController? = null

    val calculationMethods = CALCULATION_METHODS
    private var lastPermissionStatus = isLocationPermissionGranted()

    private val todayPrayerDay: Flow<PrayerDay?> = allPrayerDays.map { days -> days.find { it.date == LocalDate.now() } }

    private val todayPrayerTimes = todayPrayerDay.map { day ->
        if (day == null) return@map null
        PrayerType.entries.map { it to (day.timings[it] ?: LocalTime.MIN) }
    }

    /** Observed moon phase for the currently selected date. */
    val moonPhase = selectedDate.map { date ->
        prayerRepository.getMoonPhase(date)
    }

    /** Information about the next upcoming prayer, including remaining duration. */
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

    /** Information about the prayer time that has most recently passed. */
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
            // Periodically check health every 30 seconds
            if (now.second % 30 == 0) {
                _isNetworkAvailable.value = PermissionUtils.isNetworkAvailable(context)
                _hasSystemIssues.value = checkSystemIssues()
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

    private fun checkSystemIssues(): Boolean {
        return !PermissionUtils.isLocationEnabled(context) || 
               PermissionUtils.isDoNotDisturbActive(context) || 
               PermissionUtils.areNotificationChannelsMuted(context)
    }

    /**
     * Schedules a test Adhan alert to trigger after a specific delay.
     * @param seconds Delay in seconds before the test Adhan sounds.
     */
    fun triggerTestAlarm(seconds: Int) {
        val endTimeMillis = System.currentTimeMillis() + (seconds * 1000)
        viewModelScope.launch {
            alarmScheduler.cancelAllAlarms()
            settingsManager.clearMutedPrayer()
            settingsManager.setTestAlarmEndTime(endTimeMillis)
            alarmScheduler.scheduleTestAlarm(seconds)
        }
    }

    /** For debugging: shifts the internal app clock. */
    fun debugAddHours(minutes: Long) {
        timeManager.addMinutes(minutes)
    }

    override fun onResume(owner: LifecycleOwner) {
        val status = isLocationPermissionGranted()
        if (status && !lastPermissionStatus) refresh()
        lastPermissionStatus = status
        
        _isNetworkAvailable.value = PermissionUtils.isNetworkAvailable(context)
        _hasSystemIssues.value = checkSystemIssues()
    }

    private fun isLocationPermissionGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Initializes the [MediaController] to communicate with the [AdhanService]. */
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

    /** Stops any currently playing Adhan audio. */
    fun stopAdhan() {
        mediaController?.stop()
        _isAdhanPlaying.value = false
        _playingPrayerName.value = null
    }

    private val _hasSettled = MutableStateFlow(false)

    /**
     * The primary state observable for the Home Screen.
     * Combines multiple data streams into a single [HomeViewState].
     */
    val state: StateFlow<HomeViewState> = combine(
        combine(selectedDate, currentTime, currentPrayerDay, moonPhase, ::StateQuad),
        combine(nextPrayerInfo, currentPrayerInfo, isRefreshing, ::Triple),
        combine(settings, _isAdhanPlaying, _playingPrayerName, ::Triple),
        combine(_isNetworkAvailable, _hasSystemIssues, { a, b -> a to b }),
        allPrayerDays
    ) { (date, time, prayerDay, moon), (nextPrayer, currentPrayer, refreshing), (currentSettings, playing, prayerName), (network, issues), allDays ->
        
        val isMuted = currentSettings.mutedPrayerName.equals(nextPrayer?.type?.name, ignoreCase = true) &&
                      currentSettings.mutedPrayerDate == nextPrayer?.date?.toString()

        // Calculate Effective Hijri Date based on Sunset Rollover (Maghrib)
        val effectiveHijri = if (date == LocalDate.now() && prayerDay != null) {
            val maghribTime = prayerDay.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)
            if (time.toLocalTime().isAfter(maghribTime) || time.toLocalTime() == maghribTime) {
                // Rollover to next day's Hijri date
                allDays.find { it.date == date.plusDays(1) }?.hijriDate ?: prayerDay.hijriDate
            } else {
                prayerDay.hijriDate
            }
        } else {
            prayerDay?.hijriDate
        }

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
            hasSystemIssues = issues
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewState(isLoading = true))

    private fun hasSettled() = _hasSettled.value

    /** Cancels a running test alarm and restores normal scheduling. */
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

    /** Changes the date for which prayer times are displayed. */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (allPrayerDays.value.none { it.date == date }) viewModelScope.launch {
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

    /** Forcefully refreshes prayer data for the current year. */
    fun refresh() = viewModelScope.launch {
        _isRefreshing.value = true
        try { fetchPrayerData() } finally { _isRefreshing.value = false; _hasSettled.value = true }
    }

    /** Updates the prayer calculation method and triggers a data refresh. */
    fun updateCalculationMethod(methodId: Int) = viewModelScope.launch { settingsManager.updateCalculationMethod(methodId); refresh() }

    /** Toggles the 'muted' state for a specific upcoming prayer. */
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

    /** Toggles the UI display between Hijri and Gregorian calendars. */
    fun toggleCalendarType(isHijri: Boolean) = viewModelScope.launch {
        settingsManager.updateCalendarType(isHijri)
    }

    /** Entry point for permissions completion to initiate first-time setup. */
    fun onPermissionsGranted() = viewModelScope.launch { try { fetchPrayerData() } finally { _hasSettled.value = true } }

    /**
     * Orchestrates the fetching of prayer times.
     * 1. Detects current location.
     * 2. Resolves human-readable address.
     * 3. Fetches prayer times for all 12 months of the current year.
     */
    private suspend fun fetchPrayerData() {
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
        
        // Fetch data for the whole current year to ensure proactive caching
        for (month in 1..12) {
            prayerRepository.refreshPrayerTimes(now.year, month, lat, lng, s.calculationMethod)
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

/** Utility state container for combining four flows. */
data class StateQuad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
