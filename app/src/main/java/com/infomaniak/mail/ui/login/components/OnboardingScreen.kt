/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.ui.login.components

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.LottieAnimationView
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.core.crossapplogin.back.ExternalAccount
import com.infomaniak.core.crossapplogin.front.components.CrossLoginBottomContent
import com.infomaniak.core.crossapplogin.front.data.CrossLoginDefaults
import com.infomaniak.core.crossapplogin.front.previews.AccountsPreviewParameter
import com.infomaniak.core.onboarding.OnboardingPage
import com.infomaniak.core.onboarding.OnboardingScaffold
import com.infomaniak.core.onboarding.components.OnboardingComponents
import com.infomaniak.core.onboarding.components.OnboardingComponents.DefaultBackground
import com.infomaniak.core.onboarding.components.OnboardingComponents.DefaultTitleAndDescription
import com.infomaniak.core.onboarding.components.OnboardingComponents.RepeatableLottieIllustration
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.AccentColor
import com.infomaniak.mail.ui.login.IlluColors.changeIllustrationColors
import com.infomaniak.mail.ui.theme.MailTheme
import com.infomaniak.mail.utils.extensions.repeatFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    accounts: () -> List<ExternalAccount>,
    skippedIds: () -> Set<Long>,
    isLoginButtonLoading: () -> Boolean,
    isSignUpButtonLoading: () -> Boolean,
    onLogin: () -> Unit,
    onContinueWithSelectedAccounts: () -> Unit,
    onCreateAccount: () -> Unit,
    onUseAnotherAccountClicked: () -> Unit,
    onSaveSkippedAccounts: (Set<Long>) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { Page.entries.size })
    var accentColor by rememberSaveable { mutableStateOf(AccentColor.PINK) }

    val context = LocalContext.current
    val animatedPrimaryColor by animateColorAsState(Color(accentColor.getPrimary(context)), tween(600))
    val animatedOnPrimaryColor by animateColorAsState(Color(accentColor.getOnPrimary(context)), tween(600))
    val animatedOnboardingSecondaryBackground by animateColorAsState(
        targetValue = Color(accentColor.getOnboardingSecondaryBackground(context)),
        animationSpec = tween(600),
    )

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = animatedPrimaryColor,
            onPrimary = animatedOnPrimaryColor,
        )
    ) {
        OnboardingScaffold(
            pagerState = pagerState,
            onboardingPages = Page.entries.mapIndexed { index, page ->
                page.toOnboardingPage(
                    pagerState = pagerState,
                    index = index,
                    accentColor = { accentColor },
                    onSelectAccentColor = { accentColor = it },
                    animatedOnboardingSecondaryBackground = { animatedOnboardingSecondaryBackground },
                )
            },
            bottomContent = { paddingValues ->
                val buttonShape = RoundedCornerShape(dimensionResource(R.dimen.textButtonCornerRadius))

                OnboardingComponents.CrossLoginBottomContent(
                    modifier = Modifier
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues),
                    pagerState = pagerState,
                    accounts = accounts,
                    skippedIds = skippedIds,
                    isLoginButtonLoading = isLoginButtonLoading,
                    isSignUpButtonLoading = isSignUpButtonLoading,
                    titleColor = colorResource(R.color.primaryTextColor),
                    descriptionColor = colorResource(R.color.secondaryTextColor),
                    onLogin = onLogin,
                    onContinueWithSelectedAccounts = onContinueWithSelectedAccounts,
                    onCreateAccount = onCreateAccount,
                    onUseAnotherAccountClicked = onUseAnotherAccountClicked,
                    onSaveSkippedAccounts = onSaveSkippedAccounts,
                    nextButtonShape = buttonShape, // TODO: Compare with old design
                    primaryButtonShape = buttonShape,
                    primaryButtonHeight = dimensionResource(R.dimen.textButtonPrimaryHeight) - dimensionResource(R.dimen.textButtonPrimaryVerticalInset) * 2,
                    accountsBottomSheetCustomization = CrossLoginDefaults.customize(
                        colors = CrossLoginDefaults.colors(descriptionColor = colorResource(R.color.secondaryTextColor))
                    ),
                )
            },
        )
    }
}

private sealed interface Page {
    @get:DrawableRes
    val backgroundRes: Int
    @get:RawRes
    val illustrationRes: Int
    val repeatFrameStart: Int
    val repeatFrameEnd: Int
    @get:StringRes
    val titleRes: Int

    fun toOnboardingPage(
        pagerState: PagerState,
        index: Int,
        accentColor: () -> AccentColor,
        onSelectAccentColor: (AccentColor) -> Unit,
        animatedOnboardingSecondaryBackground: () -> Color
    ): OnboardingPage

    sealed class SimplePage(
        @DrawableRes override val backgroundRes: Int,
        @RawRes override val illustrationRes: Int,
        override val repeatFrameStart: Int,
        override val repeatFrameEnd: Int,
        @StringRes override val titleRes: Int,
        @StringRes val descriptionRes: Int,
    ) : Page {
        override fun toOnboardingPage(
            pagerState: PagerState,
            index: Int,
            accentColor: () -> AccentColor,
            onSelectAccentColor: (AccentColor) -> Unit,
            animatedOnboardingSecondaryBackground: () -> Color
        ): OnboardingPage = OnboardingPage(
            background = {
                DefaultBackground(
                    ImageVector.vectorResource(backgroundRes),
                    modifier = Modifier.padding(bottom = 300.dp),
                    colorFilter = ColorFilter.tint(animatedOnboardingSecondaryBackground()),
                )
            },
            illustration = {
                // TODO
                RepeatableLottieIllustration(
                    lottieRawRes = illustrationRes,
                    isCurrentPageVisible = { pagerState.currentPage == index },
                    firstFrame = repeatFrameStart,
                    lastFrame = repeatFrameEnd,
                    // Force height of the animation because animations are way too big otherwise. Also having the same height for
                    // all animations makes it so content is correctly centered vertically between view pager's screens.
                    modifier = Modifier.height(250.dp)
                )

                // CustomRepeatableLottieIllustration(pagerState, index, accentColor)

                // Illustration(pagerState, index, accentColor)
            },
            text = {
                DefaultTitleAndDescription(
                    title = stringResource(titleRes),
                    description = stringResource(descriptionRes),
                    titleStyle = Typography.h2.copy(color = colorResource(R.color.primaryTextColor)),
                    descriptionStyle = Typography.bodyRegular.copy(color = colorResource(R.color.secondaryTextColor)),
                )
            }
        )
    }

    sealed class ThemePage(
        @DrawableRes override val backgroundRes: Int,
        @RawRes override val illustrationRes: Int,
        override val repeatFrameStart: Int,
        override val repeatFrameEnd: Int,
        @StringRes override val titleRes: Int,
    ) : Page {
        override fun toOnboardingPage(
            pagerState: PagerState,
            index: Int,
            accentColor: () -> AccentColor,
            onSelectAccentColor: (AccentColor) -> Unit,
            animatedOnboardingSecondaryBackground: () -> Color,
        ): OnboardingPage = OnboardingPage(
            background = {
                DefaultBackground(
                    background = ImageVector.vectorResource(backgroundRes),
                    modifier = Modifier.padding(bottom = 300.dp),
                    colorFilter = ColorFilter.tint(animatedOnboardingSecondaryBackground()),
                )
            },
            illustration = {
                // RepeatableLottieIllustration(
                //     lottieRawRes = illustrationRes,
                //     isCurrentPageVisible = { pagerState.currentPage == index },
                //     firstFrame = repeatFrameStart,
                //     lastFrame = repeatFrameEnd,
                //     // Force height of the animation because animations are way too big otherwise. Also having the same height for
                //     // all animations makes it so content is correctly centered vertically between view pager's screens.
                //     modifier = Modifier.height(250.dp)
                // )

                Illustration(pagerState, index, accentColor)
            },
            text = {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Margin.Large)
                        .widthIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(Margin.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        textAlign = TextAlign.Center,
                        text = stringResource(titleRes),
                        style = Typography.h2.copy(color = colorResource(R.color.primaryTextColor)),
                    )
                    AccentColorSelector(accentColor, onSelectAccentColor)
                }
            }
        )
    }

    data object ThemeChoice : ThemePage(
        backgroundRes = R.drawable.ic_back_wave_1,
        illustrationRes = R.raw.illustration_onboarding_1,
        repeatFrameStart = 54,
        repeatFrameEnd = 138,
        titleRes = R.string.onBoardingTitle1,
    )

    data object SwipeActions : SimplePage(
        backgroundRes = R.drawable.ic_back_wave_2,
        illustrationRes = R.raw.illustration_onboarding_2,
        repeatFrameStart = 108,
        repeatFrameEnd = 253,
        titleRes = R.string.onBoardingTitle2,
        descriptionRes = R.string.onBoardingDescription2,
    )

    data object MultiSelect : SimplePage(
        backgroundRes = R.drawable.ic_back_wave_3,
        illustrationRes = R.raw.illustration_onboarding_3,
        repeatFrameStart = 111,
        repeatFrameEnd = 187,
        titleRes = R.string.onBoardingTitle3,
        descriptionRes = R.string.onBoardingDescription3,
    )

    data object GetStarted : SimplePage(
        backgroundRes = R.drawable.ic_back_wave_4,
        illustrationRes = R.raw.illustration_onboarding_4,
        repeatFrameStart = 127,
        repeatFrameEnd = 236,
        titleRes = R.string.onBoardingTitle4,
        descriptionRes = R.string.onBoardingDescription4,
    )

    companion object {
        val entries: Array<Page> get() = arrayOf(ThemeChoice, SwipeActions, MultiSelect, GetStarted)
    }
}

// @Composable
// private fun Page.CustomRepeatableLottieIllustration(pagerState: PagerState, index: Int, theme: () -> AccentColor) {
//     DotLottieAnimation(
//         source = DotLottieSource.Res(illustrationRes),
//         themeId =
//     )
// }

@Composable
private fun Page.Illustration(pagerState: PagerState, index: Int, theme: () -> AccentColor) {
    AndroidView(
        modifier = Modifier.height(250.dp),
        factory = {
            LottieAnimationView(it).apply {
                setAnimation(illustrationRes)
                repeatFrame(repeatFrameStart, repeatFrameEnd)
            }
        },
        update = {
            if (pagerState.currentPage == index) it.playAnimation()
            // TODO: Fix animation recomposition that restarts the animation. Maybe the fix should be to do the animation in
            //  compose instead
            it.changeIllustrationColors(index, theme())
        }
    )
}

@Preview
@Composable
private fun Preview(@PreviewParameter(AccountsPreviewParameter::class) accounts: List<ExternalAccount>) {
    MailTheme {
        Surface(color = colorResource(R.color.backgroundColor)) {
            OnboardingScreen(
                accounts = { accounts },
                skippedIds = { emptySet() },
                onLogin = {},
                onContinueWithSelectedAccounts = {},
                onCreateAccount = {},
                onUseAnotherAccountClicked = {},
                onSaveSkippedAccounts = {},
                isLoginButtonLoading = { false },
                isSignUpButtonLoading = { false },
            )
        }
    }
}
