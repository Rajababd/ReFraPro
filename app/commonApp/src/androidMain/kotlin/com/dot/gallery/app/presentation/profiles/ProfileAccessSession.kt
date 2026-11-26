package com.dot.gallery.app.presentation.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-only unlock state. No authentication result is persisted across process restarts.
 */
object ProfileAccessSession {
    const val LOCKED_PROFILE_ID: Long = -1L
    private val _unlockedProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedProfileIds = _unlockedProfileIds.asStateFlow()

    fun unlock(profileId: Long) {
        _unlockedProfileIds.value = _unlockedProfileIds.value + profileId
    }

    fun lock(profileId: Long) {
        _unlockedProfileIds.value = _unlockedProfileIds.value - profileId
    }
}
