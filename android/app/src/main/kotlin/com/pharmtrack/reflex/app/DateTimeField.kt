package com.pharmtrack.reflex.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmtrack.reflex.core.Support.Instant
import java.util.Calendar
import java.util.TimeZone

/**
 * The date + time control every form uses. The platform pickers are the Android
 * counterpart of the SwiftUI DatePicker; the value stays an Instant in the machine.
 */
@Composable
fun DateTimeField(
    label: String,
    instant: Instant,
    zone: Instant.Zone,
    isDark: Boolean = isSystemInDarkTheme(),
    onChange: (Instant) -> Unit
) {
    val context = LocalContext.current

    val calendar = remember(instant, zone) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = ((instant.secondsSince1970 + zone.offsetSeconds) * 1000.0).toLong()
        }
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Theme.caption(isDark)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.surface(isDark))
                .border(1.dp, Theme.border(isDark), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateFormatting.mediumDate(instant),
                fontSize = 15.sp,
                color = Theme.heading(isDark),
                modifier = Modifier.clickable {
                    DatePickerDialog(
                        context,
                        { _, pickedYear, pickedMonth, pickedDay ->
                            val days = Instant.daysFromCivil(pickedYear, pickedMonth + 1, pickedDay)
                            val seconds =
                                days * 86_400.0 + hour * 3_600.0 + minute * 60.0 - zone.offsetSeconds
                            onChange(Instant(seconds))
                        },
                        year,
                        month,
                        day
                    ).show()
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = DateFormatting.time(instant),
                fontSize = 15.sp,
                color = Theme.heading(isDark),
                modifier = Modifier.clickable {
                    TimePickerDialog(
                        context,
                        { _, pickedHour, pickedMinute ->
                            val days = Instant.daysFromCivil(year, month + 1, day)
                            val seconds =
                                days * 86_400.0 + pickedHour * 3_600.0 + pickedMinute * 60.0 - zone.offsetSeconds
                            onChange(Instant(seconds))
                        },
                        hour,
                        minute,
                        true
                    ).show()
                }
            )
        }
    }
}
