package com.haoqi.training.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

/**
 * 全 App 统一动画参数：所有时长 / 缓动 / 回弹都从这里取，改一处全局生效。
 */
object Motion {
    val fadeIn = tween<Float>(200, easing = FastOutSlowInEasing)
    val fadeOut = tween<Float>(120, easing = FastOutSlowInEasing)
    val press = tween<Float>(120, easing = FastOutSlowInEasing)
    val slideEnter = tween<IntOffset>(220, easing = FastOutSlowInEasing)
    val slideExit = tween<IntOffset>(120, easing = FastOutSlowInEasing)
    val pageSlideIn = tween<Float>(320, easing = FastOutSlowInEasing)
    val pageSlideOut = tween<Float>(240, easing = FastOutSlowInEasing)
    val navSlide = tween<Float>(320, easing = FastOutSlowInEasing)
    val navIcon: SpringSpec<Float> = spring(dampingRatio = 0.5f, stiffness = 600f)
    val color = tween<Color>(180, easing = FastOutSlowInEasing)
}
