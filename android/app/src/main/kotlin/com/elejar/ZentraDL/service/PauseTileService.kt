package com.elejar.ZentraDL.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.TorrentRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick tile (X1): pause everything, or resume when idle. */
@AndroidEntryPoint
class PauseTileService : TileService() {

    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var trepo: TorrentRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            scope.launch {
                if (repo.hasRunning() || trepo.hasActive()) {
                    repo.pauseAll()
                    trepo.pauseAllTorrents()
                } else {
                    repo.resumeAll()
                    trepo.resumeAllTorrents()
                }
                updateState()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { updateState() }
    }

    private fun updateState() {
        val tile = qsTile ?: return
        tile.state = if (repo.hasRunning() || trepo.hasActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
