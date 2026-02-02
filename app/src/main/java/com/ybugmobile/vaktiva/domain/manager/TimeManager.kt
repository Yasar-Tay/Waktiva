package com.ybugmobile.vaktiva.domain.manager

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _timeOffset = MutableStateFlow(Duration.ZERO)
    
    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    init {
        flow {
            while (true) {
                emit(Unit)
                delay(1000)
            }
        }.onEach {
            _currentTime.value = LocalDateTime.now().plus(_timeOffset.value)
        }.launchIn(scope)
    }

    fun addMinutes(minutes: Long) {
        _timeOffset.value = _timeOffset.value.plusMinutes(minutes)
        _currentTime.value = LocalDateTime.now().plus(_timeOffset.value)
    }
}
