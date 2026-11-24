/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.Profile

data class SettingsTimelineAlbumsState(
    /** `SdkCompat.supportsFavorites` — gates the favorite-icon + search-bar-fav rows. */
    val supportsFavorites: Boolean = true,
    /** `AlbumsViewModel.sectionsFlow` — rendered by the album-sections detail pane. */
    val albumSections: List<AlbumSection> = emptyList(),
    /** sectionId -> album count, derived from `LocalMediaDistributor.albumsFlow`. */
    val albumCountBySection: Map<Long, Int> = emptyMap(),
    val profiles: List<Profile> = emptyList(),
    val defaultProfileId: Long = Profile.ALL_PHOTOS_ID,
)
