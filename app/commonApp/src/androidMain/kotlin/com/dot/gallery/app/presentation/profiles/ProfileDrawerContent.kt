/*
 * SPDX-FileCopyrightText: 2026 ReFraPro contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.profiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.domain.model.Profile
import com.dot.gallery.feature.albums.ui.collections.isSelectableCollectionAlbumId
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import com.dot.gallery.generated.resources.profile_lock
import com.dot.gallery.generated.resources.profile_unlock
import com.dot.gallery.generated.resources.profile_password
import com.dot.gallery.generated.resources.profile_password_confirm
import com.dot.gallery.generated.resources.profile_password_mismatch
import com.dot.gallery.generated.resources.profile_password_invalid
import com.dot.gallery.generated.resources.profile_biometric
import com.dot.gallery.generated.resources.profile_remove_lock
import com.dot.gallery.generated.resources.profile_use_biometric
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.profile_add
import com.dot.gallery.generated.resources.profile_add_album
import com.dot.gallery.generated.resources.profile_all_photos
import com.dot.gallery.generated.resources.profile_cancel
import com.dot.gallery.generated.resources.profile_create
import com.dot.gallery.generated.resources.profile_create_title
import com.dot.gallery.generated.resources.profile_name_hint
import com.dot.gallery.generated.resources.profile_settings_title
import com.dot.gallery.generated.resources.profile_rename
import com.dot.gallery.injection.injectViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileDrawerContent(onProfileSelected: () -> Unit) {
    val viewModel = injectViewModel<ProfileViewModel> { profileViewModel }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val lockedProfileIds by viewModel.lockedProfileIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val activeAlbumsFlow = remember(activeProfileId) { viewModel.albumsInProfile(activeProfileId) }
    val activeAlbums by activeAlbumsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var albumTargetProfile by remember { mutableStateOf<Profile?>(null) }
    var profileSettingsTarget by remember { mutableStateOf<Profile?>(null) }
    var editedProfileName by rememberSaveable { mutableStateOf("") }
    var passwordTarget by remember { mutableStateOf<Profile?>(null) }
    var unlockTarget by remember { mutableStateOf<Profile?>(null) }
    var profilePassword by rememberSaveable { mutableStateOf("") }
    var confirmProfilePassword by rememberSaveable { mutableStateOf("") }
    var unlockPassword by rememberSaveable { mutableStateOf("") }
    var passwordError by rememberSaveable { mutableStateOf("") }
    var useBiometric by rememberSaveable { mutableStateOf(false) }
    var openSettingsAfterUnlock by rememberSaveable { mutableStateOf(false) }
    val invalidPasswordText = stringResource(Res.string.profile_password_invalid)
    val passwordMismatchText = stringResource(Res.string.profile_password_mismatch)
    val biometricState = rememberBiometricState(
        title = stringResource(Res.string.profile_unlock),
        subtitle = stringResource(Res.string.profile_use_biometric),
        onSuccess = {
            unlockTarget?.let { target ->
                viewModel.unlockProfileSession(target.id)
                viewModel.selectProfile(target.id)
                unlockTarget = null
                unlockPassword = ""
                passwordError = ""
                if (openSettingsAfterUnlock) {
                    profileSettingsTarget = target
                    editedProfileName = target.name
                    openSettingsAfterUnlock = false
                } else {
                    onProfileSelected()
                }
            }
        },
        onFailed = { passwordError = invalidPasswordText }
    )
    val allPhotosName = stringResource(Res.string.profile_all_photos)
    val allPhotosProfile = remember(allPhotosName) {
        Profile(id = Profile.ALL_PHOTOS_ID, name = allPhotosName)
    }

    val targetAlbumIdsFlow = remember(albumTargetProfile?.id) {
        albumTargetProfile?.let { target ->
            viewModel.albumIdsInProfile(target.id).map { ids -> ids to true }
        } ?: flowOf(emptyList<Long>() to false)
    }
    val targetAlbumSelection by key(albumTargetProfile?.id) {
        targetAlbumIdsFlow.collectAsStateWithLifecycle(
            initialValue = emptyList<Long>() to false
        )
    }
    val selectedAlbumIds = targetAlbumSelection.first

    ModalDrawerSheet {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp)) {
            Text(
                text = "ReFraPro",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationDrawerItem(
                    modifier = Modifier.weight(1f).combinedClickable(
                        onClick = {
                            if (Profile.ALL_PHOTOS_ID in lockedProfileIds) {
                                openSettingsAfterUnlock = false
                                unlockTarget = allPhotosProfile
                                unlockPassword = ""
                                passwordError = ""
                            } else {
                                viewModel.selectProfile(Profile.ALL_PHOTOS_ID)
                                onProfileSelected()
                            }
                        },
                        onLongClick = {
                            if (Profile.ALL_PHOTOS_ID in lockedProfileIds) {
                                openSettingsAfterUnlock = true
                                unlockTarget = allPhotosProfile
                                unlockPassword = ""
                                passwordError = ""
                            } else {
                                profileSettingsTarget = allPhotosProfile
                                editedProfileName = allPhotosName
                            }
                        }
                    ),
                    label = { Text(allPhotosName) },
                    selected = activeProfileId == Profile.ALL_PHOTOS_ID,
                    onClick = {},
                    icon = { Icon(if (Profile.ALL_PHOTOS_ID in lockedProfileIds) Icons.Outlined.Lock else Icons.Outlined.Photo, contentDescription = null) }
                )
                IconButton(onClick = { albumTargetProfile = allPhotosProfile }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(Res.string.profile_add_album),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            profiles.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavigationDrawerItem(
                        modifier = Modifier.weight(1f).combinedClickable(
                            onClick = {
                                if (profile.id in lockedProfileIds) {
                                    openSettingsAfterUnlock = false
                                    unlockTarget = profile
                                    unlockPassword = ""
                                    passwordError = ""
                                } else {
                                    viewModel.selectProfile(profile.id)
                                    onProfileSelected()
                                }
                            },
                            onLongClick = {
                                if (profile.id in lockedProfileIds) {
                                    openSettingsAfterUnlock = true
                                    unlockTarget = profile
                                    unlockPassword = ""
                                    passwordError = ""
                                } else {
                                    profileSettingsTarget = profile
                                    editedProfileName = profile.name
                                }
                            }
                        ),
                        label = { Text(profile.name) },
                        selected = activeProfileId == profile.id,
                        onClick = {},
                        icon = { Icon(if (profile.id in lockedProfileIds) Icons.Outlined.Lock else Icons.Outlined.Photo, contentDescription = null) }
                    )
                    IconButton(onClick = { albumTargetProfile = profile }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(Res.string.profile_add_album),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

    albumTargetProfile?.let { target ->
        ProfileAlbumSelectorSheet(
            profileId = target.id,
            profileName = target.name,
            albums = activeAlbums.filter { isSelectableCollectionAlbumId(it.id) },
            initiallySelectedAlbumIds = selectedAlbumIds,
            onDismiss = { albumTargetProfile = null },
            onSave = { albumIds ->
                viewModel.replaceAlbumsInProfileWithinSource(target.id, activeAlbums.map { it.id }, albumIds)
                albumTargetProfile = null
            },
        )
    }


    profileSettingsTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                profileSettingsTarget = null
                editedProfileName = ""
            },
            title = { Text(stringResource(Res.string.profile_settings_title)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedProfileName,
                        onValueChange = { editedProfileName = it },
                        enabled = target.id != Profile.ALL_PHOTOS_ID,
                        label = { Text(stringResource(Res.string.profile_name_hint)) },
                        singleLine = true,
                    )
                    TextButton(onClick = {
                        passwordTarget = target
                        profilePassword = ""
                        confirmProfilePassword = ""
                        passwordError = ""
                        useBiometric = viewModel.isProfileBiometricEnabled(target.id)
                        profileSettingsTarget = null
                    }) { Text(stringResource(Res.string.profile_lock)) }
                    if (target.id in lockedProfileIds) {
                        TextButton(onClick = {
                            viewModel.removeProfileLock(target.id)
                            profileSettingsTarget = null
                        }) { Text(stringResource(Res.string.profile_remove_lock)) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = target.id != Profile.ALL_PHOTOS_ID && editedProfileName.isNotBlank() && editedProfileName.trim() != target.name,
                    onClick = {
                        viewModel.renameProfile(target.id, editedProfileName)
                        profileSettingsTarget = null
                        editedProfileName = ""
                    }
                ) { Text(stringResource(Res.string.profile_rename)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    profileSettingsTarget = null
                    editedProfileName = ""
                }) { Text(stringResource(Res.string.profile_cancel)) }
            }
        )
    }

    passwordTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                passwordTarget = null
                profilePassword = ""
                confirmProfilePassword = ""
                passwordError = ""
            },
            title = { Text(stringResource(Res.string.profile_lock)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = profilePassword,
                        onValueChange = { profilePassword = it },
                        label = { Text(stringResource(Res.string.profile_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmProfilePassword,
                        onValueChange = { confirmProfilePassword = it },
                        label = { Text(stringResource(Res.string.profile_password_confirm)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useBiometric,
                            enabled = biometricState.isSupported,
                            onCheckedChange = { useBiometric = it }
                        )
                        Text(stringResource(Res.string.profile_biometric))
                    }
                    if (passwordError.isNotBlank()) {
                        Text(passwordError, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = profilePassword.length >= 4 && confirmProfilePassword.isNotBlank(),
                    onClick = {
                        if (profilePassword != confirmProfilePassword) {
                            passwordError = passwordMismatchText
                        } else {
                            scope.launch {
                                viewModel.setProfilePassword(target.id, profilePassword)
                                viewModel.setProfileBiometricEnabled(target.id, useBiometric)
                                passwordTarget = null
                                profilePassword = ""
                                confirmProfilePassword = ""
                                onProfileSelected()
                            }
                        }
                    }
                ) { Text(stringResource(Res.string.profile_lock)) }
            },
            dismissButton = {
                TextButton(onClick = { passwordTarget = null }) {
                    Text(stringResource(Res.string.profile_cancel))
                }
            }
        )
    }

    unlockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                unlockTarget = null
                unlockPassword = ""
                passwordError = ""
                openSettingsAfterUnlock = false
            },
            title = { Text(stringResource(Res.string.profile_unlock)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    OutlinedTextField(
                        value = unlockPassword,
                        onValueChange = { unlockPassword = it },
                        label = { Text(stringResource(Res.string.profile_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    if (passwordError.isNotBlank()) {
                        Text(passwordError, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = unlockPassword.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            when (viewModel.verifyProfilePassword(target.id, unlockPassword)) {
                                VerifyResult.Success -> {
                                    viewModel.unlockProfileSession(target.id)
                                    viewModel.selectProfile(target.id)
                                    unlockTarget = null
                                    unlockPassword = ""
                                    passwordError = ""
                                    if (openSettingsAfterUnlock) {
                                        profileSettingsTarget = target
                                        editedProfileName = target.name
                                        openSettingsAfterUnlock = false
                                    } else {
                                        onProfileSelected()
                                    }
                                }
                                is VerifyResult.Failed, is VerifyResult.LockedOut ->
                                    passwordError = invalidPasswordText
                            }
                        }
                    }
                ) { Text(stringResource(Res.string.profile_unlock)) }
            },
            dismissButton = {
                Row {
                    if (viewModel.isProfileBiometricEnabled(target.id) && biometricState.isSupported) {
                        TextButton(onClick = { biometricState.authenticate() }) {
                            Text(stringResource(Res.string.profile_use_biometric))
                        }
                    }
                    TextButton(onClick = { unlockTarget = null; openSettingsAfterUnlock = false }) {
                        Text(stringResource(Res.string.profile_cancel))
                    }
                }
            }
        )
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
