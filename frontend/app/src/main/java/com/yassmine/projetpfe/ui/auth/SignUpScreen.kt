package com.yassmine.projetpfe.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.theme.ConveneTheme
import com.yassmine.projetpfe.ui.theme.TextGray
import com.yassmine.projetpfe.ui.theme.White
import com.yassmine.projetpfe.viewmodel.AuthUiState
import com.yassmine.projetpfe.viewmodel.AuthViewModel

@Composable
fun SignUpScreen(
    onBackToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SignUpScreenContent(
        uiState = uiState,
        onRegister = { fullName, email, password ->
            viewModel.register(fullName, email, password)
        },
        onResetState = { viewModel.resetState() },
        onBackToLogin = onBackToLogin,
        onSignUpSuccess = onSignUpSuccess
    )
}

@Composable
private fun SignUpScreenContent(
    uiState: AuthUiState,
    onRegister: (String, String, String) -> Unit,
    onResetState: () -> Unit,
    onBackToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreeToTerms by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isLoading = uiState is AuthUiState.Loading
    val isEmailValid = email.contains("@") && email.contains(".")
    val isPasswordValid = password.length >= 6
    val doPasswordsMatch = password == confirmPassword && confirmPassword.isNotEmpty()

    val isFormValid =
        fullName.isNotBlank() &&
                isEmailValid &&
                isPasswordValid &&
                doPasswordsMatch &&
                agreeToTerms

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Success -> {
                onResetState()
                onSignUpSuccess()
            }
            is AuthUiState.Error -> {
                snackbarHostState.showError(state.message)
                onResetState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(44.dp))

                ConveneLogo(subtitle = stringResource(id = R.string.auth_signup_subtitle))

                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {

                        Text(
                            text = stringResource(id = R.string.auth_create_account),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        AuthTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = stringResource(id = R.string.auth_full_name),
                            placeholder = stringResource(id = R.string.auth_full_name_placeholder),
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        AuthTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = stringResource(id = R.string.auth_email),
                            placeholder = stringResource(id = R.string.auth_email_placeholder),
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            isError = email.isNotEmpty() && !isEmailValid,
                            errorMessage = stringResource(id = R.string.auth_invalid_email)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        PasswordTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(id = R.string.auth_password),
                            placeholder = stringResource(id = R.string.auth_password_create_placeholder),
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            isError = password.isNotEmpty() && !isPasswordValid,
                            errorMessage = stringResource(id = R.string.auth_password_min_chars)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        PasswordTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = stringResource(id = R.string.auth_password_confirm),
                            placeholder = stringResource(id = R.string.auth_password_confirm_placeholder),
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            isError = confirmPassword.isNotEmpty() && !doPasswordsMatch,
                            errorMessage = stringResource(id = R.string.auth_passwords_no_match)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = agreeToTerms,
                                onCheckedChange = { agreeToTerms = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    checkmarkColor = White
                                )
                            )
                            Text(
                                text = stringResource(id = R.string.auth_terms),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        AuthButton(
                            text = stringResource(id = R.string.auth_create_account_button),
                            onClick = { onRegister(fullName, email, password) },
                            enabled = isFormValid && !isLoading,
                            isLoading = isLoading
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val signInText = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = TextGray)) {
                                append(stringResource(id = R.string.auth_have_account))
                            }
                            pushStringAnnotation("SIGNIN", "signin")
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(stringResource(id = R.string.auth_sign_in_link))
                            }
                            pop()
                        }

                        ClickableText(
                            text = signInText,
                            onClick = { offset ->
                                signInText.getStringAnnotations("SIGNIN", offset, offset)
                                    .firstOrNull()?.let {
                                        onBackToLogin()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SignUpScreenPreview() {
    ConveneTheme {
        SignUpScreenContent(
            uiState = AuthUiState.Idle,
            onRegister = { _, _, _ -> },
            onResetState = {},
            onBackToLogin = {},
            onSignUpSuccess = {}
        )
    }
}
