package com.yassmine.projetpfe.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yassmine.projetpfe.ui.theme.ErrorRed
import com.yassmine.projetpfe.ui.theme.SuccessGreen


@Composable
fun AppSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        val label = data.visuals.actionLabel
        val isError = label == "ERROR"

        Snackbar(
            modifier = Modifier.shadow(6.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            containerColor = if (isError) ErrorRed else SuccessGreen,
            contentColor = Color.White,
            actionContentColor = Color.White,
            dismissActionContentColor = Color.White,
            content = { Text(data.visuals.message) },
        )
    }
}

suspend fun SnackbarHostState.showSuccess(message: String) {
    showSnackbar(
        message = message,
        actionLabel = "SUCCESS",
        duration = SnackbarDuration.Short
    )
}

suspend fun SnackbarHostState.showError(message: String) {
    showSnackbar(
        message = message,
        actionLabel = "ERROR",
        duration = SnackbarDuration.Short
    )
}
