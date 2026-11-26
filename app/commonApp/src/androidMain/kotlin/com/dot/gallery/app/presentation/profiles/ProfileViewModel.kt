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
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Profile
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import kotlin.uuid.Uuid
import com.dot.gallery.domain.util.MediaOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val dataStoreContext = context.applicationContext
    private val dataStore = dataStoreContext.activeDataStore

    val profiles = repository.getProfiles().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    // This is deliberately sourced from the repository, not MediaDistributor.albumsFlow:
    // albumsFlow is filtered by the currently active profile and would hide albums that
    // the user is trying to add back into a profile.
    val deviceAlbums = repository.getAlbums(MediaOrder.Default)
        .map { it.data.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rawActiveProfileId = dataStore.data
        .onStart {
            val defaultId = dataStore.data.first()[DEFAULT_PROFILE_ID]
            if (defaultId != null) {
                dataStore.edit { it[ACTIVE_PROFILE_ID] = defaultId }
            }
        }
        .map { it[ACTIVE_PROFILE_ID] ?: Profile.ALL_PHOTOS_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile.ALL_PHOTOS_ID)

    val lockedProfileIds = dataStore.data
        .map { prefs -> prefs[LOCKED_PROFILE_IDS].orEmpty().mapNotNull(String::toLongOrNull).toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val biometricProfileIds = dataStore.data
        .map { it[BIOMETRIC_PROFILE_IDS].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val activeProfileId = combine(rawActiveProfileId, lockedProfileIds, ProfileAccessSession.unlockedProfileIds) { activeId, locked, unlocked ->
        if (activeId in locked && activeId !in unlocked) ProfileAccessSession.LOCKED_PROFILE_ID else activeId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileAccessSession.LOCKED_PROFILE_ID)

    val defaultProfileId = dataStore.data
        .map { it[DEFAULT_PROFILE_ID] ?: Profile.ALL_PHOTOS_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile.ALL_PHOTOS_ID)

    val activeProfile = combine(profiles, activeProfileId) { items, activeId ->
        if (activeId == Profile.ALL_PHOTOS_ID) null else items.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            if (profileId == Profile.ALL_PHOTOS_ID || repository.getProfileAsync(profileId) != null) {
                val previousId = rawActiveProfileId.value
                if (previousId != profileId) ProfileAccessSession.lock(previousId)
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
            val previousId = rawActiveProfileId.value
            val id = repository.insertProfile(Profile(name = normalized))
            if (previousId != id) ProfileAccessSession.lock(previousId)
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

    fun albumIdsInProfile(profileId: Long): Flow<List<Long>> =
        if (profileId == Profile.ALL_PHOTOS_ID) {
            combine(deviceAlbums, dataStore.data) { albums, prefs ->
                val excluded = prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS].orEmpty()
                albums.asSequence().map { it.id }
                    .filterNot { it.toString() in excluded }
                    .toList()
            }
        } else {
            repository.getAlbumIdsInProfile(profileId)
        }

    /** Albums visible inside a profile; All Photos is computed from its persisted exclusions. */
    fun albumsInProfile(profileId: Long): Flow<List<Album>> =
        if (profileId == Profile.ALL_PHOTOS_ID) {
            combine(deviceAlbums, dataStore.data) { albums, prefs ->
                val excluded = prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS].orEmpty()
                albums.filterNot { it.id.toString() in excluded }
            }
        } else {
            combine(deviceAlbums, repository.getAlbumIdsInProfile(profileId)) { albums, ids ->
                val included = ids.toHashSet()
                albums.filter { it.id in included }
            }
        }

    /** Profiles that already contain at least one of the selected albums. */
    suspend fun profilesContainingAlbums(albumIds: List<Long>): Set<Long> {
        val ids = albumIds.distinct().toSet()
        if (ids.isEmpty()) return emptySet()
        val result = mutableSetOf<Long>()
        val excluded = dataStore.data.first()[ALL_PHOTOS_EXCLUDED_ALBUM_IDS].orEmpty()
        val allPhotosIds = deviceAlbums.value.asSequence().map { it.id }
            .filterNot { it.toString() in excluded }
            .toSet()
        if (ids.any { it in allPhotosIds }) result += Profile.ALL_PHOTOS_ID
        profiles.value.forEach { profile ->
            val included = repository.getAlbumIdsInProfile(profile.id).first().toHashSet()
            if (ids.any { it in included }) result += profile.id
        }
        return result
    }

    /**
     * Updates only the albums shown by the active profile's selector.
     * Membership outside [sourceAlbumIds] is preserved in the target profile.
     */
    fun replaceAlbumsInProfileWithinSource(
        profileId: Long,
        sourceAlbumIds: List<Long>,
        selectedAlbumIds: List<Long>,
    ) {
        val source = sourceAlbumIds.distinct().toSet()
        val selected = selectedAlbumIds.distinct().toSet().intersect(source)
        if (source.isEmpty()) return
        viewModelScope.launch {
            if (profileId == Profile.ALL_PHOTOS_ID) {
                dataStore.edit { prefs ->
                    val oldExcluded = prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS].orEmpty()
                    val preservedOutsideSource = oldExcluded.filterNot { raw ->
                        raw.toLongOrNull() in source
                    }.toSet()
                    val excludedWithinSource = (source - selected).map(Long::toString).toSet()
                    prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS] = preservedOutsideSource + excludedWithinSource
                }
            } else {
                val current = repository.getAlbumIdsInProfile(profileId).first().toSet()
                (current intersect source - selected).forEach { albumId ->
                    repository.removeAlbumFromProfile(profileId, albumId)
                }
                (selected - current).forEach { albumId ->
                    repository.addAlbumsToProfile(profileId, listOf(albumId))
                }
            }
        }
    }

    fun replaceAlbumsInProfile(profileId: Long, albumIds: List<Long>) {
        val selected = albumIds.distinct().toSet()
        viewModelScope.launch {
            if (profileId == Profile.ALL_PHOTOS_ID) {
                val allDeviceIds = deviceAlbums.value.map { it.id }.toSet()
                dataStore.edit { prefs ->
                    prefs[ALL_PHOTOS_EXCLUDED_ALBUM_IDS] =
                        (allDeviceIds - selected).map(Long::toString).toSet()
                }
            } else {
                repository.replaceAlbumsInProfile(profileId, selected.toList())
            }
        }
    }

    fun removeAlbumFromProfile(profileId: Long, albumId: Long) =
        removeAlbumsFromProfiles(listOf(profileId), listOf(albumId))

    fun isProfileLocked(profileId: Long): Boolean = profileId in lockedProfileIds.value

    fun isProfileBiometricEnabled(profileId: Long): Boolean =
        profileId.toString() in biometricProfileIds.value

    suspend fun setProfilePassword(profileId: Long, password: String) {
        if (profileId < 0L || password.length < 4) return
        val wasActive = rawActiveProfileId.value == profileId
        VaultPasswordManager.setPassword(
            dataStoreContext,
            profileCredentialUuid(profileId),
            password,
            VaultAuthType.PASSWORD
        )
        dataStore.edit { prefs ->
            prefs[LOCKED_PROFILE_IDS] = prefs[LOCKED_PROFILE_IDS].orEmpty() + profileId.toString()
        }
        ProfileAccessSession.lock(profileId)
        if (wasActive) {
            // Keep the selected id persisted; the distributor masks it until authenticated.
            dataStore.edit { it[ACTIVE_PROFILE_ID] = profileId }
        }
    }

    suspend fun verifyProfilePassword(profileId: Long, password: String): VerifyResult =
        VaultPasswordManager.verifyPassword(dataStoreContext, profileCredentialUuid(profileId), password)

    fun unlockProfileSession(profileId: Long) {
        ProfileAccessSession.unlock(profileId)
    }

    fun setProfileBiometricEnabled(profileId: Long, enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val ids = prefs[BIOMETRIC_PROFILE_IDS].orEmpty()
                prefs[BIOMETRIC_PROFILE_IDS] = if (enabled) ids + profileId.toString() else ids - profileId.toString()
            }
        }
    }

    fun removeProfileLock(profileId: Long) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LOCKED_PROFILE_IDS] = prefs[LOCKED_PROFILE_IDS].orEmpty() - profileId.toString()
                prefs[BIOMETRIC_PROFILE_IDS] = prefs[BIOMETRIC_PROFILE_IDS].orEmpty() - profileId.toString()
            }
            ProfileAccessSession.unlock(profileId)
            VaultPasswordManager.clearVaultSecrets(dataStoreContext, profileCredentialUuid(profileId))
        }
    }

    private fun profileCredentialUuid(profileId: Long): Uuid {
        val suffix = profileId.toString(16).takeLast(12).padStart(12, '0')
        return Uuid.parse("00000001-0000-0000-0000-$suffix")
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
            dataStore.edit { prefs ->
                if (prefs[ACTIVE_PROFILE_ID] == profileId) {
                    prefs[ACTIVE_PROFILE_ID] = Profile.ALL_PHOTOS_ID
                }
                if (prefs[DEFAULT_PROFILE_ID] == profileId) {
                    prefs[DEFAULT_PROFILE_ID] = Profile.ALL_PHOTOS_ID
                }
                prefs[LOCKED_PROFILE_IDS] = prefs[LOCKED_PROFILE_IDS].orEmpty() - profileId.toString()
                prefs[BIOMETRIC_PROFILE_IDS] = prefs[BIOMETRIC_PROFILE_IDS].orEmpty() - profileId.toString()
            }
            ProfileAccessSession.lock(profileId)
            VaultPasswordManager.clearVaultSecrets(dataStoreContext, profileCredentialUuid(profileId))
        }
    }

    companion object {
        private val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        private val LOCKED_PROFILE_IDS = stringSetPreferencesKey("locked_profile_ids")
        private val BIOMETRIC_PROFILE_IDS = stringSetPreferencesKey("biometric_profile_ids")
        private val DEFAULT_PROFILE_ID = longPreferencesKey("default_profile_id")
        private val ALL_PHOTOS_EXCLUDED_MEDIA_IDS =
            stringSetPreferencesKey("all_photos_excluded_media_ids")
        private val ALL_PHOTOS_EXCLUDED_ALBUM_IDS =
            stringSetPreferencesKey("all_photos_excluded_album_ids")
    }
}
