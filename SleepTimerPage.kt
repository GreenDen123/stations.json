package com.allworld.radio

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.min // Нужно для функции min() в расчете размера
import java.util.Locale
import kotlin.math.*

@Composable
fun SleepTimerPage(controller: MediaController?, viewModel: TimerViewModel) {
    var angle by remember { mutableFloatStateOf(0f) }

    // Получаем текущую конфигурацию экрана
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp > 600

    // Адаптивный размер круга (80% ширины экрана, но не более 400dp)
    val canvasSize = min(configuration.screenWidthDp * 0.8f, if (isLargeScreen) 400f else 300f).dp

    val startText = stringResource(R.string.timer_start)
    val stopText = stringResource(R.string.timer_stop)
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error

    val minutes = (angle / 360f * 120).toInt().coerceIn(0, 120)
    val displayTime = if (viewModel.isRunning) {
        String.format(Locale.getDefault(), "%02d:%02d", viewModel.timeLeft / 60, viewModel.timeLeft % 60)
    } else "$minutes"

    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(canvasSize)
                    .pointerInput(viewModel.isRunning) {
                        if (!viewModel.isRunning) {
                            detectDragGestures { change, _ ->
                                val pos = change.position
                                val center = size.width / 2
                                val deg = Math.toDegrees(atan2(pos.y - center, pos.x - center).toDouble()).toFloat() + 90f
                                angle = if (deg < 0) deg + 360f else deg
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                // Оставляем больше места для цифр снаружи кольца
                val radius = size.width / 2 - 40.dp.toPx()

                drawArc(
                    Color.LightGray.copy(0.3f),
                    0f, 360f, false,
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                )

                for (i in 0 until 12) {
                    val tickAngle = i * 30f - 90f
                    val angleRad = Math.toRadians(tickAngle.toDouble())

                    val innerTick = Offset(
                        center.x + (radius - 8.dp.toPx()) * cos(angleRad).toFloat(),
                        center.y + (radius - 8.dp.toPx()) * sin(angleRad).toFloat()
                    )
                    val outerTick = Offset(
                        center.x + radius * cos(angleRad).toFloat(),
                        center.y + radius * sin(angleRad).toFloat()
                    )

                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = innerTick,
                        end = outerTick,
                        strokeWidth = 2.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.apply {
                        // Располагаем цифры чуть дальше от края кольца
                        val textRadius = radius + 25.dp.toPx()
                        val x = center.x + textRadius * cos(angleRad).toFloat()
                        val y = center.y + textRadius * sin(angleRad).toFloat()

                        drawText(
                            (i * 10).toString(),
                            x,
                            y + 7.dp.toPx(), // Центрирование цифр по вертикали с учетом плотности
                            Paint().apply {
                                color = android.graphics.Color.GRAY
                                // Динамический размер шрифта: на больших экранах чуть крупнее, но пропорционально
                                textSize = (if (isLargeScreen) 14.sp else 12.sp).toPx()
                                textAlign = Paint.Align.CENTER
                                isFakeBoldText = false // Убираем жирность для эстетики
                                isAntiAlias = true
                            }
                        )
                    }
                }

                val sweepAngle = if (viewModel.isRunning) {
                    (viewModel.timeLeft / (120f * 60f) * 360f)
                } else angle

                drawArc(
                    color = if (viewModel.isRunning) Color(0xFF4CAF50) else primaryColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Text(
                text = displayTime,
                style = if (isLargeScreen) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = onSurfaceColor
            )
        }

        Spacer(Modifier.height(if (isLargeScreen) 64.dp else 48.dp))

        Button(
            onClick = {
                if (viewModel.isRunning) viewModel.stopTimer()
                else viewModel.startTimer(minutes, controller)
            },
            modifier = Modifier
                .width(if (isLargeScreen) 300.dp else 220.dp)
                .height(if (isLargeScreen) 72.dp else 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.isRunning) errorColor else primaryColor
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (viewModel.isRunning) stopText else startText,
                style = if (isLargeScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
            )
        }
    }
}