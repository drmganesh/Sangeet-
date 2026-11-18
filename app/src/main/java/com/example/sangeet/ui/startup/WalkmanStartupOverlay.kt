package com.example.sangeet.ui.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen AMOLED black overlay playing the SANGEET Walkman startup sequence on launch
 * or on-demand from Settings without interrupting ExoPlayer playback or reloading repositories.
 */
@Composable
fun WalkmanStartupOverlay(
    preferences: StartupAnimationPreferencesSnapshot,
    isPreview: Boolean,
    isNotificationPlayerLaunch: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)

    val isReducedMotion = remember(context) {
        StartupTimelinePlanner.isSystemReducedMotion(context)
    }

    val plan = remember(preferences, isReducedMotion, isNotificationPlayerLaunch, isPreview) {
        StartupTimelinePlanner.buildPlan(
            prefs = preferences,
            isReducedMotion = isReducedMotion,
            isNotificationPlayerLaunch = isNotificationPlayerLaunch,
            isPreview = isPreview
        )
    }

    if (!plan.shouldPlay) {
        LaunchedEffect(plan) {
            latestOnFinished()
        }
        return
    }

    val timelineProgress = remember(plan) { Animatable(0f) }
    var dismissed by remember { mutableStateOf(false) }

    // Track which mechanical cues have already fired during this playback run
    val firedCues = remember(plan) { mutableSetOf<StartupMechanicalCue>() }

    DisposableEffect(Unit) {
        onDispose {
            StartupFeedbackEngine.cancelPendingAudio()
        }
    }

    LaunchedEffect(plan) {
        timelineProgress.snapTo(0f)
        timelineProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = plan.totalDurationMs,
                easing = LinearEasing
            )
        )
        if (!dismissed) {
            dismissed = true
            latestOnFinished()
        }
    }

    val p = timelineProgress.value
    val visualState = StartupTimelinePlanner.evaluate(
        progress = p,
        style = plan.effectiveStyle,
        reducedMotionFallback = plan.isReducedMotionFallback
    )

    // Fire optional offline mechanical sound & vibration cues at exact physical thresholds
    if (!plan.isReducedMotionFallback && plan.effectiveStyle != StartupAnimationStyle.LOGO_ONLY) {
        val isShort = plan.effectiveStyle == StartupAnimationStyle.SHORT_WALKMAN
        val doorOpenThresh = if (isShort) 0.14f else 0.17f
        val insertThresh = if (isShort) 0.39f else 0.44f
        val doorCloseThresh = if (isShort) 0.51f else 0.56f
        val playThresh = if (isShort) 0.55f else 0.60f
        val reelThresh = if (isShort) 0.58f else 0.63f

        LaunchedEffect(p >= doorOpenThresh) {
            if (p >= doorOpenThresh && firedCues.add(StartupMechanicalCue.DOOR_OPEN)) {
                StartupFeedbackEngine.triggerCue(
                    context = context,
                    cue = StartupMechanicalCue.DOOR_OPEN,
                    soundEnabled = preferences.mechanicalSoundsEnabled,
                    vibrationEnabled = preferences.vibrationEnabled
                )
            }
        }
        LaunchedEffect(p >= insertThresh) {
            if (p >= insertThresh && firedCues.add(StartupMechanicalCue.CASSETTE_INSERT)) {
                StartupFeedbackEngine.triggerCue(
                    context = context,
                    cue = StartupMechanicalCue.CASSETTE_INSERT,
                    soundEnabled = preferences.mechanicalSoundsEnabled,
                    vibrationEnabled = preferences.vibrationEnabled
                )
            }
        }
        LaunchedEffect(p >= doorCloseThresh) {
            if (p >= doorCloseThresh && firedCues.add(StartupMechanicalCue.DOOR_CLOSE)) {
                StartupFeedbackEngine.triggerCue(
                    context = context,
                    cue = StartupMechanicalCue.DOOR_CLOSE,
                    soundEnabled = preferences.mechanicalSoundsEnabled,
                    vibrationEnabled = preferences.vibrationEnabled
                )
            }
        }
        LaunchedEffect(p >= playThresh) {
            if (p >= playThresh && firedCues.add(StartupMechanicalCue.PLAY_SWITCH_CLICK)) {
                StartupFeedbackEngine.triggerCue(
                    context = context,
                    cue = StartupMechanicalCue.PLAY_SWITCH_CLICK,
                    soundEnabled = preferences.mechanicalSoundsEnabled,
                    vibrationEnabled = preferences.vibrationEnabled
                )
            }
        }
        LaunchedEffect(p >= reelThresh) {
            if (p >= reelThresh && firedCues.add(StartupMechanicalCue.REEL_WHIRL_START)) {
                StartupFeedbackEngine.triggerCue(
                    context = context,
                    cue = StartupMechanicalCue.REEL_WHIRL_START,
                    soundEnabled = preferences.mechanicalSoundsEnabled,
                    vibrationEnabled = preferences.vibrationEnabled
                )
            }
        }
    }

    val skipNow: () -> Unit = {
        if (!dismissed) {
            dismissed = true
            StartupFeedbackEngine.cancelPendingAudio()
            latestOnFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = visualState.overlayAlpha
                scaleX = visualState.exitZoomScale
                scaleY = visualState.exitZoomScale
            }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = skipNow
            )
            .testTag("walkman_startup_overlay"),
        contentAlignment = Alignment.Center
    ) {
        // Subtle warm golden center illumination on AMOLED black backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val g = visualState.backdropGoldGlow
            if (g > 0.01f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BrandGold.copy(alpha = 0.22f * g),
                            BrandDeepNavy.copy(alpha = 0.36f * g),
                            Color.Black
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.48f),
                        radius = size.maxDimension * 0.55f
                    )
                )
            }
        }

        // Scenes 1 -> 6: Vintage Walkman Deck + Hinged Door + Descending Cassette + Play Switch
        if (visualState.walkmanAlpha > 0.01f) {
            WalkmanStartupStage(state = visualState)
        }

        // Scenes 6 -> 8: Circular Golden Emblem + Ring Sweep + SANGEET by Ganeswar
        if (visualState.emblemAlpha > 0.01f || visualState.brandTitleAlpha > 0.01f) {
            SangeetBrandRevealStage(state = visualState)
        }

        // Subtle Skip Intro Pill (minimum 48dp interactive target height)
        Surface(
            onClick = skipNow,
            shape = RoundedCornerShape(999.dp),
            color = BrandDeepNavy.copy(alpha = 0.72f),
            border = BorderStroke(0.9.dp, BrandGold.copy(alpha = 0.45f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp)
                .minimumInteractiveComponentSize()
                .testTag("startup_skip_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPreview) "CLOSE PREVIEW" else "SKIP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    ),
                    color = BrandGoldBright
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = "Skip startup animation",
                    tint = BrandGoldBright,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
