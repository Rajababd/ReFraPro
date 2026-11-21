/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.dot.gallery.domain.model.Profile
import com.dot.gallery.domain.model.ProfileAlbum
import com.dot.gallery.domain.model.ProfileExcludedMedia
import com.dot.gallery.domain.model.ProfileMedia
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    fun getProfile(profileId: Long): Flow<Profile?>

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileAsync(profileId: Long): Profile?

    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    fun getProfiles(): Flow<List<Profile>>

    @Query("SELECT COUNT(*) FROM profiles")
    fun getProfileCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMedia(profileMedia: ProfileMedia)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaList(profileMedia: List<ProfileMedia>)

    @Query("DELETE FROM profile_media WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun removeMedia(profileId: Long, mediaId: Long)

    @Query("DELETE FROM profile_media WHERE profileId = :profileId")
    suspend fun removeAllMedia(profileId: Long)

    @Query("SELECT mediaId FROM profile_media WHERE profileId = :profileId ORDER BY sortOrder ASC, addedAt ASC")
    fun getDirectMediaIds(profileId: Long): Flow<List<Long>>

    @Query("SELECT mediaId FROM profile_media WHERE profileId = :profileId ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getDirectMediaIdsAsync(profileId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAlbum(profileAlbum: ProfileAlbum)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAlbums(profileAlbums: List<ProfileAlbum>)

    @Query("DELETE FROM profile_albums WHERE profileId = :profileId AND albumId = :albumId")
    suspend fun removeAlbum(profileId: Long, albumId: Long)

    @Query("DELETE FROM profile_albums WHERE profileId = :profileId")
    suspend fun removeAllAlbums(profileId: Long)

    @Query("SELECT albumId FROM profile_albums WHERE profileId = :profileId ORDER BY addedAt ASC")
    fun getAlbumIds(profileId: Long): Flow<List<Long>>

    @Query("SELECT albumId FROM profile_albums WHERE profileId = :profileId ORDER BY addedAt ASC")
    suspend fun getAlbumIdsAsync(profileId: Long): List<Long>

    @Transaction
    suspend fun replaceAlbums(profileId: Long, albumIds: List<Long>) {
        removeAllAlbums(profileId)
        if (albumIds.isNotEmpty()) {
            addAlbums(albumIds.distinct().map { ProfileAlbum(profileId, it) })
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun excludeMedia(excluded: ProfileExcludedMedia)

    @Query("DELETE FROM profile_excluded_media WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun clearExcludedMedia(profileId: Long, mediaId: Long)

    @Query("SELECT mediaId FROM profile_excluded_media WHERE profileId = :profileId")
    fun getExcludedMediaIds(profileId: Long): Flow<List<Long>>

    @Query("SELECT mediaId FROM profile_excluded_media WHERE profileId = :profileId")
    suspend fun getExcludedMediaIdsAsync(profileId: Long): List<Long>

    @Query(PROFILE_MEMBER_IDS)
    fun getProfileMediaIds(profileId: Long): Flow<List<Long>>

    @Query(PROFILE_MEMBER_COUNT)
    fun getProfileMediaCount(profileId: Long): Flow<Int>
}

private const val PROFILE_MEMBER_IDS = """
    SELECT members.id FROM (
        SELECT pm.mediaId AS id FROM profile_media pm WHERE pm.profileId = :profileId
        UNION
        SELECT m.id AS id FROM media m
        INNER JOIN profile_albums pa
            ON pa.profileId = :profileId AND m.albumID = pa.albumId
    ) AS members
    WHERE members.id NOT IN (
        SELECT e.mediaId FROM profile_excluded_media e WHERE e.profileId = :profileId
    )
"""

private const val PROFILE_MEMBER_COUNT = """
    SELECT COUNT(*) FROM (
        $PROFILE_MEMBER_IDS
    )
"""
