package com.tds.binarystars.presentation.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tds.binarystars.presentation.ui.theme.*
import com.tds.binarystars.presentation.viewmodel.BluetoothViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabletScreen(
    isDark: Boolean,
    viewModel: BluetoothViewModel,
    onBackToOptions: () -> Unit
) {
    val ratioPair by viewModel.tabletRatio.collectAsState()

    // Request current mapping ratio from Linux host on startup
    LaunchedEffect(Unit) {
        viewModel.requestTabletRatio()
    }

    val widthRatio = ratioPair?.first ?: 1920
    val heightRatio = ratioPair?.second ?: 1080
    val aspectRatio = widthRatio.toFloat() / heightRatio.toFloat()

    val strokePoints = remember { mutableStateListOf<Offset>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row: Info & Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackToOptions,
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        1.dp,
                        if (isDark) DarkBorder else LightBorder,
                        RoundedCornerShape(6.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to dashboard",
                    tint = if (isDark) TextDarkPrimary else TextLightPrimary
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "Drawing Tablet Mode",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextDarkPrimary else TextLightPrimary
                )
                Text(
                    text = "Mapped Screen: $widthRatio x $heightRatio",
                    fontSize = 11.sp,
                    color = if (isDark) TextDarkSecondary else TextLightSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tablet Canvas drawing container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF101010) else Color(0xFFFCFCFC))
                    .border(
                        width = 2.dp,
                        color = PrimaryBlue,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                val canvasWidth = constraints.maxWidth.toFloat()
                val canvasHeight = constraints.maxHeight.toFloat()

                // Draw Grid Background & Drawing Path Trace
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { motionEvent ->
                            val rawX = motionEvent.x
                            val rawY = motionEvent.y
                            val normX = (rawX / canvasWidth).coerceIn(0f, 1f)
                            val normY = (rawY / canvasHeight).coerceIn(0f, 1f)

                            when (motionEvent.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    strokePoints.clear()
                                    strokePoints.add(Offset(rawX, rawY))
                                    viewModel.sendTabletSignal("down", normX, normY)
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    strokePoints.add(Offset(rawX, rawY))
                                    viewModel.sendTabletSignal("move", normX, normY)
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    strokePoints.add(Offset(rawX, rawY))
                                    viewModel.sendTabletSignal("up", normX, normY)
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    // Draw a subtle coordinate grid
                    val gridColor = if (isDark) Color(0xFF222222) else Color(0xFFECECEC)
                    val cols = 8
                    val rows = 8
                    for (i in 1 until cols) {
                        val x = (size.width / cols) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    for (i in 1 until rows) {
                        val y = (size.height / rows) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draw the visual stroke feedback trail
                    if (strokePoints.size > 1) {
                        val path = Path().apply {
                            val first = strokePoints.first()
                            moveTo(first.x, first.y)
                            for (i in 1 until strokePoints.size) {
                                val pt = strokePoints[i]
                                lineTo(pt.x, pt.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = PrimaryBlue,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instruction Text at bottom
        Text(
            text = "Drag your finger across the container to paint. The visual pointer maps exactly to your Linux screen.",
            fontSize = 11.sp,
            color = if (isDark) TextDarkSecondary else TextLightSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
