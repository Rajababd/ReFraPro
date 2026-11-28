/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.domain.model.Profile
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.profile_add_media
import com.dot.gallery.generated.resources.profile_all_photos
import com.dot.gallery.generated.resources.profile_album_selector_save
import com.dot.gallery.generated.resources.profile_cancel
import com.dot.gallery.generated.resources.profile_remove_media
import org.jetbrains.compose.resources.stringResource

enum class ProfileTargetAction { ADD, REMOVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTargetSheet(
    visible: Boolean,
    profiles: List<Profile>,
    action: ProfileTargetAction,
    initiallySelectedProfileIds: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    if (!visible) return
    var selectedIds by remember(visible, action, initiallySelectedProfileIds) {
        mutableStateOf(if (action == ProfileTargetAction.REMOVE) initiallySelectedProfileIds else emptySet())
    }
    val showAllPhotos = (Profile.ALL_PHOTOS_ID in initiallySelectedProfileIds) ==
        (action == ProfileTargetAction.REMOVE)
    val targetProfiles = profiles.filter { profile ->
        (profile.id in initiallySelectedProfileIds) == (action == ProfileTargetAction.REMOVE)
    }
    val allPhotosId = Profile.ALL_PHOTOS_ID
    val title = stringResource(
        if (action == ProfileTargetAction.ADD) Res.string.profile_add_media
        else Res.string.profile_remove_media
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (showAllPhotos) ProfileTargetRow(
                name = stringResource(Res.string.profile_all_photos),
                selected = allPhotosId in selectedIds,
                onToggle = {
                    selectedIds = if (allPhotosId in selectedIds) selectedIds - allPhotosId
                    else selectedIds + allPhotosId
                }
            )
            targetProfiles.forEach { profile ->
                ProfileTargetRow(
                    name = profile.name,
                    selected = profile.id in selectedIds,
                    onToggle = {
                        selectedIds = if (profile.id in selectedIds) selectedIds - profile.id
                        else selectedIds + profile.id
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.profile_cancel))
                }
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { onConfirm(selectedIds.toList()) }
                ) {
                    Text(stringResource(Res.string.profile_album_selector_save))
                }
            }
        }
    }
}

@Composable
private fun ProfileTargetRow(
    name: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}
