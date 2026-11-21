/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Owns profile selection and CRUD. The default All Photos entry is virtual:
 * it is represented by Profile.ALL_PHOTOS_ID and is never written to Room.
 */
class ProfileViewModel @Inject constructor(
    context: Context,
    private val repository: MediaRepository
) : ViewModel() {
    private val dataStore = context.applicationContext.activeDataStore

    val profiles = repository.getProfiles().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val activeProfileId = dataStore.data
        .map { it[ACTIVE_PROFILE_ID] ?: Profile.ALL_PHOTOS_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile.ALL_PHOTOS_ID)

    val activeProfile = combine(profiles, activeProfileId) { items, activeId ->
        if (activeId == Profile.ALL_PHOTOS_ID) null else items.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            if (profileId == Profile.ALL_PHOTOS_ID || repository.getProfileAsync(profileId) != null) {
                dataStore.edit { it[ACTIVE_PROFILE_ID] = profileId }
            }
        }
    }

    fun createProfile(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            val id = repository.insertProfile(Profile(name = normalized))
            dataStore.edit { it[ACTIVE_PROFILE_ID] = id }
        }
    }

    fun renameProfile(profileId: Long, name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty() || profileId == Profile.ALL_PHOTOS_ID) return
        viewModelScope.launch {
            repository.getProfileAsync(profileId)?.let {
                repository.updateProfile(it.copy(name = normalized))
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        if (profileId == Profile.ALL_PHOTOS_ID) return
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            if (activeProfileId.value == profileId) {
                dataStore.edit { it[ACTIVE_PROFILE_ID] = Profile.ALL_PHOTOS_ID }
            }
        }
    }

    companion object {
        private val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
    }
}
