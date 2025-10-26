package com.questnetshaper.app.data

import com.questnetshaper.app.model.ShapingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConfigStore {
    private val mutableConfig = MutableStateFlow(ShapingConfig())
    val config: StateFlow<ShapingConfig> = mutableConfig.asStateFlow()

    fun update(transform: (ShapingConfig) -> ShapingConfig) {
        mutableConfig.update(transform)
    }

    fun set(config: ShapingConfig) {
        mutableConfig.value = config
    }
}
