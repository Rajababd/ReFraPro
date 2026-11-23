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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.domain.model.Album
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.profile_album_selector_title
import com.dot.gallery.generated.resources.profile_album_selector_count
import com.dot.gallery.generated.resources.profile_cancel
import com.dot.gallery.generated.resources.profile_album_selector_save
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileAlbumSelectorSheet(
    profileName: String,
    albums: List<Album>,
    initiallySelectedAlbumIds: List<Long>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    val selectedIds = remember(profileName) { mutableStateListOf<Long>() }
    LaunchedEffect(profileName, initiallySelectedAlbumIds) {
        selectedIds.clear()
        selectedIds.addAll(initiallySelectedAlbumIds.distinct())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.profile_album_selector_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = profileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    val checked = album.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selectedIds.remove(album.id)
                                else selectedIds.add(album.id)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(Res.string.profile_album_selector_count, album.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked && album.id !in selectedIds) selectedIds.add(album.id)
                                if (!isChecked) selectedIds.remove(album.id)
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.profile_cancel))
                }
                Button(
                    onClick = { onSave(selectedIds.toList()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.profile_album_selector_save))
                }
            }
        }
    }
}
