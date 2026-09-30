package de.ca.qcc.feature.duplicates

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ca.qcc.R
import kotlin.math.min

private val GaugeTrack = Color(0xFFE6E6E6)
private val GaugeGradient = listOf(Color(0xFFE53935), Color(0xFFFBC02D), Color(0xFF2E7D32))

@Composable
fun ProximityGauge(proximity: Int, active: Boolean, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(targetValue = proximity / 100f * 180f, label = "sweep")

    // Arc "breathes" in the beep rhythm
    val periodMs = (beepIntervalMs(proximity) ?: 1000L).toInt().coerceAtLeast(160)
    val breathing = active && proximity > 0
    val alpha by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = if (breathing) 0.55f else 1f,
        animationSpec = infiniteRepeatable(tween(periodMs / 2), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(modifier.fillMaxWidth().aspectRatio(2f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val diameter = min(size.width, size.height * 2) - stroke
            val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = GaugeTrack,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.horizontalGradient(GaugeGradient),
                    startAngle = 180f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = alpha,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$proximity", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Text("% " + stringResource(R.string.dup_proximity), color = Color(0xFF555555))
        }
    }
}
