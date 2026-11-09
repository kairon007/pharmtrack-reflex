package com.pharmtrack.reflex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Dumb building blocks ported from SharedUI/Medical/Components.swift. Zero state,
// every interaction is a callback the screen wires to an Action.

@Composable
fun SectionHeader(
    title: String,
    actionTitle: String? = null,
    action: (() -> Unit)? = null,
    isDark: Boolean = isSystemInDarkTheme()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.heading(isDark)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (actionTitle != null && action != null) {
            Text(
                text = actionTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.primary,
                modifier = Modifier
                    .height(44.dp)
                    .clickable(onClick = action)
                    .padding(horizontal = 4.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun CardSurface(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    title: String,
    icon: SemanticIcon? = null,
    enabled: Boolean = true,
    action: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Theme.primary else Theme.primary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = action),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                AppIcon(icon, size = 20.dp, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: SemanticIcon,
    title: String,
    message: String,
    isDark: Boolean = isSystemInDarkTheme()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.surface(isDark))
            .border(1.dp, Theme.border(isDark), RoundedCornerShape(20.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(icon, size = 32.dp, tint = if (isDark) Theme.slate400 else Theme.slate300)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.heading(isDark),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = Theme.caption(isDark),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FilterChip(
    title: String,
    isSelected: Boolean,
    isDark: Boolean = isSystemInDarkTheme(),
    action: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Theme.primary else Theme.surface(isDark))
            .then(
                if (isSelected) Modifier
                else Modifier.border(1.dp, Theme.border(isDark), RoundedCornerShape(20.dp))
            )
            .clickable(onClick = action)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else Theme.body(isDark)
        )
    }
}

@Composable
fun ProgressBar(
    fraction: Double,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val clamped = fraction.coerceIn(0.0, 1.0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isDark) Theme.borderDark else Theme.slate100)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped.toFloat())
                .clip(RoundedCornerShape(3.dp))
                .background(Theme.primary)
        )
    }
}

@Composable
fun IconCircleButton(
    icon: SemanticIcon,
    tint: Color,
    label: String,
    action: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.1f))
            .clickable(onClick = action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, size = 18.dp, tint = tint)
    }
}

/**
 * The onboarding demo scroll (0.5s to bottom, 2.5s back to top) is presentation
 * choreography, not behavior — Core has no action for it (services.yaml#Scheduler).
 */
@Composable
fun OnboardingAutoScroll(active: Boolean, listState: LazyListState) {
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(500)
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
        delay(2000)
        listState.animateScrollToItem(0)
    }
}
