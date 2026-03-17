package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.domain.model.HijriData
import com.ybugmobile.waktiva.domain.provider.ReligiousDaysProvider
import java.time.LocalDate
import java.util.Locale

/**
 * A contextual badge that appears when the selected date coincides with a religious holiday or significant day.
 *
 * @param gregorianDate The date to check for religious significance.
 * @param contentColor Base color for the text.
 * @param hijriDate Optional Hijri data for more accurate identification.
 * @param modifier Root layout modifier.
 */
@Composable
fun ReligiousBadge(
    gregorianDate: LocalDate,
    contentColor: Color,
    hijriDate: HijriData? = null,
    modifier: Modifier = Modifier
) {
    var religiousDayName by remember { mutableStateOf<Int?>(null) }

    // Check for religious significance whenever the date changes
    LaunchedEffect(gregorianDate, hijriDate) {
        religiousDayName = ReligiousDaysProvider.getReligiousDay(gregorianDate)?.nameResId
    }

    AnimatedVisibility(
        visible = religiousDayName != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        religiousDayName?.let {
            Text(
                text = stringResource(it).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
