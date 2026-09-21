package com.elejar.ZentraDL.domain.rules

/** Built-in rule templates (gallery-lite; sentence builder is a P6 gap). */
object RuleTemplates {
    fun videos(): Rule = Rule(
        "", "Sort videos", true, Trigger.ON_COMPLETE,
        listOf(RuleCondition.Ext(setOf("mp4", "mkv", "webm", "avi", "mov"))),
        listOf(RuleAction.MoveCategory("videos")),
    )

    fun music(): Rule = Rule(
        "", "Sort music", true, Trigger.ON_COMPLETE,
        listOf(RuleCondition.Ext(setOf("mp3", "flac", "wav", "ogg", "m4a"))),
        listOf(RuleAction.MoveCategory("music")),
    )

    fun cleanNames(): Rule = Rule(
        "", "Clean filenames", true, Trigger.ON_ADD,
        emptyList(),
        listOf(RuleAction.Rename("{name}", true)),
    )

    fun all(): List<Rule> = listOf(videos(), music(), cleanNames())
}

/** Human-readable rule text (export/import, one block per rule). */
object RuleText {
    fun export(rules: List<Rule>): String = rules.joinToString("\n\n") { r ->
        buildString {
            appendLine("# ${r.name}")
            appendLine("trigger: ${r.trigger.name}")
            r.conditions.forEach { appendLine("when: ${RuleCodec.encodeCondition(it)}") }
            r.actions.forEach { appendLine("do: ${RuleCodec.encodeAction(it)}") }
        }.trimEnd()
    }

    fun parse(text: String, newId: () -> String): List<Rule> {
        val out = mutableListOf<Rule>()
        var name = ""
        var trigger = Trigger.ON_COMPLETE
        val conds = mutableListOf<RuleCondition>()
        val acts = mutableListOf<RuleAction>()
        fun flush() {
            if (acts.isNotEmpty()) {
                out += Rule(newId(), name.ifBlank { "Imported rule" }, true, trigger, conds.toList(), acts.toList())
            }
            name = ""
            trigger = Trigger.ON_COMPLETE
            conds.clear()
            acts.clear()
        }
        text.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("#") -> {
                    if (name.isNotBlank() || conds.isNotEmpty() || acts.isNotEmpty()) flush()
                    name = line.removePrefix("#").trim()
                }
                line.startsWith("trigger:") -> runCatching {
                    trigger = Trigger.valueOf(line.removePrefix("trigger:").trim().uppercase())
                }
                line.startsWith("when:") -> RuleCodec.decodeCondition(line.removePrefix("when:").trim())?.let { conds += it }
                line.startsWith("do:") -> RuleCodec.decodeAction(line.removePrefix("do:").trim())?.let { acts += it }
            }
        }
        flush()
        return out
    }
}
