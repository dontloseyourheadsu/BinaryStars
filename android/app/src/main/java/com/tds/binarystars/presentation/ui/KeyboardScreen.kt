package com.tds.binarystars.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tds.binarystars.presentation.ui.theme.*
import com.tds.binarystars.presentation.viewmodel.BluetoothViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    isDark: Boolean,
    viewModel: BluetoothViewModel,
    onBackToOptions: () -> Unit
) {
    // Modifier Toggle States
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    var metaActive by remember { mutableStateOf(false) }

    // Tab switching (0: Modifiers, 1: Navigation/Editing, 2: D-Pad / Arrows)
    var selectedTab by remember { mutableStateOf(0) }

    // Input state - keep a single space " " as placeholder to detect backspaces
    var textInputState by remember { mutableStateOf(TextFieldValue(" ")) }

    // Clean up modifiers on exit
    DisposableEffect(Unit) {
        onDispose {
            if (ctrlActive) viewModel.sendKeyboardKey("up", "ctrl")
            if (altActive) viewModel.sendKeyboardKey("up", "alt")
            if (shiftActive) viewModel.sendKeyboardKey("up", "shift")
            if (metaActive) viewModel.sendKeyboardKey("up", "meta")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top row: Header & Clear
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
                    text = "Keyboard Controller",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextDarkPrimary else TextLightPrimary
                )
                Text(
                    text = "Linux Input Receiver Active",
                    fontSize = 11.sp,
                    color = PrimaryBlue
                )
            }

            // Clear all modifiers button
            TextButton(
                onClick = {
                    if (ctrlActive) { viewModel.sendKeyboardKey("up", "ctrl"); ctrlActive = false }
                    if (altActive) { viewModel.sendKeyboardKey("up", "alt"); altActive = false }
                    if (shiftActive) { viewModel.sendKeyboardKey("up", "shift"); shiftActive = false }
                    if (metaActive) { viewModel.sendKeyboardKey("up", "meta"); metaActive = false }
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "CLEAR ALL",
                    fontSize = 11.sp,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Modifiers Display Bar (glowing status cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusColor = if (isDark) DarkBorder else LightBorder
            ModifierStatusCard(isDark, "CTRL", ctrlActive, Modifier.weight(1f))
            ModifierStatusCard(isDark, "ALT", altActive, Modifier.weight(1f))
            ModifierStatusCard(isDark, "SHIFT", shiftActive, Modifier.weight(1f))
            ModifierStatusCard(isDark, "META", metaActive, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Horizontal Custom Tab Switcher
        Row(
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
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardTabButton("Modifiers", selectedTab == 0, isDark, Modifier.weight(1f)) { selectedTab = 0 }
            KeyboardTabButton("Editing", selectedTab == 1, isDark, Modifier.weight(1f)) { selectedTab = 1 }
            KeyboardTabButton("D-Pad / Arrows", selectedTab == 2, isDark, Modifier.weight(1f)) { selectedTab = 2 }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Panel Contents Card (with animated transition)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = selectedTab, label = "tabContent") { tab ->
                when (tab) {
                    0 -> ModifiersPanel(
                        isDark = isDark,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        shiftActive = shiftActive,
                        metaActive = metaActive,
                        onCtrlToggle = {
                            ctrlActive = !ctrlActive
                            viewModel.sendKeyboardKey(if (ctrlActive) "down" else "up", "ctrl")
                        },
                        onAltToggle = {
                            altActive = !altActive
                            viewModel.sendKeyboardKey(if (altActive) "down" else "up", "alt")
                        },
                        onShiftToggle = {
                            shiftActive = !shiftActive
                            viewModel.sendKeyboardKey(if (shiftActive) "down" else "up", "shift")
                        },
                        onMetaToggle = {
                            metaActive = !metaActive
                            viewModel.sendKeyboardKey(if (metaActive) "down" else "up", "meta")
                        },
                        onKeyClick = { key ->
                            viewModel.sendKeyboardKey("click", key)
                        }
                    )
                    1 -> NavigationPanel(
                        isDark = isDark,
                        onKeyClick = { key ->
                            viewModel.sendKeyboardKey("click", key)
                        }
                    )
                    2 -> DpadPanel(
                        isDark = isDark,
                        onKeyClick = { key ->
                            viewModel.sendKeyboardKey("click", key)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Hidden Text Input Area for Pop-up Keyboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDark) DarkCardBg else LightCardBg)
                .border(
                    width = 1.dp,
                    color = PrimaryBlue.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Invisible BasicTextField that triggers the keyboard
            TextField(
                value = textInputState,
                onValueChange = { newValue ->
                    val newText = newValue.text
                    val oldText = textInputState.text

                    if (newText.length > oldText.length) {
                        // Character typed
                        val charTyped = newText.substring(oldText.length - 1)
                        if (charTyped == "\n") {
                            viewModel.sendKeyboardKey("click", "enter")
                        } else {
                            viewModel.sendKeyboardKey("char", charTyped)
                        }
                    } else if (newText.length < oldText.length) {
                        // Backspace typed
                        viewModel.sendKeyboardKey("click", "backspace")
                    }

                    // Reset buffer to keep " " (space) so backspaces/typing can always be detected
                    textInputState = TextFieldValue(" ")
                },
                placeholder = {
                    Text(
                        text = "⌨️ Tap here to open keyboard and type...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) TextDarkSecondary else TextLightSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Transparent,
                    unfocusedTextColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.None
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ModifierStatusCard(
    isDark: Boolean,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = when {
        active -> PrimaryBlue
        isDark -> DarkCardBg
        else -> LightCardBg
    }
    val border = when {
        active -> PrimaryBlue
        isDark -> DarkBorder
        else -> LightBorder
    }
    val textColor = when {
        active -> Color.White
        isDark -> TextDarkPrimary
        else -> TextLightPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun KeyboardTabButton(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) PrimaryBlue else Color.Transparent
    val textColor = if (selected) Color.White else (if (isDark) TextDarkSecondary else TextLightSecondary)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// Sub-Panel 1: Modifiers (Ctrl, Alt, Shift, Meta)
@Composable
fun ModifiersPanel(
    isDark: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    shiftActive: Boolean,
    metaActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onShiftToggle: () -> Unit,
    onMetaToggle: () -> Unit,
    onKeyClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toggle Keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToggleButton(isDark, "CTRL", ctrlActive, Modifier.weight(1f), onCtrlToggle)
            ToggleButton(isDark, "ALT", altActive, Modifier.weight(1f), onAltToggle)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToggleButton(isDark, "SHIFT", shiftActive, Modifier.weight(1f), onShiftToggle)
            ToggleButton(isDark, "META/SUPER", metaActive, Modifier.weight(1f), onMetaToggle)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Command Keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandButton(isDark, "ESC", Modifier.weight(1f)) { onKeyClick("escape") }
            CommandButton(isDark, "TAB", Modifier.weight(1f)) { onKeyClick("tab") }
            CommandButton(isDark, "SPACE", Modifier.weight(1f)) { onKeyClick("space") }
        }
    }
}

// Sub-Panel 2: Navigation & Editing
@Composable
fun NavigationPanel(
    isDark: Boolean,
    onKeyClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandButton(isDark, "HOME", Modifier.weight(1f)) { onKeyClick("home") }
            CommandButton(isDark, "END", Modifier.weight(1f)) { onKeyClick("end") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandButton(isDark, "PG UP", Modifier.weight(1f)) { onKeyClick("pgup") }
            CommandButton(isDark, "PG DN", Modifier.weight(1f)) { onKeyClick("pgdn") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandButton(isDark, "INSERT", Modifier.weight(1f)) { onKeyClick("ins") }
            CommandButton(isDark, "DELETE", Modifier.weight(1f)) { onKeyClick("del") }
        }
    }
}

// Sub-Panel 3: Circular D-Pad Controller
@Composable
fun DpadPanel(
    isDark: Boolean,
    onKeyClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(
                color = if (isDark) DarkCardBg else LightCardBg,
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = if (isDark) DarkBorder else LightBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // D-Pad grid
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Up Button
            DpadButton(isDark, "▲") { onKeyClick("up") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Button
                DpadButton(isDark, "◀") { onKeyClick("left") }

                // Center Select / Enter Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(PrimaryBlue, CircleShape)
                        .clickable { onKeyClick("enter") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Right Button
                DpadButton(isDark, "▶") { onKeyClick("right") }
            }

            // Down Button
            DpadButton(isDark, "▼") { onKeyClick("down") }
        }
    }
}

@Composable
fun ToggleButton(
    isDark: Boolean,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (active) PrimaryBlue.copy(alpha = 0.2f) else Color.Transparent
    val border = if (active) PrimaryBlue else (if (isDark) DarkBorder else LightBorder)
    val textColor = if (active) PrimaryBlue else (if (isDark) TextDarkPrimary else TextLightPrimary)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun CommandButton(
    isDark: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) DarkCardBg else LightCardBg)
            .border(
                width = 1.dp,
                color = if (isDark) DarkBorder else LightBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) TextDarkPrimary else TextLightPrimary
        )
    }
}

@Composable
fun DpadButton(
    isDark: Boolean,
    symbol: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (isDark) DarkCardBg else LightCardBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = if (isDark) TextDarkPrimary else TextLightPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

