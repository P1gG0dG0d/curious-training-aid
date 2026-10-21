package com.haoqi.training.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.haoqi.training.domain.CalorieChartMath
import com.haoqi.training.ui.theme.BarDashedLine
import com.haoqi.training.ui.theme.BarExpenditure
import com.haoqi.training.ui.theme.BarIntake
import com.haoqi.training.ui.theme.Hairline
import com.haoqi.training.ui.theme.MacroCarbs
import com.haoqi.training.ui.theme.MacroFat
import com.haoqi.training.ui.theme.MacroProtein
import java.util.Locale

/**
 * 每日热量双进度条：灰条=总消耗、黑条=摄入，共用横向 kcal 刻度，
 * 竖虚线固定在 max(消耗, 摄入) − 500 的位置（见 [CalorieChartMath]）。
 */
@Composable
fun CalorieBars(
    expenditureKcal: Float,
    intakeKcal: Float,
    modifier: Modifier = Modifier,
) {
    val axisMax = CalorieChartMath.axisMaxKcal(expenditureKcal, intakeKcal)
    val expenditureTarget = CalorieChartMath.fractionOf(expenditureKcal, axisMax)
    val intakeTarget = CalorieChartMath.fractionOf(intakeKcal, axisMax)
    val lineTarget = CalorieChartMath.fractionOf(
        CalorieChartMath.lineKcal(expenditureKcal, intakeKcal),
        axisMax,
    )

    val expenditureFraction by animateFloatAsState(expenditureTarget, tween(500, easing = FastOutSlowInEasing), label = "expenditure")
    val intakeFraction by animateFloatAsState(intakeTarget, tween(500, easing = FastOutSlowInEasing), label = "intake")
    val lineFraction by animateFloatAsState(lineTarget, tween(500, easing = FastOutSlowInEasing), label = "line")

    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val barHeight = 24.dp.toPx()
        val corner = CornerRadius(barHeight / 2)
        val topY = 6.dp.toPx()
        val bottomY = topY + barHeight + 12.dp.toPx()

        // 只画实际数值；无底槽或固定终点，刻度随当前数据自适应。
        // 灰条：总消耗
        if (expenditureFraction > 0f) {
            drawRoundRect(BarExpenditure, Offset(0f, topY), Size(size.width * expenditureFraction, barHeight), corner)
        }
        // 黑条：摄入
        if (intakeFraction > 0f) {
            drawRoundRect(BarIntake, Offset(0f, bottomY), Size(size.width * intakeFraction, barHeight), corner)
        }

        // 500kcal 竖虚线
        val lineX = (size.width * lineFraction).coerceIn(1f, size.width - 1f)
        drawLine(
            color = BarDashedLine,
            start = Offset(lineX, 0f),
            end = Offset(lineX, size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

/** 蛋白质 / 碳水 / 脂肪 摄入量柱状图（每日总量） */
@Composable
fun MacroBars(
    protein: Float,
    carbs: Float,
    fat: Float,
    modifier: Modifier = Modifier,
) {
    val maxValue = maxOf(protein, carbs, fat, 1f)
    Row(
        modifier = modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        MacroBar("蛋白质", protein, MacroProtein, maxValue, Modifier.weight(1f))
        MacroBar("碳水", carbs, MacroCarbs, maxValue, Modifier.weight(1f))
        MacroBar("脂肪", fat, MacroFat, maxValue, Modifier.weight(1f))
    }
}

@Composable
private fun MacroBar(
    label: String,
    value: Float,
    color: Color,
    maxValue: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = (value / maxValue).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(500, easing = FastOutSlowInEasing), label = label)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatGrams(value),
            style = MaterialTheme.typography.titleSmall,
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight(animated.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

fun formatGrams(value: Float): String = String.format(Locale.US, "%.1f g", value)

fun formatKcal(value: Float): String = "${value.toInt()} kcal"
