package com.ybugmobile.waktiva.ui.settings.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme

/** Prayers that have an adhan; Sunrise is excluded. */
private val ADHAN_PRAYERS = listOf(
    PrayerType.FAJR,
    PrayerType.DHUHR,
    PrayerType.ASR,
    PrayerType.MAGHRIB,
    PrayerType.ISHA
)

/**
 * Lets the user pick which prayers play the adhan audio. Each prayer is a
 * toggleable pill; unselected prayers fall back to the silent notification.
 */
@Composable
fun AdhanPrayerSelectionItem(
    title: String,
    subtitle: String,
    disabledPrayers: Set<PrayerType>,
    onPrayerToggle: (PrayerType, Boolean) -> Unit
) {
    val glassTheme = LocalGlassTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(glassTheme.contentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = glassTheme.contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = glassTheme.contentColor
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = glassTheme.secondaryContentColor,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AdhanPrayerPills(
            disabledPrayers = disabledPrayers,
            onPrayerToggle = onPrayerToggle,
            modifier = Modifier.padding(start = 60.dp)
        )
    }
}

/**
 * Wrapping row of toggleable pills, one per adhan prayer. [accentColor] tints
 * the selected pills so each screen can match its own switch styling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdhanPrayerPills(
    disabledPrayers: Set<PrayerType>,
    onPrayerToggle: (PrayerType, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF81C784)
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ADHAN_PRAYERS.forEach { type ->
            PrayerPill(
                label = type.displayName,
                selected = type !in disabledPrayers,
                accentColor = accentColor,
                onToggle = { onPrayerToggle(type, it) }
            )
        }
    }
}

@Composable
private fun PrayerPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onToggle: (Boolean) -> Unit
) {
    val glassTheme = LocalGlassTheme.current
    val shape = RoundedCornerShape(50)

    Surface(
        shape = shape,
        color = if (selected) accentColor.copy(alpha = 0.35f) else glassTheme.contentColor.copy(alpha = 0.06f),
        border = BorderStroke(
            1.dp,
            if (selected) accentColor.copy(alpha = 0.6f) else glassTheme.contentColor.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .clip(shape)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = onToggle
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = glassTheme.contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) glassTheme.contentColor else glassTheme.contentColor.copy(alpha = 0.6f)
                )
            )
        }
    }
}
