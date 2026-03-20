package com.ankireview.utils

data class ParsedCard(val question: String, val analysis: List<AnalysisSection>)
data class AnalysisSection(val title: String, val body: String, val type: SectionType)
enum class SectionType { NORMAL, KNOWLEDGE_POINTS, SUMMARY, TAGS }

object MarkdownParser {
    private val OBSIDIAN_IMG = Regex("""!\[\[([^\]]+)]]""")

    fun parse(raw: String): ParsedCard {
        val body   = raw.replace(Regex("""^---[\s\S]*?---\n?"""), "").trim()
        val MARKER = "## 题目 (Question)"
        val idx    = body.indexOf(MARKER)
        val question: String
        val analysisRaw: String
        if (idx != -1) {
            val after = body.substring(idx + MARKER.length)
            val h2 = Regex("""\n##\s""").find(after)
            question    = h2?.let { after.substring(0, it.range.first).trim() } ?: after.trim()
            analysisRaw = h2?.let { after.substring(it.range.first).trim() }    ?: ""
        } else {
            val h2 = Regex("""\n##\s""").find(body)
            question    = h2?.let { body.substring(0, it.range.first).trim() } ?: body
            analysisRaw = h2?.let { body.substring(it.range.first).trim() }    ?: ""
        }
        return ParsedCard(question, parseAnalysis(analysisRaw))
    }

    private fun parseAnalysis(raw: String): List<AnalysisSection> {
        if (raw.isBlank()) return emptyList()
        return raw.split(Regex("""\n##\s+""")).map { it.trim() }.filter { it.isNotBlank() }
            .mapNotNull { block ->
                val nl    = block.indexOf('\n')
                val title = (if (nl != -1) block.substring(0, nl) else block).trim()
                val body  = (if (nl != -1) block.substring(nl) else "").trim()
                val type  = when {
                    title.contains("知识点")                              -> SectionType.KNOWLEDGE_POINTS
                    title.contains("总结") || title.contains("Summary")  -> SectionType.SUMMARY
                    title.contains("标签") || title.contains("Tags")     -> SectionType.TAGS
                    else                                                   -> SectionType.NORMAL
                }
                if (type == SectionType.TAGS) null else AnalysisSection(title, body, type)
            }
    }

    fun extractObsidianImages(text: String): List<String> =
        OBSIDIAN_IMG.findAll(text).map { it.groupValues[1] }.toList()

    fun normalizeImages(text: String, urlMap: Map<String, String>): String =
        OBSIDIAN_IMG.replace(text) { mr ->
            val name = mr.groupValues[1]
            val url  = urlMap[name] ?: urlMap[name.substringAfterLast('/')]
            if (url != null) "![$name]($url)" else "> ⚠️ 图片未找到：$name"
        }

    /**
     * Pre-process LaTeX so Markwon can render it correctly.
     * Converts $...$ to \(...\) and $$...$$ to \[...\]
     * This is more reliable than relying on JLatexMathPlugin inline detection.
     */
    fun preprocessLatex(text: String): String {
        var result = text
        // Block math: $$...$$ -> \[...\]  (must do before inline)
        result = Regex("""\$\$([\s\S]+?)\$\$""").replace(result) { mr ->
            "\\[${mr.groupValues[1]}\\]"
        }
        // Inline math: $...$ -> \(...\)
        // Avoid replacing already-converted \[...\] and currency like $10
        result = Regex("""\$([^$\n]+?)\$""").replace(result) { mr ->
            val inner = mr.groupValues[1].trim()
            // Skip if it looks like currency (starts with digit or is very short)
            if (inner.isEmpty()) mr.value
            else "\\(${mr.groupValues[1]}\\)"
        }
        return result
    }
}
