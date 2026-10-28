package com.tom7.openremote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom7.openremote.ui.theme.CodeTextStyle

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String?, val text: String) : MdBlock
    data class Bullets(val items: List<String>, val ordered: Boolean) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> InlineText(block.text)
                is MdBlock.Heading -> InlineText(
                    block.text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = when (block.level) {
                            1 -> 21.sp
                            2 -> 18.sp
                            else -> 16.sp
                        },
                        lineHeight = when (block.level) {
                            1 -> 27.sp
                            2 -> 24.sp
                            else -> 22.sp
                        },
                    ),
                    forceBold = true,
                )

                is MdBlock.Code -> CodeBlock(block.language, block.text)
                is MdBlock.Bullets -> Bullets(block.items, block.ordered)
                is MdBlock.Quote -> Quote(block.text)
                MdBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    forceBold: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val annotated = remember(text, colors.primary, colors.surfaceVariant, colors.onSurfaceVariant, forceBold) {
        buildInline(
            text = text,
            linkColor = colors.primary,
            codeBackground = colors.surfaceVariant,
            codeForeground = colors.onSurfaceVariant,
            forceBold = forceBold,
        )
    }
    Text(text = annotated, style = style, color = colors.onSurface, modifier = modifier)
}

@Composable
private fun Bullets(items: List<String>, ordered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp),
                )
                InlineText(item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Quote(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        InlineText(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val copy = rememberClipboardWriter()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.takeIf { it.isNotBlank() } ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { copy(code) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = code,
                style = CodeTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

private fun buildInline(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    codeForeground: Color,
    forceBold: Boolean,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    fun styled(open: String, close: String, style: SpanStyle) {
        val end = text.indexOf(close, index + open.length)
        if (end < 0) {
            plain.append(text[index])
            index++
            return
        }
        flushPlain()
        withStyle(style) { append(text.substring(index + open.length, end)) }
        index = end + close.length
    }

    while (index < text.length) {
        when {
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end < 0) {
                    plain.append(text[index]); index++
                } else {
                    flushPlain()
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeForeground,
                        ),
                    ) { append(text.substring(index + 1, end)) }
                    index = end + 1
                }
            }

            text.startsWith("**", index) -> styled("**", "**", SpanStyle(fontWeight = FontWeight.Bold))
            text.startsWith("__", index) -> styled("__", "__", SpanStyle(fontWeight = FontWeight.Bold))
            text.startsWith("~~", index) -> styled("~~", "~~", SpanStyle(textDecoration = TextDecoration.LineThrough))
            text.startsWith("*", index) -> styled("*", "*", SpanStyle(fontStyle = FontStyle.Italic))
            text.startsWith("_", index) -> styled("_", "_", SpanStyle(fontStyle = FontStyle.Italic))
            text.startsWith("[", index) -> {
                val labelEnd = text.indexOf(']', index + 1)
                val urlStart = if (labelEnd > 0) text.indexOf('(', labelEnd + 1) else -1
                val urlEnd = if (urlStart > 0) text.indexOf(')', urlStart + 1) else -1
                if (labelEnd > 0 && urlStart == labelEnd + 1 && urlEnd > urlStart) {
                    val label = text.substring(index + 1, labelEnd)
                    val url = text.substring(urlStart + 1, urlEnd)
                    flushPlain()
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            ),
                        ),
                    ) { append(label) }
                    index = urlEnd + 1
                } else {
                    plain.append(text[index]); index++
                }
            }

            text.startsWith("http://", index) || text.startsWith("https://", index) -> {
                var end = index
                while (end < text.length && !text[end].isWhitespace() && text[end] !in ")]}") end++
                val url = text.substring(index, end)
                flushPlain()
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                    ),
                ) { append(url) }
                index = end
            }

            else -> {
                plain.append(text[index])
                index++
            }
        }
    }
    flushPlain()
    if (forceBold) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), 0, length)
}

private fun parseMarkdown(input: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = input.replace("\r\n", "\n").split('\n')
    val paragraph = StringBuilder()
    val listItems = mutableListOf<String>()
    var listOrdered = false
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    fun flushList() {
        if (listItems.isNotEmpty()) {
            blocks.add(MdBlock.Bullets(listItems.toList(), listOrdered))
            listItems.clear()
        }
    }

    fun flushAll() {
        flushParagraph()
        flushList()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                flushAll()
                val fence = trimmed.take(3)
                val language = trimmed.removePrefix(fence).trim().ifBlank { null }
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
                    code.add(lines[index])
                    index++
                }
                blocks.add(MdBlock.Code(language, code.joinToString("\n")))
            }

            trimmed.isEmpty() -> flushAll()

            trimmed.startsWith("#") -> {
                val hashes = trimmed.takeWhile { it == '#' }.length
                if (hashes in 1..6 && trimmed.getOrNull(hashes) == ' ') {
                    flushAll()
                    blocks.add(MdBlock.Heading(hashes, trimmed.substring(hashes + 1).trim()))
                } else {
                    appendParagraph(paragraph, line)
                }
            }

            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                flushAll()
                blocks.add(MdBlock.Rule)
            }

            trimmed.startsWith("> ") || trimmed == ">" -> {
                flushAll()
                val quoted = mutableListOf<String>()
                while (index < lines.size) {
                    val next = lines[index].trimStart()
                    if (!next.startsWith(">")) break
                    quoted.add(next.removePrefix(">").removePrefix(" ").trimEnd())
                    index++
                }
                index--
                blocks.add(MdBlock.Quote(quoted.joinToString("\n")))
            }

            isBullet(trimmed) -> {
                flushParagraph()
                if (listItems.isNotEmpty() && listOrdered) flushList()
                listOrdered = false
                listItems.add(trimmed.drop(2).trim())
            }

            orderedMatch(trimmed) != null -> {
                flushParagraph()
                if (listItems.isNotEmpty() && !listOrdered) flushList()
                listOrdered = true
                listItems.add(trimmed.substringAfter('.').trim())
            }

            else -> {
                flushList()
                appendParagraph(paragraph, line)
            }
        }
        index++
    }
    flushAll()
    return blocks
}

private fun appendParagraph(builder: StringBuilder, line: String) {
    if (builder.isNotEmpty()) builder.append('\n')
    builder.append(line.trimEnd())
}

private fun isBullet(line: String): Boolean =
    (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ "))

private fun orderedMatch(line: String): MatchResult? =
    Regex("^\\d+\\.\\s").find(line)
