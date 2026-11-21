/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.domain.model

import androidx.compose.runtime.Immutable
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A user-defined media profile. The built-in "All Photos" profile is synthetic
 * (id 0) and is deliberately not persisted in this table.
 */
@Entity(tableName = "profiles")
@Immutable
@Serializable
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        const val ALL_PHOTOS_ID: Long = 0L
    }
}
