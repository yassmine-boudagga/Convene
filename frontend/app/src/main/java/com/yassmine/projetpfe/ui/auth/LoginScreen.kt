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
fun LoginScreen(
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LoginScreenContent(
        uiState = uiState,
        onLogin = { email, password, rememberMe ->
            viewModel.login(email, password, rememberMe)
        },
        onResetState = { viewModel.resetState() },
        onSignUpClick = onSignUpClick,
        onForgotPasswordClick = onForgotPasswordClick,
        onLoginSuccess = onLoginSuccess
    )
}

@Composable
private fun LoginScreenContent(
    uiState: AuthUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onResetState: () -> Unit,
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isLoading = uiState is AuthUiState.Loading
    val isEmailValid = email.contains("@") && email.contains(".")
    val isFormValid = isEmailValid && password.isNotEmpty()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Success -> {
                onResetState()
                onLoginSuccess()
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(56.dp))

                ConveneLogo(subtitle = stringResource(id = R.string.auth_login_subtitle))

                Spacer(modifier = Modifier.height(40.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {

                    Column(modifier = Modifier.padding(24.dp)) {

                        Text(
                            text = stringResource(id = R.string.auth_welcome_back),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(24.dp))

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

                        Spacer(modifier = Modifier.height(16.dp))

                        PasswordTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(id = R.string.auth_password),
                            placeholder = stringResource(id = R.string.auth_password_placeholder),
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = White
                                    )
                                )
                                Text(
                                    text = stringResource(id = R.string.auth_remember_me),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = onForgotPasswordClick,
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.auth_forgot_password),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        AuthButton(
                            text = stringResource(id = R.string.auth_sign_in),
                            onClick = { onLogin(email, password, rememberMe) },
                            enabled = isFormValid && !isLoading,
                            isLoading = isLoading
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val signUpText = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = TextGray)) {
                                append(stringResource(id = R.string.auth_no_account))
                            }
                            pushStringAnnotation("SIGNUP", "signup")
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(stringResource(id = R.string.auth_sign_up))
                            }
                            pop()
                        }

                        ClickableText(
                            text = signUpText,
                            onClick = { offset ->
                                signUpText.getStringAnnotations("SIGNUP", offset, offset)
                                    .firstOrNull()?.let {
                                        onSignUpClick()
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
fun LoginScreenPreview() {
    ConveneTheme {
        LoginScreenContent(
            uiState = AuthUiState.Idle,
            onLogin = { _, _, _ -> },
            onResetState = {},
            onSignUpClick = {},
            onForgotPasswordClick = {},
            onLoginSuccess = {}
        )
    }
}
