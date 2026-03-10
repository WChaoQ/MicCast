package com.miccast.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miccast.model.ConnectionMethod
import com.miccast.model.ConnectionState

// ─────────────────────────────────────────────────────────────────────────────
//  Connection method tab row (WiFi | USB)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConnectionMethodSelector(
    selected: ConnectionMethod,
    onMethodSelected: (ConnectionMethod) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val options = listOf(
        Triple(ConnectionMethod.WIFI,      Icons.Outlined.Wifi,      "WIFI"),
        Triple(ConnectionMethod.USB,       Icons.Outlined.Usb,       "USB"),
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEachIndexed { index, (method, icon, label) ->
            val isSelected = method == selected
            MethodChip(
                icon = icon,
                label = label,
                selected = isSelected,
                enabled = enabled,
                onClick = { if (enabled) onMethodSelected(method) },
                modifier = Modifier.weight(1f)
            )
            if (index < options.size - 1) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun MethodChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f)
        else Color.Transparent,
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.7f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        label = "chipContent"
    )
    val borderColor by animateColorAsState(
        if (selected) Color.Transparent
        else MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.5f else 0.2f),
        label = "chipBorder"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Connection diagram (mic → dots → method icon → dots → monitor)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConnectionDiagram(
    connectionState: ConnectionState,
    connectionMethod: ConnectionMethod,
    modifier: Modifier = Modifier
) {
    val connected = connectionState == ConnectionState.CONNECTED

    // Solid color that deepens when connected
    val dotColor by animateColorAsState(
        targetValue = if (connected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        animationSpec = tween(500),
        label = "dotColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (connected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        animationSpec = tween(500),
        label = "iconTint"
    )

    val middleIcon = if (connected) {
        when (connectionMethod) {
            ConnectionMethod.WIFI -> Icons.Outlined.Wifi
            ConnectionMethod.USB -> Icons.Outlined.Usb
        }
    } else {
        Icons.Outlined.LinkOff
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Microphone icon (left)
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp)
            )

            // Centered dots area between Mic and Icon
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        if (it < 2) Spacer(Modifier.width(6.dp))
                    }
                }
            }

            // Center WIFI / USB / LinkOff icon
            Icon(
                imageVector = middleIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )

            // Centered dots area between Icon and Monitor
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        if (it < 2) Spacer(Modifier.width(6.dp))
                    }
                }
            }

            // Monitor icon (right)
            Icon(
                imageVector = Icons.Outlined.DesktopWindows,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  VU Meter bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VuMeter(
    level: Float,       // 0..1
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "vuLevel"
    )

    val barColor by animateColorAsState(
        targetValue = when {
            animatedLevel > 0.8f -> MaterialTheme.colorScheme.error
            animatedLevel > 0.5f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "vuColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedLevel)
                .clip(RoundedCornerShape(50))
                .background(barColor)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Inline Error Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ErrorCard(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "连接出现问题",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = message ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                            letterSpacing = 0.25.sp
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
