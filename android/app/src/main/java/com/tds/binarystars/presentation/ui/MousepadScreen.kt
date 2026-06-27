package com.tds.binarystars.presentation.ui

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tds.binarystars.presentation.ui.theme.*
import com.tds.binarystars.presentation.viewmodel.BluetoothViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MousepadScreen(
    isDark: Boolean,
    viewModel: BluetoothViewModel,
    onBackToOptions: () -> Unit
) {
    var sensitivity by remember { mutableStateOf(1.5f) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    // Make sure we release click states on exit
    DisposableEffect(Unit) {
        onDispose {
            if (leftPressed) viewModel.sendMouseSignal("up", 0, 0, "left")
            if (rightPressed) viewModel.sendMouseSignal("up", 0, 0, "right")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top row: Header & Back
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
                    text = "Trackpad Controller",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextDarkPrimary else TextLightPrimary
                )
                Text(
                    text = "Relative Mouse Simulation",
                    fontSize = 11.sp,
                    color = PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Sensitivity Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isDark) DarkCardBg else LightCardBg,
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) DarkBorder else LightBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Trackpad Sensitivity",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextDarkPrimary else TextLightPrimary
                )
                Text(
                    text = String.format("%.1fx", sensitivity),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0.5f..4.0f,
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryBlue,
                    activeTrackColor = PrimaryBlue
                ),
                modifier = Modifier.height(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Trackpad Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF101010) else Color(0xFFFCFCFC))
                .border(
                    width = 2.dp,
                    color = if (isDark) DarkBorder else LightBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { /* Drag started */ },
                        onDragEnd = { /* Drag ended */ },
                        onDragCancel = { /* Drag cancelled */ },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = (dragAmount.x * sensitivity).toInt()
                            val dy = (dragAmount.y * sensitivity).toInt()
                            if (dx != 0 || dy != 0) {
                                viewModel.sendMouseSignal("move", dx, dy)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Visual indicators for the trackpad area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "TRACKPAD",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = (if (isDark) TextDarkSecondary else TextLightSecondary).copy(alpha = 0.3f),
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Slide finger to move mouse. Double tap clicks.",
                    fontSize = 11.sp,
                    color = (if (isDark) TextDarkSecondary else TextLightSecondary).copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Click Buttons (Left / Right Click)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Click Button
            val leftBg = if (leftPressed) PrimaryBlue else (if (isDark) DarkCardBg else LightCardBg)
            val leftBorder = if (leftPressed) PrimaryBlue else (if (isDark) DarkBorder else LightBorder)
            val leftText = if (leftPressed) Color.White else (if (isDark) TextDarkPrimary else TextLightPrimary)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(leftBg)
                    .border(1.dp, leftBorder, RoundedCornerShape(8.dp))
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN -> {
                                leftPressed = true
                                viewModel.sendMouseSignal("down", 0, 0, "left")
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                leftPressed = false
                                viewModel.sendMouseSignal("up", 0, 0, "left")
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LEFT CLICK",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = leftText
                )
            }

            // Right Click Button
            val rightBg = if (rightPressed) PrimaryBlue else (if (isDark) DarkCardBg else LightCardBg)
            val rightBorder = if (rightPressed) PrimaryBlue else (if (isDark) DarkBorder else LightBorder)
            val rightText = if (rightPressed) Color.White else (if (isDark) TextDarkPrimary else TextLightPrimary)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(rightBg)
                    .border(1.dp, rightBorder, RoundedCornerShape(8.dp))
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN -> {
                                rightPressed = true
                                viewModel.sendMouseSignal("down", 0, 0, "right")
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                rightPressed = false
                                viewModel.sendMouseSignal("up", 0, 0, "right")
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RIGHT CLICK",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = rightText
                )
            }
        }
    }
}
