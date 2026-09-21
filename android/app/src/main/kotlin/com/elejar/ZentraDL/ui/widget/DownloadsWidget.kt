package com.elejar.ZentraDL.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.elejar.ZentraDL.MainActivity
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.TorrentRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Downloads glance widget (X1): active count + speed, pause-all, tap opens app. */
class DownloadsWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    @Composable
    override fun Content() {
        val prefs = currentState<androidx.glance.state.Preferences>()
        val active = prefs[Keys.ACTIVE] ?: 0
        val speed = prefs[Keys.SPEED] ?: 0L
        GlanceTheme {
            Column(
                modifier = androidx.glance.GlanceModifier.fillMaxSize()
                    .padding(12.dp),
            ) {
                Text(
                    text = if (active == 0) "ZentraDL idle" else "$active active",
                    style = TextStyle(color = GlanceTheme.colors.onSurface),
                )
                if (active > 0) {
                    Text(
                        text = humanSpeed(speed),
                        style = TextStyle(color = GlanceTheme.colors.onSurface),
                    )
                    Spacer(modifier = androidx.glance.GlanceModifier.height(4.dp))
                    Row(modifier = androidx.glance.GlanceModifier.fillMaxWidth()) {
                        androidx.glance.Button(
                            text = "Pause all",
                            onClick = actionRunCallback<PauseAllCallback>(),
                        )
                    }
                } else {
                    Spacer(modifier = androidx.glance.GlanceModifier.defaultWeight())
                    androidx.glance.Button(
                        text = "Open",
                        onClick = actionStartActivity<MainActivity>(),
                    )
                }
            }
        }
    }

    object Keys {
        val ACTIVE = androidx.glance.state.intPreferencesKey("w_active")
        val SPEED = androidx.glance.state.longPreferencesKey("w_speed")
    }

    companion object {
        fun humanSpeed(bps: Long): String = if (bps <= 0) {
            "—"
        } else {
            val mb = bps / 1_048_576.0
            if (mb >= 1) "%.1f MB/s".format(mb) else "%d KB/s".format(bps / 1024)
        }

        suspend fun push(ctx: Context, active: Int, speedBps: Long) {
            val manager = GlanceAppWidgetManager(ctx)
            manager.getGlanceIds(DownloadsWidget::class.java).forEach { id ->
                updateAppWidgetState(ctx, id) {
                    it[Keys.ACTIVE] = active
                    it[Keys.SPEED] = speedBps
                }
                DownloadsWidget().update(ctx, id)
            }
        }
    }
}

class DownloadsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DownloadsWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskRepo(): TaskRepository
    fun torrentRepo(): TorrentRepository
}

class PauseAllCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        entry.taskRepo().pauseAll()
        entry.torrentRepo().pauseAllTorrents()
        DownloadsWidget.push(context, 0, 0)
    }
}
