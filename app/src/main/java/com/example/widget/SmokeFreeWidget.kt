package com.example.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity

class SmokeFreeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val sharedPrefs = context.getSharedPreferences("smokefree_widget_prefs", Context.MODE_PRIVATE)
            val quitDate = sharedPrefs.getLong("quit_date", 0L)

            val daysCount = if (quitDate > 0L) {
                val diff = System.currentTimeMillis() - quitDate
                if (diff > 0) (diff / (1000 * 60 * 60 * 24)).toInt() else 0
            } else {
                0
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF2D2251)))
                    .cornerRadius(16.dp)
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SMOKE-FREE JOURNEY",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Text(
                    text = "$daysCount ${if (daysCount == 1) "DAY" else "DAYS"}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(vertical = 4.dp)
                )
                
                Text(
                    text = "Keep up the great work!",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF4ECBA0)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
