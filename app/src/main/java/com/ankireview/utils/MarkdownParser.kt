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
     * Convert LaTeX math to Unicode symbols.
     * This works WITHOUT any LaTeX rendering plugin.
     * Handles the math symbols common in Chinese middle/high school math.
     */
    fun renderMath(text: String): String {
        var s = text

        // ── Step 1: extract and convert $...$ and $$...$$ blocks ──────────
        // Block math $$...$$
        s = Regex("""\$\$([\s\S]+?)\$\$""").replace(s) { mr ->
            "\n" + convertLatex(mr.groupValues[1].trim()) + "\n"
        }
        // Inline math $...$
        s = Regex("""\$([^$\n]+?)\$""").replace(s) { mr ->
            convertLatex(mr.groupValues[1])
        }

        // ── Step 2: convert any remaining bare LaTeX commands ─────────────
        // (for markdown files that use LaTeX without $ delimiters)
        s = convertLatex(s)

        return s
    }

    private fun convertLatex(expr: String): String {
        var s = expr

        // Fractions: \frac{a}{b} → a/b
        s = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""").replace(s) { mr ->
            "${mr.groupValues[1]}/${mr.groupValues[2]}"
        }
        // Superscripts: x^{abc} → x^abc,  x^2 → x²
        s = Regex("""\^\\circ""").replace(s, "°")
        s = Regex("""\^\{([^}]+)\}""").replace(s) { mr -> toSuperscript(mr.groupValues[1]) }
        s = Regex("""\^(\d)""").replace(s) { mr -> toSuperscript(mr.groupValues[1]) }
        // Subscripts: x_{abc} → x_abc
        s = Regex("""_\{([^}]+)\}""").replace(s) { mr -> "_${mr.groupValues[1]}" }
        // Square root
        s = Regex("""\\sqrt\{([^}]+)\}""").replace(s) { mr -> "√${mr.groupValues[1]}" }
        s = Regex("""\\sqrt(\d)""").replace(s) { mr -> "√${mr.groupValues[1]}" }

        // Relations & operators
        s = s.replace("\\parallel",   "∥")
        s = s.replace("\\perp",       "⊥")
        s = s.replace("\\angle",      "∠")
        s = s.replace("\\triangle",   "△")
        s = s.replace("\\sim",        "∼")
        s = s.replace("\\cong",       "≅")
        s = s.replace("\\neq",        "≠")
        s = s.replace("\\ne",         "≠")
        s = s.replace("\\leq",        "≤")
        s = s.replace("\\le",         "≤")
        s = s.replace("\\geq",        "≥")
        s = s.replace("\\ge",         "≥")
        s = s.replace("\\approx",     "≈")
        s = s.replace("\\equiv",      "≡")
        s = s.replace("\\times",      "×")
        s = s.replace("\\div",        "÷")
        s = s.replace("\\pm",         "±")
        s = s.replace("\\cdot",       "·")
        s = s.replace("\\circ",       "°")
        s = s.replace("\\degree",     "°")

        // Set operations
        s = s.replace("\\in",         "∈")
        s = s.replace("\\notin",      "∉")
        s = s.replace("\\subset",     "⊂")
        s = s.replace("\\subseteq",   "⊆")
        s = s.replace("\\cup",        "∪")
        s = s.replace("\\cap",        "∩")
        s = s.replace("\\emptyset",   "∅")
        s = s.replace("\\varnothing", "∅")

        // Arrows
        s = s.replace("\\rightarrow",     "→")
        s = s.replace("\\leftarrow",      "←")
        s = s.replace("\\Rightarrow",     "⇒")
        s = s.replace("\\Leftarrow",      "⇐")
        s = s.replace("\\Leftrightarrow", "⇔")
        s = s.replace("\\leftrightarrow", "↔")

        // Greek letters
        s = s.replace("\\alpha",  "α"); s = s.replace("\\Alpha",  "Α")
        s = s.replace("\\beta",   "β"); s = s.replace("\\Beta",   "Β")
        s = s.replace("\\gamma",  "γ"); s = s.replace("\\Gamma",  "Γ")
        s = s.replace("\\delta",  "δ"); s = s.replace("\\Delta",  "Δ")
        s = s.replace("\\epsilon","ε"); s = s.replace("\\varepsilon","ε")
        s = s.replace("\\theta",  "θ"); s = s.replace("\\Theta",  "Θ")
        s = s.replace("\\lambda", "λ"); s = s.replace("\\Lambda", "Λ")
        s = s.replace("\\mu",     "μ")
        s = s.replace("\\pi",     "π"); s = s.replace("\\Pi",     "Π")
        s = s.replace("\\sigma",  "σ"); s = s.replace("\\Sigma",  "Σ")
        s = s.replace("\\phi",    "φ"); s = s.replace("\\Phi",    "Φ")
        s = s.replace("\\omega",  "ω"); s = s.replace("\\Omega",  "Ω")

        // Trig / functions
        s = s.replace("\\sin",   "sin"); s = s.replace("\\cos", "cos")
        s = s.replace("\\tan",   "tan"); s = s.replace("\\log", "log")
        s = s.replace("\\lg",    "lg");  s = s.replace("\\ln",  "ln")
        s = s.replace("\\max",   "max"); s = s.replace("\\min", "min")

        // Misc
        s = s.replace("\\ldots",  "…"); s = s.replace("\\cdots", "⋯")
        s = s.replace("\\infty",  "∞")
        s = s.replace("\\because","∵"); s = s.replace("\\therefore","∴")
        s = s.replace("\\overline",""); // e.g. \overline{AB} → AB (can't do overline in plain text)

        // Remove remaining braces and known commands that have no Unicode equivalent
        s = Regex("""\\[a-zA-Z]+\{([^}]*)\}""").replace(s) { mr -> mr.groupValues[1] }
        s = Regex("""\\[a-zA-Z]+""").replace(s, "")
        s = s.replace("{", "").replace("}", "")

        return s
    }

    private fun toSuperscript(str: String): String {
        val supMap = mapOf('0' to '⁰','1' to '¹','2' to '²','3' to '³','4' to '⁴',
            '5' to '⁵','6' to '⁶','7' to '⁷','8' to '⁸','9' to '⁹',
            'n' to 'ⁿ','+' to '⁺','-' to '⁻','(' to '⁽',')' to '⁾')
        return str.map { supMap[it] ?: it }.joinToString("")
    }
}
