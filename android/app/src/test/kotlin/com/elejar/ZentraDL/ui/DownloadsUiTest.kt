package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.designsystem.components.TaskStatus
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private fun rec(id: String, status: String, name: String = "$id.bin", created: Long = 1000L, total: Long = 100L) =
    TaskRecord(id, "https://x/$name", name, "/d", status, total, created)

class DownloadsUiTest {

    @Test fun filterAndSearch() {
        val records = listOf(rec("a", "downloading"), rec("b", "completed", "movie.mkv"), rec("c", "failed"))
        val all = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Date)
        assertThat(all.filterIsInstance<DownloadsUi.ListItem.Row>()).hasSize(3)
        val failed = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.Failed, DownloadsUi.SortMode.Date)
        assertThat(failed.filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }).containsExactly("c")
        val q = DownloadsUi.buildList(records, emptyMap(), "movie", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Date)
        assertThat(q.filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }).containsExactly("b")
    }

    @Test fun categoryFilter() {
        val records = listOf(
            rec("a", "completed", "movie.mkv").copy(categoryId = "videos"),
            rec("b", "completed", "song.mp3").copy(categoryId = "music"),
        )
        val videos = DownloadsUi.buildList(
            records, emptyMap(), "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Date,
            categoryId = "videos",
        ).filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }
        assertThat(videos).containsExactly("a")
        val all = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Date)
        assertThat(all.filterIsInstance<DownloadsUi.ListItem.Row>()).hasSize(2)
    }

    @Test fun vaultHiddenExceptUnderVaultFilter() {
        val records = listOf(
            rec("a", "completed"),
            rec("b", "completed").copy(vaulted = true),
        )
        val all = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Date)
            .filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }
        assertThat(all).containsExactly("a")
        val vault = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.Vault, DownloadsUi.SortMode.Date)
            .filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }
        assertThat(vault).containsExactly("b")
    }

    @Test fun activeFirstThenDayBuckets() {
        val now = 1_750_000_000_000L
        val records = listOf(
            rec("old", "completed", created = now - 30 * 86_400_000L),
            rec("run", "downloading", created = now - 30 * 86_400_000L),
            rec("new", "completed", created = now),
        )
        val items = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Date, now)
        val headers = items.filterIsInstance<DownloadsUi.ListItem.Header>().map { it.title }
        assertThat(headers).containsExactly("Active", "Today", "Earlier").inOrder()
        val firstRow = (items[1] as DownloadsUi.ListItem.Row).row
        assertThat(firstRow.record.id).isEqualTo("run")
    }

    @Test fun sortNameAndProgress() {
        val records = listOf(rec("b", "completed"), rec("a", "completed"))
        val byName = DownloadsUi.buildList(records, emptyMap(), "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Name)
            .filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }
        assertThat(byName).containsExactly("a", "b").inOrder()
        val prog = mapOf("a" to DownloadProgress(10, 100, 5), "b" to DownloadProgress(90, 100, 5))
        val recs = listOf(rec("a", "downloading"), rec("b", "downloading"))
        val byProg = DownloadsUi.buildList(recs, prog, "", DownloadsUi.StatusFilter.All, DownloadsUi.SortMode.Progress)
            .filterIsInstance<DownloadsUi.ListItem.Row>().map { it.row.record.id }
        assertThat(byProg).containsExactly("b", "a").inOrder()
    }

    @Test fun metaLines() {
        val r = rec("a", "downloading")
        val m = DownloadsUi.metaFor(r, DownloadProgress(512, 2048, 1024))
        assertThat(m).contains("512 B of 2.0 KB")
        val done = DownloadsUi.metaFor(rec("a", "completed", total = 2048), null)
        assertThat(done).contains("2.0 KB")
        assertThat(DownloadsUi.mapStatus("bogus")).isEqualTo(TaskStatus.Failed)
    }
}
