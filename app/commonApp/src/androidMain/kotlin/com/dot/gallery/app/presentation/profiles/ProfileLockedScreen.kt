package com.dot.gallery.app.presentation.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.profile_password
import com.dot.gallery.generated.resources.profile_all_photos
import com.dot.gallery.generated.resources.profile_password_invalid
import com.dot.gallery.generated.resources.profile_unlock
import com.dot.gallery.generated.resources.profile_use_biometric
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.domain.model.Profile
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Full-screen gate shown instead of any gallery content while the active profile is locked. */
@Composable
fun ProfileLockedScreen(profileId: Long) {
    val viewModel = injectViewModel<ProfileViewModel> { profileViewModel }
    val profiles by viewModel.profiles.collectAsState()
    val profileName = if (profileId == Profile.ALL_PHOTOS_ID) {
        stringResource(Res.string.profile_all_photos)
    } else profiles.firstOrNull { it.id == profileId }?.name.orEmpty()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val invalidPassword = stringResource(Res.string.profile_password_invalid)

    val biometricState = rememberBiometricState(
        title = stringResource(Res.string.profile_unlock),
        subtitle = stringResource(Res.string.profile_use_biometric),
        onSuccess = { viewModel.unlockProfileSession(profileId) },
        onFailed = { error = invalidPassword }
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Text(
            text = profileName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(Res.string.profile_unlock),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = "" },
            label = { Text(stringResource(Res.string.profile_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Button(
            enabled = password.isNotEmpty(),
            onClick = {
                scope.launch {
                    when (viewModel.verifyProfilePassword(profileId, password)) {
                        VerifyResult.Success -> {
                            viewModel.unlockProfileSession(profileId)
                            password = ""
                            error = ""
                        }
                        is VerifyResult.Failed, is VerifyResult.LockedOut -> error = invalidPassword
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text(stringResource(Res.string.profile_unlock)) }
        if (viewModel.isProfileBiometricEnabled(profileId) && biometricState.isSupported) {
            TextButton(
                onClick = { biometricState.authenticate() },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(Res.string.profile_use_biometric)) }
        }
    }
}

@Composable
fun ProfileLockGateOverlay() {
    val viewModel = injectViewModel<ProfileViewModel> { profileViewModel }
    val rawActiveId by viewModel.rawActiveProfileId.collectAsState()
    val lockedIds by viewModel.lockedProfileIds.collectAsState()
    val unlockedIds by ProfileAccessSession.unlockedProfileIds.collectAsState()
    if (rawActiveId in lockedIds && rawActiveId !in unlockedIds) {
        ProfileLockedScreen(profileId = rawActiveId)
    }
}
