/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.domain.model.Profile
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.profile_add
import com.dot.gallery.generated.resources.profile_all_photos
import com.dot.gallery.generated.resources.profile_cancel
import com.dot.gallery.generated.resources.profile_create
import com.dot.gallery.generated.resources.profile_create_title
import com.dot.gallery.generated.resources.profile_name_hint
import com.dot.gallery.injection.injectViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDrawerContent(onProfileSelected: () -> Unit) {
    val viewModel = injectViewModel<ProfileViewModel> { profileViewModel }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newProfileName by rememberSaveable { mutableStateOf("") }

    ModalDrawerSheet {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp)) {
            Text(
                text = "ReFraPro",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.profile_all_photos)) },
                selected = activeProfileId == Profile.ALL_PHOTOS_ID,
                onClick = {
                    viewModel.selectProfile(Profile.ALL_PHOTOS_ID)
                    onProfileSelected()
                },
                icon = { Icon(Icons.Outlined.Photo, contentDescription = null) }
            )
            profiles.forEach { profile ->
                NavigationDrawerItem(
                    label = { Text(profile.name) },
                    selected = activeProfileId == profile.id,
                    onClick = {
                        viewModel.selectProfile(profile.id)
                        onProfileSelected()
                    },
                    icon = { Icon(Icons.Outlined.Photo, contentDescription = null) }
                )
            }
            Spacer(Modifier.height(12.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.profile_add)) },
                selected = false,
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) }
            )
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newProfileName = ""
            },
            title = { Text(stringResource(Res.string.profile_create_title)) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text(stringResource(Res.string.profile_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newProfileName.isNotBlank(),
                    onClick = {
                        viewModel.createProfile(newProfileName)
                        newProfileName = ""
                        showCreateDialog = false
                        onProfileSelected()
                    }
                ) { Text(stringResource(Res.string.profile_create)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newProfileName = ""
                    }
                ) { Text(stringResource(Res.string.profile_cancel)) }
            }
        )
    }
}
