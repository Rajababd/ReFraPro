/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.domain.model

import androidx.compose.runtime.Immutable
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "profile_media",
    primaryKeys = ["profileId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"]), Index(value = ["mediaId"])]
)
@Immutable
@Serializable
data class ProfileMedia(
    val profileId: Long,
    val mediaId: Long,
    val sortOrder: Int = 0,
    val addedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
)
