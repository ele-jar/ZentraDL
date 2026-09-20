package com.elejar.ZentraDL.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowserTabsTest {

    @Test fun open_appendsAndSelects() {
        val (tabs, sel) = BrowserTabs.open(emptyList(), "https://a.test", 1)
        assertThat(tabs.map { it.id }).containsExactly(1L)
        assertThat(sel).isEqualTo(1L)
    }

    @Test fun close_fallsBackLeft() {
        val tabs = listOf(WebTab(1, "a"), WebTab(2, "b"), WebTab(3, "c"))
        val (rest, sel) = BrowserTabs.close(tabs, 2)
        assertThat(rest.map { it.id }).containsExactly(1L, 3L).inOrder()
        assertThat(sel).isEqualTo(1L)
        val (rest2, sel2) = BrowserTabs.close(rest, 1)
        assertThat(sel2).isEqualTo(3L)
        val (rest3, sel3) = BrowserTabs.close(rest2, 3)
        assertThat(rest3).isEmpty()
        assertThat(sel3).isEqualTo(-1L)
    }

    @Test fun navigate_updatesUrl() {
        val tabs = BrowserTabs.navigate(listOf(WebTab(1, "a")), 1, "https://b.test", "B")
        assertThat(tabs.single().url).isEqualTo("https://b.test")
        assertThat(tabs.single().title).isEqualTo("B")
    }
}
