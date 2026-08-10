package com.yassmine.projetpfe.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.ui.theme.BackgroundLight
import com.yassmine.projetpfe.ui.theme.ConvenePrimary
import com.yassmine.projetpfe.ui.theme.ConveneSecondaryContainer
import com.yassmine.projetpfe.ui.theme.ConveneTheme
import com.yassmine.projetpfe.ui.theme.SurfaceContainerLow
import com.yassmine.projetpfe.ui.theme.TextGray
import com.yassmine.projetpfe.ui.theme.TextLight
import com.yassmine.projetpfe.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(2100L)
        onSplashDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(BackgroundLight, SurfaceContainerLow, White),
                    radius = 1000f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .alpha(alpha)
                    .background(color = White, shape = RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.convene_logo),
                    contentDescription = stringResource(id = R.string.logo_content_description),
                    modifier = Modifier
                        .size(124.dp)
                        .alpha(alpha)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = stringResource(id = R.string.app_brand_name),
                color = ConvenePrimary,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(id = R.string.app_tagline),
                color = TextGray,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(56.dp))

            LinearProgressIndicator(
                progress = { 0.32f },
                modifier = Modifier
                    .width(190.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .alpha(alpha),
                color = ConvenePrimary,
                trackColor = ConveneSecondaryContainer
            )
        }

        Text(
            text = stringResource(id = R.string.splash_footer),
            color = TextLight,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .alpha(alpha)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    ConveneTheme {
        SplashScreen(onSplashDone = {})
    }
}
