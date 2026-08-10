package com.yassmine.projetpfe.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.ui.theme.ConvenePrimary
import com.yassmine.projetpfe.ui.theme.ConveneSecondary
import com.yassmine.projetpfe.ui.theme.ConveneSecondaryContainer
import com.yassmine.projetpfe.ui.theme.SurfaceContainerLow
import com.yassmine.projetpfe.ui.theme.TextDark
import com.yassmine.projetpfe.ui.theme.TextGray
import com.yassmine.projetpfe.ui.theme.TextLight
import com.yassmine.projetpfe.ui.theme.White

private data class OnboardingUi(
    val imageUrl: String,
    val title: String,
    val highlight: String,
    val subtitle: String,
    val badgeTitle: String,
    val badgeText: String,
)

@Composable
fun OnboardingStep1Screen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val ui = OnboardingUi(
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBZyW6TFvJY62VeAOtb-cGAMn1aH8mXPAeyy6jgLAeLOkEJfHs-yKoQ2D1_Dfu2V5OAIkxAD5v6fkDxQyE3XvVhRszRiQp6DP38FJLjImoqYz9iHyoJeYsBcM-0SkvxQU0IFAsDqvaYbvw3h3f5yx6y_T-Kngp-5CwtpPO2uLP1Obj3uSsMrK0-BnvfabhLBUJql8ZHyxtjJ-F2CNtsvkVs5sALf75ZIgvBya0H2Xqn0M50vWbk9AQ5XLQCvAkXLzZtuLYxRn9Yrs5W",
        title = stringResource(R.string.onboarding_1_title),
        highlight = stringResource(R.string.onboarding_1_highlight),
        subtitle = stringResource(R.string.onboarding_1_subtitle),
        badgeTitle = stringResource(R.string.onboarding_1_badge_title),
        badgeText = stringResource(R.string.onboarding_1_badge_text),
    )
    OnboardingScaffold(
        currentLanguage = currentLanguage,
        onLanguageChange = onLanguageChange,
        onSkip = onSkip,
        onNext = onNext,
        stepIndex = 0,
        isLastStep = false,
        ui = ui,
    )
}

@Composable
fun OnboardingStep2Screen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val ui = OnboardingUi(
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB6pQNmF636bS2isNUi_tW0CMN2cgov-eKvNi7WvvI_3PYhgKUHffDCndSdAz7Y8yzcT_kFGAa6E-SJw-rU7tyLpyJtGnJOzu-sxAMgO22Y9VIe3lwHtWc7cxqmIcb-8oAlUrVdMUi6_an_koGrCMMILckDzUPhJTHNx_RNiUHJjiu3YwctEP-rd3AlFTLbTJkyayApkwKs_pDOLACPu0ZneQgmn4Dj2Gk-lddFuBZ9S5QaHzRA3IQWq99FTD4jl12GGGIiCfpdkKXh",
        title = stringResource(R.string.onboarding_2_title),
        highlight = stringResource(R.string.onboarding_2_highlight),
        subtitle = stringResource(R.string.onboarding_2_subtitle),
        badgeTitle = stringResource(R.string.onboarding_2_badge_title),
        badgeText = stringResource(R.string.onboarding_2_badge_text),
    )
    OnboardingScaffold(
        currentLanguage = currentLanguage,
        onLanguageChange = onLanguageChange,
        onSkip = onSkip,
        onNext = onNext,
        stepIndex = 1,
        isLastStep = false,
        ui = ui,
    )
}

@Composable
fun OnboardingStep3Screen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onSkip: () -> Unit,
    onGetStarted: () -> Unit,
) {
    val ui = OnboardingUi(
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB7Qw5fxIqUvkWjApPwU3alvwVo0UFmfDEjDE61R3XaeITMFmkc69cUxdEn_aYgTdSNpZpVNjJ0iZr1iws-NIRFnV7xGhW5hFfHsL8Z_qtURJgIvJkArtbUG79KKOxYSJms8PydjyTrj9h4mNXnDMmxOt0ge2qghTzays_9oQaCL858ob0ESDOF4Zt4EzfiQbYuSmL2k8XSa5AUG4tutoLKhDhoINaSL__GOhTS3L9Ps8ghWApFQUXGGyGlRxl2h2d17n_5G41V9KQz",
        title = stringResource(R.string.onboarding_3_title),
        highlight = stringResource(R.string.onboarding_3_highlight),
        subtitle = stringResource(R.string.onboarding_3_subtitle),
        badgeTitle = stringResource(R.string.onboarding_3_badge_title),
        badgeText = stringResource(R.string.onboarding_3_badge_text),
    )
    OnboardingScaffold(
        currentLanguage = currentLanguage,
        onLanguageChange = onLanguageChange,
        onSkip = onSkip,
        onNext = onGetStarted,
        stepIndex = 2,
        isLastStep = true,
        ui = ui,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OnboardingScaffold(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    stepIndex: Int,
    isLastStep: Boolean,
    ui: OnboardingUi,
) {
    var showLanguageSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_brand_name),
                color = ConvenePrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguagePickerButton(
                    currentLanguage = currentLanguage,
                    onClick = { showLanguageSheet = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = TextGray,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ConveneSecondaryContainer.copy(alpha = 0.35f))
                        .clickable(onClick = onSkip)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            shape = RoundedCornerShape(36.dp),
            color = SurfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                AsyncImage(
                    model = ui.imageUrl,
                    contentDescription = stringResource(R.string.onboarding_illustration_content_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentScale = ContentScale.Crop
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = ui.badgeTitle,
                            color = TextDark,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = ui.badgeText,
                            color = TextDark,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = ui.title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextDark,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = ui.highlight,
            style = MaterialTheme.typography.headlineLarge,
            color = ConvenePrimary,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = ui.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val selected = index == stepIndex
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(if (selected) 28.dp else 10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) ConvenePrimary else ConveneSecondaryContainer)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            shape = RoundedCornerShape(99.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ConvenePrimary)
        ) {
            Text(
                text = if (isLastStep) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_next),
                color = White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isLastStep) {
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = White
                )
            }
        }

        if (isLastStep) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSkip,
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_learn_more),
                    color = ConveneSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(TextLight.copy(alpha = 0.55f))
                )
            }
        ) {
            LanguageBottomSheetContent(
                currentLanguage = currentLanguage,
                onLanguageSelected = { code ->
                    onLanguageChange(code)
                    showLanguageSheet = false
                }
            )
        }
    }
}

@Composable
private fun LanguagePickerButton(
    currentLanguage: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(ConveneSecondaryContainer.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = ConveneSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (currentLanguage == "fr") {
                stringResource(id = R.string.language_fr)
            } else {
                stringResource(id = R.string.language_en)
            },
            color = TextDark,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 2.dp)
        )
    }
}

@Composable
private fun LanguageBottomSheetContent(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.profile_language_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextDark
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(id = R.string.profile_language_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        LanguageOption(
            label = stringResource(id = R.string.language_fr),
            selected = currentLanguage == "fr",
            onClick = { onLanguageSelected("fr") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LanguageOption(
            label = stringResource(id = R.string.language_en),
            selected = currentLanguage == "en",
            onClick = { onLanguageSelected("en") }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (selected) ConveneSecondaryContainer.copy(alpha = 0.45f) else SurfaceContainerLow,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextDark,
                fontWeight = FontWeight.SemiBold
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ConvenePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
