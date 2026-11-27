package io.github.robin19666.zapretmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

/** Блок разметки в описании обновления. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val number: Int? = null) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data object Rule : MdBlock
}

/**
 * Мини-разметка для текстов релизов: заголовки, списки, **жирный**, *курсив*,
 * `код`, [ссылки](https://…), блоки кода и горизонтальные линии.
 * GitHub-разметка в описании обновления больше не показывается «звёздочками».
 */
object Markdown {
    fun parse(text: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val paragraph = StringBuilder()
        val code = ArrayList<String>()
        var inCode = false

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                blocks += MdBlock.Paragraph(paragraph.toString().trim())
                paragraph.clear()
            }
        }

        text.replace("\r\n", "\n").lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (inCode) {
                if (line.trimStart().startsWith("```")) {
                    blocks += MdBlock.Code(code.toList())
                    code.clear()
                    inCode = false
                } else {
                    code += line
                }
                return@forEach
            }
            when {
                line.trimStart().startsWith("```") -> {
                    flushParagraph()
                    inCode = true
                }
                line.isBlank() -> flushParagraph()
                Regex("^#{1,6}\\s+.*").matches(line) -> {
                    flushParagraph()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += MdBlock.Heading(level, line.dropWhile { it == '#' }.trim())
                }
                Regex("^\\s*[-*+•]\\s+.*").matches(line) -> {
                    flushParagraph()
                    blocks += MdBlock.Bullet(line.trimStart().dropWhile { it in "-*+•" }.trim())
                }
                Regex("^\\s*\\d+[.)]\\s+.*").matches(line) -> {
                    flushParagraph()
                    val number = Regex("^\\s*(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                    blocks += MdBlock.Bullet(
                        line.trimStart().replaceFirst(Regex("^\\d+[.)]\\s*"), "").trim(),
                        number,
                    )
                }
                Regex("^\\s*([-*_])\\1{2,}\\s*$").matches(line) -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.toList())
        flushParagraph()
        return blocks
    }

    /** Инлайн-разметка одного абзаца/пункта. */
    @Composable
    fun inline(text: String): AnnotatedString {
        val linkColor = ZmColors.accent
        val codeColor = ZmColors.textPrimary
        return buildAnnotatedString {
            var index = 0
            while (index < text.length) {
                val rest = text.substring(index)
                when {
                    rest.startsWith("`") -> {
                        val end = text.indexOf('`', index + 1)
                        if (end > index) {
                            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor))
                            append(text.substring(index + 1, end))
                            pop()
                            index = end + 1
                        } else {
                            append('`')
                            index++
                        }
                    }
                    rest.startsWith("**") -> {
                        val end = text.indexOf("**", index + 2)
                        if (end > index) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                            append(text.substring(index + 2, end))
                            pop()
                            index = end + 2
                        } else {
                            append("**")
                            index += 2
                        }
                    }
                    rest.startsWith("*") -> {
                        val end = text.indexOf('*', index + 1)
                        if (end > index) {
                            pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                            append(text.substring(index + 1, end))
                            pop()
                            index = end + 1
                        } else {
                            append('*')
                            index++
                        }
                    }
                    rest.startsWith("[") -> {
                        val closeLabel = text.indexOf("](", index)
                        val closeUrl = if (closeLabel > 0) text.indexOf(')', closeLabel + 2) else -1
                        if (closeLabel > 0 && closeUrl > closeLabel) {
                            val label = text.substring(index + 1, closeLabel)
                            val url = text.substring(closeLabel + 2, closeUrl)
                            withLink(LinkAnnotation.Url(url)) {
                                pushStyle(
                                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                                )
                                append(label)
                                pop()
                            }
                            index = closeUrl + 1
                        } else {
                            append('[')
                            index++
                        }
                    }
                    else -> {
                        append(text[index])
                        index++
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = Markdown.parse(text)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    Markdown.inline(block.text),
                    fontSize = if (block.level <= 2) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZmColors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MdBlock.Paragraph -> Text(
                    Markdown.inline(block.text),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = ZmColors.textPrimary,
                )
                is MdBlock.Bullet -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = block.number?.let { "$it." } ?: "•",
                        fontSize = 12.sp,
                        color = ZmColors.accent,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        Markdown.inline(block.text),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ZmColors.textPrimary,
                    )
                }
                is MdBlock.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZmColors.surfaceHigh, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    block.lines.forEach { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ZmColors.textSecondary,
                        )
                    }
                }
                MdBlock.Rule -> HorizontalDivider(color = ZmColors.divider)
            }
        }
    }
}
