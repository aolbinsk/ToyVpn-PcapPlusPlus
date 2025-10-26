package com.questnetshaper.app.data

import com.questnetshaper.app.model.MetricsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MetricsStore {
    private val mutableSnapshot = MutableStateFlow(MetricsSnapshot())
    val snapshot: StateFlow<MetricsSnapshot> = mutableSnapshot.asStateFlow()

    fun update(snapshot: MetricsSnapshot) {
        mutableSnapshot.value = snapshot
    }
}
