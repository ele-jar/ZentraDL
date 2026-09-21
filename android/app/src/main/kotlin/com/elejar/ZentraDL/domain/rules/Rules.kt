package com.elejar.ZentraDL.domain.rules

import com.elejar.ZentraDL.data.local.TaskRecord

/**
 * Automation rules (P6a, S1–S3 on this engine; R1).
 *
 * Conditions/actions serialize as tiny `type:payload` strings (no serializer
 * config, human-readable in Room/JSON export):
 * - conditions: `ext:mp4,mkv` · `minSize:104857600` · `host:example.com` · `nameRe:.*1080p.*`
 * - actions: `moveCat:videos` · `rename:{date} {name}:cleanup`
 */
enum class Trigger { ON_COMPLETE, ON_ADD }

sealed interface RuleCondition {
    data class Ext(val exts: Set<String>) : RuleCondition
    data class MinSize(val bytes: Long) : RuleCondition
    data class Host(val host: String) : RuleCondition
    data class NameRe(val regex: String) : RuleCondition
}

sealed interface RuleAction {
    data class MoveCategory(val categoryId: String) : RuleAction
    data class Rename(val template: String, val cleanup: Boolean) : RuleAction
}

data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: Trigger,
    val conditions: List<RuleCondition>,
    val actions: List<RuleAction>,
)

object RuleCodec {
    fun encodeCondition(c: RuleCondition): String = when (c) {
        is RuleCondition.Ext -> "ext:" + c.exts.joinToString(",")
        is RuleCondition.MinSize -> "minSize:${c.bytes}"
        is RuleCondition.Host -> "host:${c.host.lowercase()}"
        is RuleCondition.NameRe -> "nameRe:${c.regex}"
    }

    fun decodeCondition(s: String): RuleCondition? {
        val type = s.substringBefore(':')
        val payload = s.substringAfter(':', "")
        return when (type) {
            "ext" -> {
                val exts = payload.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                if (exts.isEmpty()) null else RuleCondition.Ext(exts)
            }
            "minSize" -> payload.toLongOrNull()?.let { RuleCondition.MinSize(it) }
            "host" -> if (payload.isNotBlank()) RuleCondition.Host(payload.lowercase()) else null
            "nameRe" -> runCatching { Regex(payload); RuleCondition.NameRe(payload) }.getOrNull()
            else -> null
        }
    }

    fun encodeAction(a: RuleAction): String = when (a) {
        is RuleAction.MoveCategory -> "moveCat:${a.categoryId}"
        is RuleAction.Rename -> "rename:${a.template}:${if (a.cleanup) "cleanup" else "raw"}"
    }

    fun decodeAction(s: String): RuleAction? = when {
        s.startsWith("moveCat:") -> s.removePrefix("moveCat:").takeIf { it.isNotBlank() }?.let { RuleAction.MoveCategory(it) }
        s.startsWith("rename:") -> {
            val body = s.removePrefix("rename:")
            val cleanup = body.endsWith(":cleanup")
            val template = if (cleanup) body.removeSuffix(":cleanup") else body.removeSuffix(":raw")
            template.takeIf { it.isNotBlank() }?.let { RuleAction.Rename(it, cleanup) }
        }
        else -> null
    }
}

object RuleMatch {
    fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/', "").substringBefore('?')
            .substringBefore('@').substringAfter('@').lowercase()

    fun extOf(name: String): String =
        name.substringAfterLast('.', "").substringBefore('?').lowercase()

    fun matches(rec: TaskRecord, cond: RuleCondition): Boolean = when (cond) {
        is RuleCondition.Ext -> extOf(rec.fileName) in cond.exts
        is RuleCondition.MinSize -> rec.totalBytes >= cond.bytes
        is RuleCondition.Host -> hostOf(rec.url) == cond.host ||
            hostOf(rec.url).endsWith(".${cond.host}")
        is RuleCondition.NameRe -> runCatching { Regex(cond.regex).containsMatchIn(rec.fileName) }.getOrDefault(false)
    }

    fun matchesAll(rec: TaskRecord, conds: List<RuleCondition>): Boolean = conds.all { matches(rec, it) }
}
