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

@Composable
fun ReligiousBadge(
    gregorianDate: LocalDate,
    contentColor: Color,
    hijriDate: HijriData? = null,
    modifier: Modifier = Modifier
) {
    var religiousDayName by remember { mutableStateOf<Int?>(null) }

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
                text = stringResource(it).uppercase(),
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
