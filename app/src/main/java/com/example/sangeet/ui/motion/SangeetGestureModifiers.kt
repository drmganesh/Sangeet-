package com.example.sangeet.ui.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pure Kotlin gesture evaluator for unit-testable directional axis locking,
 * edge-swipe exclusion, velocity flick thresholds, and debounce safety.
 */
object EdgeSafeSwipeEvaluator {
    const val DEFAULT_EDGE_GUARD_DP = 24f
    const val DEFAULT_VELOCITY_COMMIT_PX_PER_SEC = 540f
    const val DEFAULT_COMMIT_COOLDOWN_MS = 280L

    enum class DragAxis { UNDECIDED, HORIZONTAL, VERTICAL }

    enum class SwipeDecision { NONE, COMMIT_NEGATIVE, COMMIT_POSITIVE, CANCEL }

    fun isInsideEdgeGuard(
        startX: Float,
        containerWidth: Float,
        edgeGuardPx: Float
    ): Boolean {
        if (containerWidth <= edgeGuardPx * 2.2f || edgeGuardPx <= 0f) return false
        return startX < edgeGuardPx || startX > (containerWidth - edgeGuardPx)
    }

    fun resolveDragAxis(
        dx: Float,
        dy: Float,
        touchSlopPx: Float
    ): DragAxis {
        val distance = hypot(dx, dy)
        if (distance < touchSlopPx) return DragAxis.UNDECIDED
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDx > absDy * 1.15f -> DragAxis.HORIZONTAL
            absDy > absDx * 1.15f -> DragAxis.VERTICAL
            else -> {
                // Angle tie-breaker at ~45 deg favors dominant scalar
                val angle = Math.toDegrees(atan2(absDy.toDouble(), absDx.toDouble()))
                if (angle < 42.0) DragAxis.HORIZONTAL else DragAxis.VERTICAL
            }
        }
    }

    fun evaluateSwipeCommit(
        totalOffset: Float,
        velocityPerSec: Float,
        distanceThresholdPx: Float,
        velocityThresholdPxPerSec: Float = DEFAULT_VELOCITY_COMMIT_PX_PER_SEC
    ): SwipeDecision {
        return when {
            velocityPerSec <= -velocityThresholdPxPerSec && totalOffset < -distanceThresholdPx * 0.35f ->
                SwipeDecision.COMMIT_NEGATIVE
            velocityPerSec >= velocityThresholdPxPerSec && totalOffset > distanceThresholdPx * 0.35f ->
                SwipeDecision.COMMIT_POSITIVE
            totalOffset <= -distanceThresholdPx -> SwipeDecision.COMMIT_NEGATIVE
            totalOffset >= distanceThresholdPx -> SwipeDecision.COMMIT_POSITIVE
            else -> SwipeDecision.CANCEL
        }
    }

    fun canAcceptSwipeCommit(
        nowMs: Long,
        lastCommitMs: Long,
        cooldownMs: Long = DEFAULT_COMMIT_COOLDOWN_MS
    ): Boolean {
        return lastCommitMs <= 0L || (nowMs - lastCommitMs) >= cooldownMs
    }
}

/**
 * Interactive, edge-safe horizontal swipe modifier with real-time spring physics,
 * velocity flick detection, strict vertical scroll coexistence, and commit cooldown.
 */
fun Modifier.sangeetHorizontalSwipeDeck(
    enabled: Boolean,
    itemCount: Int = 2,
    distanceThreshold: Dp = 46.dp,
    edgeGuard: Dp = 24.dp,
    maxDragPreviewDp: Dp = 72.dp,
    applyPerspectiveTilt: Boolean = true,
    onSwipeLeftNext: () -> Unit,
    onSwipeRightPrevious: () -> Unit
): Modifier = composed {
    val motion = LocalSangeetMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { distanceThreshold.toPx() }
    val edgeGuardPx = with(density) { edgeGuard.toPx() }
    val maxPreviewPx = with(density) { maxDragPreviewDp.toPx() }

    val dragOffset = remember { Animatable(0f) }
    var lastCommitMs by remember { mutableLongStateOf(0L) }

    val currentEnabled by rememberUpdatedState(enabled && motion.allowHorizontalSwipes && itemCount > 1)
    val currentOnNext by rememberUpdatedState(onSwipeLeftNext)
    val currentOnPrev by rememberUpdatedState(onSwipeRightPrevious)

    this
        .pointerInput(currentEnabled, itemCount) {
            if (!currentEnabled) return@pointerInput
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (EdgeSafeSwipeEvaluator.isInsideEdgeGuard(down.position.x, size.width.toFloat(), edgeGuardPx)) {
                    return@awaitEachGesture
                }
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var totalDx = 0f
                var totalDy = 0f
                var axis = EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED

                var pointer = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer }
                        ?: event.changes.firstOrNull()
                        ?: break
                    pointer = change.id

                    if (!change.pressed) {
                        val velX = tracker.calculateVelocity().x
                        if (axis == EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL) {
                            val decision = EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                                totalOffset = totalDx,
                                velocityPerSec = velX,
                                distanceThresholdPx = thresholdPx
                            )
                            val now = SystemClock.uptimeMillis()
                            if (decision == EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_NEGATIVE &&
                                EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(now, lastCommitMs)
                            ) {
                                lastCommitMs = now
                                currentOnNext()
                            } else if (decision == EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_POSITIVE &&
                                EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(now, lastCommitMs)
                            ) {
                                lastCommitMs = now
                                currentOnPrev()
                            }
                        }
                        scope.launch {
                            dragOffset.animateTo(0f, animationSpec = motion.floatSpring())
                        }
                        break
                    }

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    tracker.addPosition(change.uptimeMillis, change.position)

                    if (axis == EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED) {
                        axis = EdgeSafeSwipeEvaluator.resolveDragAxis(totalDx, totalDy, touchSlop)
                        if (axis == EdgeSafeSwipeEvaluator.DragAxis.VERTICAL) {
                            // Yield immediately to outer LazyColumn scroll without interrupting
                            scope.launch { dragOffset.snapTo(0f) }
                            return@awaitEachGesture
                        }
                    }

                    if (axis == EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL) {
                        change.consume()
                        val clamped = (totalDx * 0.55f).coerceIn(-maxPreviewPx, maxPreviewPx)
                        scope.launch { dragOffset.snapTo(clamped) }
                    }
                }
            }
        }
        .graphicsLayer {
            val offset = dragOffset.value
            if (abs(offset) > 0.25f) {
                translationX = offset
                if (applyPerspectiveTilt && !motion.isReducedMotion) {
                    rotationY = (offset / maxPreviewPx).coerceIn(-1f, 1f) * 4.5f
                    cameraDistance = 12f * density.density
                }
            }
        }
}

/**
 * Bi-axial gesture handler specifically built for the persistent MiniPlayer bar:
 * - Swipe Left -> Next track
 * - Swipe Right -> Previous track
 * - Swipe Up -> Expand Walkman Player
 * - Swipe Down -> Collapse MiniPlayer while preserving background playback
 */
fun Modifier.sangeetMiniPlayerGestures(
    onSwipeLeftNext: () -> Unit,
    onSwipeRightPrevious: () -> Unit,
    onSwipeUpExpand: () -> Unit,
    onSwipeDownCollapse: () -> Unit
): Modifier = composed {
    val motion = LocalSangeetMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val horizontalThresholdPx = with(density) { 44.dp.toPx() }
    val verticalThresholdPx = with(density) { 36.dp.toPx() }
    val edgeGuardPx = with(density) { 24.dp.toPx() }

    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var lastCommitMs by remember { mutableLongStateOf(0L) }

    val currentOnNext by rememberUpdatedState(onSwipeLeftNext)
    val currentOnPrev by rememberUpdatedState(onSwipeRightPrevious)
    val currentOnExpand by rememberUpdatedState(onSwipeUpExpand)
    val currentOnCollapse by rememberUpdatedState(onSwipeDownCollapse)
    val allowH = motion.allowHorizontalSwipes && motion.allowPlayerGestures
    val allowV = motion.allowVerticalSwipes && motion.allowPlayerGestures

    this
        .pointerInput(allowH, allowV) {
            if (!allowH && !allowV) return@pointerInput
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (EdgeSafeSwipeEvaluator.isInsideEdgeGuard(down.position.x, size.width.toFloat(), edgeGuardPx)) {
                    return@awaitEachGesture
                }
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var totalDx = 0f
                var totalDy = 0f
                var axis = EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED
                var pointer = down.id

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer }
                        ?: event.changes.firstOrNull()
                        ?: break
                    pointer = change.id

                    if (!change.pressed) {
                        val vel = tracker.calculateVelocity()
                        val now = SystemClock.uptimeMillis()
                        if (axis == EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL && allowH) {
                            val decision = EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                                totalOffset = totalDx,
                                velocityPerSec = vel.x,
                                distanceThresholdPx = horizontalThresholdPx
                            )
                            if (EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(now, lastCommitMs)) {
                                when (decision) {
                                    EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_NEGATIVE -> {
                                        lastCommitMs = now
                                        currentOnNext()
                                    }
                                    EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_POSITIVE -> {
                                        lastCommitMs = now
                                        currentOnPrev()
                                    }
                                    else -> Unit
                                }
                            }
                        } else if (axis == EdgeSafeSwipeEvaluator.DragAxis.VERTICAL && allowV) {
                            val decision = EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                                totalOffset = totalDy,
                                velocityPerSec = vel.y,
                                distanceThresholdPx = verticalThresholdPx
                            )
                            if (EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(now, lastCommitMs)) {
                                when (decision) {
                                    EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_NEGATIVE -> {
                                        lastCommitMs = now
                                        currentOnExpand()
                                    }
                                    EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_POSITIVE -> {
                                        lastCommitMs = now
                                        currentOnCollapse()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                        scope.launch { dragX.animateTo(0f, motion.floatSpring()) }
                        scope.launch { dragY.animateTo(0f, motion.floatSpring()) }
                        break
                    }

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    tracker.addPosition(change.uptimeMillis, change.position)

                    if (axis == EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED) {
                        val candidate = EdgeSafeSwipeEvaluator.resolveDragAxis(totalDx, totalDy, touchSlop)
                        axis = when (candidate) {
                            EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL -> if (allowH) candidate else return@awaitEachGesture
                            EdgeSafeSwipeEvaluator.DragAxis.VERTICAL -> if (allowV) candidate else return@awaitEachGesture
                            EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED -> EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED
                        }
                    }

                    if (axis == EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL && allowH) {
                        change.consume()
                        scope.launch { dragX.snapTo((totalDx * 0.42f).coerceIn(-68f, 68f)) }
                    } else if (axis == EdgeSafeSwipeEvaluator.DragAxis.VERTICAL && allowV) {
                        change.consume()
                        scope.launch { dragY.snapTo((totalDy * 0.45f).coerceIn(-48f, 48f)) }
                    }
                }
            }
        }
        .graphicsLayer {
            translationX = dragX.value
            translationY = dragY.value
        }
}

/**
 * Vertical swipe modifier for full Walkman player top/stage zone:
 * Swipe down -> dismiss player (if scroll is at top or gesture is strongly downward)
 * Swipe up -> open Lyrics / Queue
 */
fun Modifier.sangeetVerticalPlayerSwipe(
    enabled: Boolean,
    canDismissDown: () -> Boolean = { true },
    onSwipeDownDismiss: () -> Unit,
    onSwipeUpLyrics: () -> Unit
): Modifier = composed {
    val motion = LocalSangeetMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 64.dp.toPx() }
    val dragY = remember { Animatable(0f) }
    var lastCommitMs by remember { mutableLongStateOf(0L) }

    val active = enabled && motion.allowVerticalSwipes && motion.allowPlayerGestures
    val currentOnDismiss by rememberUpdatedState(onSwipeDownDismiss)
    val currentOnLyrics by rememberUpdatedState(onSwipeUpLyrics)

    this
        .pointerInput(active) {
            if (!active) return@pointerInput
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var totalDx = 0f
                var totalDy = 0f
                var axis = EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED
                var pointer = down.id

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer }
                        ?: event.changes.firstOrNull()
                        ?: break
                    pointer = change.id

                    if (!change.pressed) {
                        if (axis == EdgeSafeSwipeEvaluator.DragAxis.VERTICAL) {
                            val velY = tracker.calculateVelocity().y
                            val decision = EdgeSafeSwipeEvaluator.evaluateSwipeCommit(
                                totalOffset = totalDy,
                                velocityPerSec = velY,
                                distanceThresholdPx = thresholdPx
                            )
                            val now = SystemClock.uptimeMillis()
                            if (EdgeSafeSwipeEvaluator.canAcceptSwipeCommit(now, lastCommitMs)) {
                                if (decision == EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_POSITIVE && canDismissDown()) {
                                    lastCommitMs = now
                                    currentOnDismiss()
                                } else if (decision == EdgeSafeSwipeEvaluator.SwipeDecision.COMMIT_NEGATIVE) {
                                    lastCommitMs = now
                                    currentOnLyrics()
                                }
                            }
                        }
                        scope.launch { dragY.animateTo(0f, motion.floatSpring()) }
                        break
                    }

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    tracker.addPosition(change.uptimeMillis, change.position)

                    if (axis == EdgeSafeSwipeEvaluator.DragAxis.UNDECIDED) {
                        axis = EdgeSafeSwipeEvaluator.resolveDragAxis(totalDx, totalDy, touchSlop)
                        if (axis == EdgeSafeSwipeEvaluator.DragAxis.HORIZONTAL) {
                            return@awaitEachGesture
                        }
                        if (axis == EdgeSafeSwipeEvaluator.DragAxis.VERTICAL && totalDy > 0f && !canDismissDown()) {
                            return@awaitEachGesture
                        }
                    }

                    if (axis == EdgeSafeSwipeEvaluator.DragAxis.VERTICAL) {
                        val visualOffset = if (totalDy > 0f && canDismissDown()) {
                            (totalDy * 0.5f).coerceIn(0f, 180f)
                        } else if (totalDy < 0f) {
                            (totalDy * 0.35f).coerceIn(-64f, 0f)
                        } else {
                            0f
                        }
                        if (visualOffset != 0f) {
                            change.consume()
                            scope.launch { dragY.snapTo(visualOffset) }
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            val dy = dragY.value
            if (abs(dy) > 0.25f) {
                translationY = dy
                if (dy > 0f && !motion.isReducedMotion) {
                    val scale = (1f - (dy / 1600f)).coerceIn(0.92f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
}

/**
 * Tactile spring press scale respecting global MotionQuality & Reduced Motion.
 */
fun Modifier.sangeetPressScale(
    interactionSource: MutableInteractionSource
): Modifier = composed {
    val motion = LocalSangeetMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && motion.isMotionEnabled) motion.cardPressScale else 1f,
        animationSpec = motion.floatSpring(),
        label = "sangeetPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

object ModifierGestureScope {
    fun Modifier.applyMiniPlayerGestures(
        onSwipeLeftNext: () -> Unit,
        onSwipeRightPrevious: () -> Unit,
        onSwipeUpExpand: () -> Unit,
        onSwipeDownCollapse: () -> Unit
    ): Modifier = this.sangeetMiniPlayerGestures(
        onSwipeLeftNext = onSwipeLeftNext,
        onSwipeRightPrevious = onSwipeRightPrevious,
        onSwipeUpExpand = onSwipeUpExpand,
        onSwipeDownCollapse = onSwipeDownCollapse
    )
}
