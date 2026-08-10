package com.yassmine.projetpfe.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(1) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Cooldown timer for "Resend code" button
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    val sendCode: () -> Unit = sendCode@{
        val trimmedEmail = email.trim()

        if (trimmedEmail.isEmpty() ||
            !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
        ) {
            scope.launch {
                snackbarHostState.showError("Adresse email invalide")
            }
            return@sendCode
        }

        isLoading = true
        viewModel.forgotPassword(trimmedEmail) { result ->
            scope.launch {
                if (result.isSuccess) {
                    step = 2
                    resendCooldown = 60
                } else {
                    snackbarHostState.showError(
                        result.exceptionOrNull()?.message ?: "Send failed. Please retry."
                    )
                }
                isLoading = false
            }
        }
    }

    val resetPwd: () -> Unit = resetPwd@{
        when {
            code.length != 6 || !code.all { it.isDigit() } -> {
                scope.launch {
                    snackbarHostState.showError("Le code doit contenir exactement 6 chiffres")
                }
                return@resetPwd
            }
            newPassword.length < 6 -> {
                scope.launch {
                    snackbarHostState.showError("Le mot de passe doit contenir au moins 6 caractères")
                }
                return@resetPwd
            }
            newPassword != confirmPassword -> {
                scope.launch {
                    snackbarHostState.showError("Les mots de passe ne correspondent pas")
                }
                return@resetPwd
            }
        }

        isLoading = true
        viewModel.resetPassword(
            email = email.trim(),
            code = code.trim(),
            newPassword = newPassword
        ) { result ->
            scope.launch {
                if (result.isSuccess) {
                    snackbarHostState.showSuccess("Mot de passe réinitialisé avec succès !")
                    delay(1500)
                    onNavigateToLogin()
                } else {
                    snackbarHostState.showError(
                        result.exceptionOrNull()?.message
                            ?: "Invalid or expired code. Please request a new one."
                    )
                }
                isLoading = false
            }
        }
    }

    // mask email (y***@gmail.com)
    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        val local = parts[0]
        val masked = if (local.length <= 2) local else local[0] + "***"
        return "$masked@${parts[1]}"
    }
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (step == 1) stringResource(id = R.string.forgot_password_title) else stringResource(id = R.string.forgot_password_verification),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == 2) {
                            step = 1
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, stringResource(id = R.string.forgot_password_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (step == 1) {
                        //  Email Input
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            stringResource(id = R.string.forgot_password_enter_email),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(id = R.string.auth_email)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { sendCode() }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Email, null) }
                        )

                        Button(
                            onClick = sendCode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = email.isNotBlank()
                        ) {
                            Text(stringResource(id = R.string.forgot_password_send_code), style = MaterialTheme.typography.titleMedium)
                        }

                        TextButton(onClick = onNavigateBack) {
                            Text(stringResource(id = R.string.forgot_password_back_to_login))
                        }

                    } else {
                        // Code + New Password 
                        Icon(
                            Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(id = R.string.forgot_password_code_sent, maskEmail(email)),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { newCode ->
                                if (newCode.length <= 6 && newCode.all { it.isDigit() }) {
                                    code = newCode
                                }
                            },
                            label = { Text(stringResource(id = R.string.forgot_password_code_label)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, null) }
                        )

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(id = R.string.forgot_password_new_password)) },
                            visualTransformation = if (newPasswordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                    Icon(
                                        if (newPasswordVisible)
                                            Icons.Default.VisibilityOff
                                        else
                                            Icons.Default.Visibility,
                                        null
                                    )
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(stringResource(id = R.string.forgot_password_confirm_password)) },
                            visualTransformation = if (confirmPasswordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        if (confirmPasswordVisible)
                                            Icons.Default.VisibilityOff
                                        else
                                            Icons.Default.Visibility,
                                        null
                                    )
                                }
                            },
                            isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                            supportingText = {
                                if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
                                    Text(
                                        stringResource(id = R.string.auth_passwords_no_match),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { resetPwd() }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = resetPwd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = code.length == 6 &&
                                    newPassword.length >= 6 &&
                                    newPassword == confirmPassword
                        ) {
                            Text(stringResource(id = R.string.forgot_password_reset),
                                style = MaterialTheme.typography.titleMedium)
                        }

                        TextButton(
                            onClick = {
                                if (resendCooldown == 0) {
                                    sendCode()
                                    resendCooldown = 60
                                }
                            },
                            enabled = resendCooldown == 0
                        ) {
                            Text(
                                if (resendCooldown > 0)
                                    stringResource(id = R.string.forgot_password_resend_in, resendCooldown)
                                else
                                    stringResource(id = R.string.forgot_password_resend)
                            )
                        }
                    }
                }
            }
        }
    }
}
