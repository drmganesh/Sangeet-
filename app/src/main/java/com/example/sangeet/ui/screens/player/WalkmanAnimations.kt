package com.example.sangeet.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.sangeet.data.model.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class CassetteReelAnglesState {
    var leftAngleDegrees by mutableFloatStateOf(0f)
    var rightAngleDegrees by mutableFloatStateOf(0f)
    var tapeOffsetPx by mutableFloatStateOf(0f)
}

/**
 * Frame-accurate reel angle driver that:
 * - Rotates continuously around 60 FPS while [isPlaying] is true and cassette is seated
 * - Halts smoothly when paused/stopped WITHOUT resetting rotation angles
 * - Resumes seamlessly from exact preserved angle
 * - Supports momentary seek speed impulses for Rewind (-) and Fast-Forward (+)
 */
@Composable
fun rememberCassetteReelAngles(
    isPlaying: Boolean,
    isCassetteSeated: Boolean,
    progressFraction: Float,
    seekImpulseDirection: Int,
    seekImpulseToken: Int,
    reducedMotion: Boolean
): CassetteReelAnglesState {
    val state = remember { CassetteReelAnglesState() }
    var impulseVelocityDegPerSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(seekImpulseToken) {
        if (seekImpulseToken > 0 && seekImpulseDirection != 0) {
            impulseVelocityDegPerSec = seekImpulseDirection * if (reducedMotion) 120f else 540f
            delay(360L)
            impulseVelocityDegPerSec = 0f
        }
    }

    val shouldAnimate = isCassetteSeated && (isPlaying || impulseVelocityDegPerSec != 0f)

    LaunchedEffect(shouldAnimate, reducedMotion) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                if (lastFrameNs != 0L) {
                    val dt = ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val basePlaySpeed = if (isPlaying) (if (reducedMotion) 28f else 74f) else 0f
                    val activeVel = if (impulseVelocityDegPerSec != 0f) impulseVelocityDegPerSec else basePlaySpeed

                    // Realistic cassette hub physics: fuller spool rotates slightly slower than emptier spool
                    val leftRadiusFactor = 1.18f - 0.34f * (1f - progressFraction.coerceIn(0f, 1f))
                    val rightRadiusFactor = 1.18f - 0.34f * progressFraction.coerceIn(0f, 1f)

                    state.leftAngleDegrees = (state.leftAngleDegrees + activeVel * leftRadiusFactor * dt) % 360f
                    state.rightAngleDegrees = (state.rightAngleDegrees + activeVel * rightRadiusFactor * dt) % 360f
                    state.tapeOffsetPx = (state.tapeOffsetPx + (activeVel * 0.35f) * dt) % 48f
                }
                lastFrameNs = frameNs
            }
        }
    }

    return state
}

@Stable
class CassetteSwapController(
    private val scope: CoroutineScope,
    private val reducedMotion: Boolean,
    private val onPlayMechanicalSound: (MechanicalSoundType) -> Unit
) {
    var phase by mutableStateOf(CassetteSwapPhase.IDLE)
        internal set
    var direction by mutableStateOf(CassetteSwapDirection.NEXT)
        internal set

    var settledTrack by mutableStateOf<AudioTrack?>(null)
        internal set
    var outgoingTrack by mutableStateOf<AudioTrack?>(null)
        internal set
    var incomingTrack by mutableStateOf<AudioTrack?>(null)
        internal set

    val doorOpenAnim = Animatable(0f)
    val ejectAnim = Animatable(0f)
    val insertAnim = Animatable(0f)

    private var swapJob: Job? = null
    internal var pendingTrackChangeCallback: Boolean = false
    internal var lastKnownTrackId by mutableLongStateOf(-1L)

    val isCassetteSeated: Boolean
        get() = phase == CassetteSwapPhase.IDLE

    fun syncExternalTrack(newTrack: AudioTrack?) {
        if (newTrack == null) {
            settledTrack = null
            lastKnownTrackId = -1L
            return
        }
        if (lastKnownTrackId == -1L) {
            settledTrack = newTrack
            lastKnownTrackId = newTrack.id
            return
        }
        if (newTrack.id == lastKnownTrackId) {
            // Keep metadata fresh even if track id matches
            settledTrack = newTrack
            if (incomingTrack?.id == newTrack.id) incomingTrack = newTrack
            return
        }

        val previous = settledTrack ?: newTrack
        lastKnownTrackId = newTrack.id

        if (pendingTrackChangeCallback) {
            // Track updated right on schedule during triggered swap -> bind incomingTrack immediately
            pendingTrackChangeCallback = false
            incomingTrack = newTrack
            settledTrack = newTrack
            return
        }

        // External track change (song ended naturally, queue pick, notification next/prev)
        runSequence(
            oldTrack = previous,
            newTrack = newTrack,
            swapDirection = CassetteSwapDirection.NEXT,
            onSwitchTrack = null
        )
    }

    fun triggerManualSwap(
        currentDisplayedTrack: AudioTrack?,
        swapDirection: CassetteSwapDirection,
        expectedNextTrack: AudioTrack?,
        onSwitchTrack: () -> Unit
    ) {
        val old = currentDisplayedTrack ?: settledTrack ?: return
        runSequence(
            oldTrack = old,
            newTrack = expectedNextTrack,
            swapDirection = swapDirection,
            onSwitchTrack = onSwitchTrack
        )
    }

    private fun runSequence(
        oldTrack: AudioTrack,
        newTrack: AudioTrack?,
        swapDirection: CassetteSwapDirection,
        onSwitchTrack: (() -> Unit)?
    ) {
        swapJob?.cancel()
        swapJob = scope.launch {
            direction = swapDirection
            outgoingTrack = oldTrack
            incomingTrack = newTrack ?: oldTrack

            if (reducedMotion) {
                // Reduced-motion / low-performance crisp fallback
                phase = CassetteSwapPhase.OPENING
                doorOpenAnim.animateTo(0.45f, tween(70, easing = FastOutSlowInEasing))
                phase = CassetteSwapPhase.EJECTING
                ejectAnim.snapTo(0f)
                ejectAnim.animateTo(1f, tween(90, easing = FastOutLinearInEasing))
                if (onSwitchTrack != null) {
                    pendingTrackChangeCallback = true
                    onSwitchTrack()
                }
                phase = CassetteSwapPhase.INSERTING
                insertAnim.snapTo(0f)
                insertAnim.animateTo(1f, tween(110, easing = LinearOutSlowInEasing))
                settledTrack = incomingTrack
                phase = CassetteSwapPhase.CLOSING
                doorOpenAnim.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
                phase = CassetteSwapPhase.IDLE
                outgoingTrack = null
                return@launch
            }

            // STEP 1: OPEN compartment door
            phase = CassetteSwapPhase.OPENING
            onPlayMechanicalSound(MechanicalSoundType.DOOR_MECHANISM)
            ejectAnim.snapTo(0f)
            insertAnim.snapTo(0f)
            doorOpenAnim.animateTo(1f, tween(durationMillis = 120, easing = FastOutSlowInEasing))

            // STEP 2: EJECT old cassette up & out
            phase = CassetteSwapPhase.EJECTING
            ejectAnim.animateTo(1f, tween(durationMillis = 200, easing = FastOutLinearInEasing))

            // STEP 3: REPLACE - switch actual queue track cleanly right as old cassette clears bay
            if (onSwitchTrack != null) {
                pendingTrackChangeCallback = true
                onSwitchTrack()
                // Brief frame settle so incomingTrack captures the newly emitted track if synchronous
                delay(16L)
            }

            // STEP 4: INSERT new cassette printed with real next song artwork into spindles
            phase = CassetteSwapPhase.INSERTING
            insertAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMedium
                )
            )
            settledTrack = incomingTrack

            // STEP 5: CLOSE compartment door & begin rotating reels
            phase = CassetteSwapPhase.CLOSING
            onPlayMechanicalSound(MechanicalSoundType.LATCH_ENGAGE)
            doorOpenAnim.animateTo(0f, tween(durationMillis = 135, easing = FastOutSlowInEasing))

            pendingTrackChangeCallback = false
            outgoingTrack = null
            phase = CassetteSwapPhase.IDLE
        }
    }
}

@Composable
fun rememberCassetteSwapController(
    currentTrack: AudioTrack?,
    reducedMotion: Boolean,
    onPlayMechanicalSound: (MechanicalSoundType) -> Unit
): CassetteSwapController {
    val scope = rememberCoroutineScope()
    val controller = remember(reducedMotion) {
        CassetteSwapController(
            scope = scope,
            reducedMotion = reducedMotion,
            onPlayMechanicalSound = onPlayMechanicalSound
        )
    }

    LaunchedEffect(currentTrack?.id, currentTrack?.artworkUri, currentTrack?.title) {
        controller.syncExternalTrack(currentTrack)
    }

    return controller
}

/**
 * Reusable eject animation wrapper lifting and sliding the outgoing cassette out of the bay.
 */
@Composable
fun CassetteEjectAnimation(
    progress: Float,
    direction: CassetteSwapDirection,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current.density
    val dirSign = if (direction == CassetteSwapDirection.NEXT) -1f else 1f
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.coerceIn(0f, 1f)
            if (reducedMotion) {
                alpha = 1f - p
                scaleX = 1f - 0.04f * p
                scaleY = 1f - 0.04f * p
            } else {
                translationY = -p * 115f * density
                translationX = dirSign * (p * p) * 165f * density
                rotationZ = dirSign * p * 9.5f
                rotationX = -p * 16f
                cameraDistance = 14f * density
                val s = 1f - 0.14f * p
                scaleX = s
                scaleY = s
                alpha = (1f - ((p - 0.55f).coerceAtLeast(0f) / 0.45f)).coerceIn(0f, 1f)
            }
        }
    ) {
        content()
    }
}

/**
 * Reusable insertion animation wrapper gliding the incoming cassette into the twin spindle bay.
 */
@Composable
fun CassetteInsertAnimation(
    progress: Float,
    direction: CassetteSwapDirection,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current.density
    val enterFromSign = if (direction == CassetteSwapDirection.NEXT) 1f else -1f
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.coerceIn(0f, 1f)
            val inv = 1f - p
            if (reducedMotion) {
                alpha = p
                scaleX = 0.96f + 0.04f * p
                scaleY = 0.96f + 0.04f * p
            } else {
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                translationY = -inv * 92f * density
                translationX = enterFromSign * inv * inv * 155f * density
                rotationZ = enterFromSign * inv * 7.5f
                rotationX = inv * 12f
                cameraDistance = 14f * density
                val s = 0.88f + 0.12f * p
                scaleX = s
                scaleY = s
                alpha = (p / 0.35f).coerceIn(0f, 1f)
            }
        }
    ) {
        content()
    }
}
