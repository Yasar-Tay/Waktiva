package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.LocalBackgroundGradient
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The small sheet a prayer's badge opens once its time has come: the prayer, its time, a line on
 * where it stands and a "Prayed" button that logs it. A prayer already logged offers to take the
 * mark back instead. The sheet slides away before the choice is saved, so the badge's glow plays
 * in view.
 *
 * [day] is the day the prayer belongs to and [nextDay] the one after, for when Isha's time ends.
 * [weather], when known, is the weather in the prayer's hour, shown under its time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerLogSheet(
    type: PrayerType,
    day: PrayerDay,
    nextDay: PrayerDay?,
    now: LocalDateTime,
    isPrayed: Boolean,
    onPrayedChange: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
    onDismiss: () -> Unit,
    weather: String? = null
) {
    val glass = LocalGlassTheme.current
    val sky = LocalBackgroundGradient.current
    val contentColor = glass.contentColor
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val color = type.accentColor
    val name = type.displayName
    val start = day.timings[type]
    val end = remember(type, day, nextDay) { PrayerLog.windowEnd(type, day, nextDay) }
    val isOpen = end == null || now.isBefore(end)

    /** Slides the sheet away, then runs [action]. */
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = SheetShape,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(sky)
                // Deepens the sky a little so the white text reads on any hour's colours.
                .background(Color.Black.copy(alpha = 0.18f))
                .liquidGlass(SheetShape, glass)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 22.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(contentColor.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                PrayerMedallion(type, color, isPrayed)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = contentColor
                    )
                    if (start != null) {
                        val window = if (end != null) {
                            "${start.format(formatter)} – ${end.toLocalTime().format(formatter)}"
                        } else {
                            start.format(formatter)
                        }
                        Text(
                            text = window,
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = contentColor.copy(alpha = 0.65f)
                        )
                    }
                    if (weather != null) {
                        Text(
                            text = weather,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }
                StatusChip(
                    text = stringResource(
                        when {
                            isPrayed -> R.string.prayer_log_status_prayed
                            isOpen -> R.string.prayer_log_status_active
                            else -> R.string.prayer_log_status_passed
                        }
                    ),
                    color = when {
                        isPrayed -> PrayedGold
                        isOpen -> PrayedSeal
                        else -> MissedRed
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = when {
                    isPrayed -> stringResource(R.string.prayer_log_sheet_prayed, name)
                    isOpen -> stringResource(R.string.prayer_log_sheet_active, name)
                    else -> stringResource(R.string.prayer_log_sheet_passed, name)
                },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = contentColor.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(22.dp))

            if (isPrayed) {
                OutlinedButton(
                    onClick = { closeThen { onPrayedChange(false) } },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.prayer_log_unmark), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        closeThen { onPrayedChange(true) }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrayedInk),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(PrayedGold, lerp(PrayedGold, color, 0.45f))),
                                RoundedCornerShape(18.dp)
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.prayer_log_mark),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }

            TextButton(
                onClick = { closeThen(onOpenLog) },
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor.copy(alpha = 0.75f)),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.prayer_log_open), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The prayer's badge from the day circle, ringed in gold once it's prayed. */
@Composable
private fun PrayerMedallion(type: PrayerType, color: Color, isPrayed: Boolean) {
    val ink = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White
    Box(
        modifier = Modifier
            .size(52.dp)
            .then(if (isPrayed) Modifier.border(2.dp, PrayedGold, CircleShape) else Modifier)
            .padding(if (isPrayed) 4.dp else 0.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(type.iconRes),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        ),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Ink on the gold "Prayed" button. */
private val PrayedInk = Color(0xFF3B2A00)

private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
