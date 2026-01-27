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
) : ViewModel(), DefaultLifecycleObserver {

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

    private val _testAlarmTime = MutableStateFlow<LocalDateTime?>(null)

    private var mediaController: MediaController? = null

    val calculationMethods = CALCULATION_METHODS
    private var lastPermissionStatus = isLocationPermissionGranted()

    init {
        tickerFlow(1000).onEach { _currentTime.value = LocalDateTime.now() }.launchIn(viewModelScope)
        onPermissionsGranted()
        
        allPrayerDays
            .filter { it.isNotEmpty() }
            .onEach { days -> alarmScheduler.scheduleNextAlarm(days) }
            .launchIn(viewModelScope)

        setupMediaController()
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
                        _playingPrayerName.value = if (isPlaying) mediaController?.currentMediaItem?.mediaMetadata?.title?.toString() else null
                    }
                })
                _isAdhanPlaying.value = mediaController?.isPlaying == true
                _playingPrayerName.value = mediaController?.currentMediaItem?.mediaMetadata?.title?.toString()
            } catch (e: Exception) { e.printStackTrace() }
        }, MoreExecutors.directExecutor())
    }

    fun stopAdhan() = mediaController?.stop()

    fun setTestAlarm(time: LocalDateTime) {
        _testAlarmTime.value = time
        // Actually schedule a real system alarm for testing
        viewModelScope.launch {
            val dummyDay = PrayerDay(
                date = time.toLocalDate(),
                hijriDate = null,
                timings = mapOf(PrayerType.FAJR to time.toLocalTime())
            )
            alarmScheduler.scheduleNextAlarm(listOf(dummyDay))
        }
    }

    private fun tickerFlow(periodMillis: Long) = flow { while (true) { emit(Unit); delay(periodMillis) } }

    private val todayPrayerDay: Flow<PrayerDay?> = allPrayerDays.map { days -> days.find { it.date == LocalDate.now() } }

    private val todayPrayerTimes = todayPrayerDay.map { day ->
        if (day == null) return@map null
        PrayerType.entries.map { it to (day.timings[it] ?: LocalTime.MIN) }
    }

    val nextPrayerInfo: Flow<NextPrayer?> = combine(todayPrayerTimes, currentTime, _testAlarmTime) { prayers, now, testTime ->
        if (prayers == null) return@combine null
        val nowDateTime = now
        val nowTime = now.toLocalTime()
        val nextReal = prayers.firstOrNull { it.second.isAfter(nowTime) } ?: prayers.first()
        val realDateTime = if (nextReal.second.isAfter(nowTime)) nowDateTime.toLocalDate().atTime(nextReal.second) else nowDateTime.toLocalDate().plusDays(1).atTime(nextReal.second)

        if (testTime != null && testTime.isAfter(nowDateTime)) {
            // Keep the test countdown active until it hits zero
            return@combine NextPrayer(PrayerType.FAJR, testTime.toLocalTime(), testTime.toLocalDate(), Duration.between(nowDateTime, testTime))
        }
        NextPrayer(nextReal.first, nextReal.second, realDateTime.toLocalDate(), Duration.between(nowDateTime, realDateTime))
    }

    private val _hasSettled = MutableStateFlow(false)

    val state: StateFlow<HomeViewState> = combine(
        combine(selectedDate, currentTime, currentPrayerDay, ::Triple),
        combine(nextPrayerInfo, isRefreshing, settings, ::Triple),
        combine(_isAdhanPlaying, _playingPrayerName, _hasSettled, ::Triple)
    ) { (date, time, prayerDay), (nextPrayer, refreshing, currentSettings), (playing, prayerName, settled) ->
        HomeViewState(
            selectedDate = date, currentTime = time, currentPrayerDay = prayerDay,
            nextPrayer = nextPrayer, isRefreshing = refreshing, isLoading = !settled && prayerDay == null,
            locationName = currentSettings.locationName, isAdhanPlaying = playing, playingPrayerName = prayerName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewState(isLoading = true))

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (allPrayerDays.value.none { it.date == date }) viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val s = settings.first()
                val loc = locationWrapper.getCurrentLocation()
                prayerRepository.refreshPrayerTimes(date.year, date.monthValue, loc?.latitude ?: s.latitude, loc?.longitude ?: s.longitude, s.calculationMethod)
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
        val dateStr = prayerDate.toString()
        if (s.mutedPrayerName == prayerName && s.mutedPrayerDate == dateStr) settingsManager.clearMutedPrayer()
        else settingsManager.muteNextPrayer(prayerName, dateStr)
    }

    fun onPermissionsGranted() = viewModelScope.launch { try { fetchPrayerData() } finally { _hasSettled.value = true } }

    private suspend fun fetchPrayerData() {
        val loc = locationWrapper.getCurrentLocation()
        val s = settings.first()
        val now = LocalDate.now()
        val lat = loc?.latitude ?: s.latitude
        val lng = loc?.longitude ?: s.longitude
        if (loc != null) settingsManager.saveLocation(lat, lng, locationWrapper.getAddressFromLocation(lat, lng) ?: "Current Location")
        prayerRepository.refreshPrayerTimes(now.year, now.monthValue, lat, lng, s.calculationMethod)
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
