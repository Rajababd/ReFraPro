/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
 * Owns profile selection and CRUD. The built-in All Photos profile is virtual;
 * its exclusions are persisted separately because it has no Room row.
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

    val defaultProfileId = dataStore.data
        .map { it[DEFAULT_PROFILE_ID] ?: Profile.ALL_PHOTOS_ID }
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

    fun setDefaultProfile(profileId: Long) {
        viewModelScope.launch {
            if (profileId == Profile.ALL_PHOTOS_ID || repository.getProfileAsync(profileId) != null) {
                dataStore.edit { it[DEFAULT_PROFILE_ID] = profileId }
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

    fun addMediaListToProfile(profileId: Long, mediaIds: List<Long>) =
        addMediaListToProfiles(listOf(profileId), mediaIds)

    fun removeMediaListFromProfile(profileId: Long, mediaIds: List<Long>) =
        removeMediaListFromProfiles(listOf(profileId), mediaIds)

    fun addMediaListToProfiles(profileIds: List<Long>, mediaIds: List<Long>) {
        val ids = mediaIds.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            profileIds.distinct().forEach { profileId ->
                if (profileId == Profile.ALL_PHOTOS_ID) {
                    dataStore.edit { prefs ->
                        prefs[ALL_PHOTOS_EXCLUDED_MEDIA_IDS] =
                            prefs[ALL_PHOTOS_EXCLUDED_MEDIA_IDS].orEmpty() - ids.map(Long::toString).toSet()
                    }
                } else {
                    repository.addMediaListToProfile(profileId, ids)
                }
            }
        }
    }

    fun removeMediaListFromProfiles(profileIds: List<Long>, mediaIds: List<Long>) {
        val ids = mediaIds.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            profileIds.distinct().forEach { profileId ->
                if (profileId == Profile.ALL_PHOTOS_ID) {
                    dataStore.edit { prefs ->
                        prefs[ALL_PHOTOS_EXCLUDED_MEDIA_IDS] =
                            prefs[ALL_PHOTOS_EXCLUDED_MEDIA_IDS].orEmpty() + ids.map(Long::toString)
                    }
                } else {
                    ids.forEach { repository.removeMediaFromProfile(profileId, it) }
                }
            }
        }
    }

    fun addAlbumsToProfile(profileId: Long, albumIds: List<Long>) =
        addAlbumsToProfiles(listOf(profileId), albumIds)

    fun addAlbumsToProfiles(profileIds: List<Long>, albumIds: List<Long>) {
        val ids = albumIds.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            profileIds.distinct().forEach { profileId ->
                if (profileId == Profile.ALL_PHOTOS_ID) {
                    dataStore.edit { prefs ->
                        prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS] =
                            prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS].orEmpty() - ids.map(Long::toString).toSet()
                    }
                } else {
                    repository.addAlbumsToProfile(profileId, ids)
                }
            }
        }
    }

    fun removeAlbumsFromProfiles(profileIds: List<Long>, albumIds: List<Long>) {
        val ids = albumIds.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            profileIds.distinct().forEach { profileId ->
                if (profileId == Profile.ALL_PHOTOS_ID) {
                    dataStore.edit { prefs ->
                        prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS] =
                            prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS].orEmpty() + ids.map(Long::toString)
                    }
                } else {
                    ids.forEach { repository.removeAlbumFromProfile(profileId, it) }
                }
            }
        }
    }

    fun albumIdsInProfile(profileId: Long) = repository.getAlbumIdsInProfile(profileId)

    fun replaceAlbumsInProfile(profileId: Long, albumIds: List<Long>) {
        if (profileId == Profile.ALL_PHOTOS_ID) return
        viewModelScope.launch {
            repository.replaceAlbumsInProfile(profileId, albumIds.distinct())
        }
    }

    fun removeAlbumFromProfile(profileId: Long, albumId: Long) =
        removeAlbumsFromProfiles(listOf(profileId), listOf(albumId))

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
            dataStore.edit { prefs ->
                if (prefs[ACTIVE_PROFILE_ID] == profileId) {
                    prefs[ACTIVE_PROFILE_ID] = Profile.ALL_PHOTOS_ID
                }
                if (prefs[DEFAULT_PROFILE_ID] == profileId) {
                    prefs[DEFAULT_PROFILE_ID] = Profile.ALL_PHOTOS_ID
                }
            }
        }
    }

    companion object {
        private val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        private val DEFAULT_PROFILE_ID = longPreferencesKey("default_profile_id")
        private val ALL_PHOTOS_EXCLUDED_MEDIA_IDS =
            stringSetPreferencesKey("all_photos_excluded_media_ids")
        private val ALL_PHOTOS_EXCLUDED_ALBUM_IDS =
            stringSetPreferencesKey("all_photos_excluded_album_ids")
    }
}
