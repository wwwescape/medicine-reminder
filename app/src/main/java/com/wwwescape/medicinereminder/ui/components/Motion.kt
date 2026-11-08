package com.wwwescape.medicinereminder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Counts a number up (or down) to [target], restarting from wherever it is whenever [target]
 * changes — so live values (an adherence percentage) glide instead of jumping. */
@Composable
fun rememberCountUp(target: Long, durationMillis: Int = 900, startFromZero: Boolean = true): Long {
    val animatable = remember { Animatable(if (startFromZero) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        animatable.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return animatable.value.toLong()
}

/** Fades and slides its content in after [index] × [staggerMillis], once per [key] — used to
 * cascade result cards onto the screen. */
@Composable
fun StaggeredEntrance(
    index: Int,
    key: Any,
    modifier: Modifier = Modifier,
    staggerMillis: Long = 70,
    content: @Composable () -> Unit,
) {
    var visible by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!visible) {
            delay(index * staggerMillis)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(380)) +
            slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 3 } +
            scaleIn(initialScale = 0.96f, animationSpec = tween(380)),
    ) {
        content()
    }
}
