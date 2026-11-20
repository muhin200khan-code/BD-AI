package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.model.ChartSpec
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

private sealed interface ContentSegment {
    data class Prose(val text: String) : ContentSegment
    data class CodeBlock(val language: String, val code: String) : ContentSegment
}

private fun parseMessageSegments(raw: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    val regex = Regex("```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*?)```")
    var lastIdx = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIdx) {
            val prose = raw.substring(lastIdx, match.range.first).trim()
            if (prose.isNotEmpty()) segments.add(ContentSegment.Prose(prose))
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        segments.add(ContentSegment.CodeBlock(lang, code))
        lastIdx = match.range.last + 1
    }
    if (lastIdx < raw.length) {
        val tail = raw.substring(lastIdx).trim()
        if (tail.isNotEmpty()) segments.add(ContentSegment.Prose(tail))
    }
    if (segments.isEmpty() && raw.isNotBlank()) {
        segments.add(ContentSegment.Prose(raw))
    }
    return segments
}

private fun formatInlineMarkdown(line: String, codeBg: Color, codeFg: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }
                line[i] == '`' -> {
                    val end = line.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFamily,
                                background = codeBg,
                                color = codeFg
                            )
                        ) {
                            append(" ${line.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun RichAiMessageBody(
    text: String,
    chartSpec: ChartSpec?,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val segments = parseMessageSegments(text)
    val inlineBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val inlineFg = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ContentSegment.Prose -> {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            segment.text.lines().forEach { rawLine ->
                                val trimmed = rawLine.trimStart()
                                when {
                                    trimmed.startsWith("### ") -> {
                                        Text(
                                            text = formatInlineMarkdown(
                                                trimmed.removePrefix("### "),
                                                inlineBg,
                                                inlineFg
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                                        val clean = trimmed.removePrefix("## ").removePrefix("# ")
                                        Text(
                                            text = formatInlineMarkdown(clean, inlineBg, inlineFg),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = formatInlineMarkdown(
                                                    trimmed.drop(2),
                                                    inlineBg,
                                                    inlineFg
                                                ),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = formatInlineMarkdown(rawLine, inlineBg, inlineFg),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is ContentSegment.CodeBlock -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = segment.language.uppercase(Locale.ROOT),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        copyToClipboard(context, "Code", segment.code)
                                        onCopied("Code copied")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy code",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            SelectionContainer {
                                Text(
                                    text = segment.code,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (chartSpec != null) {
            NativeChartAndTableCard(spec = chartSpec)
        }
    }
}

@Composable
fun NativeChartAndTableCard(
    spec: ChartSpec,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (spec.labels.isNotEmpty() && spec.values.isNotEmpty()) {
                val count = minOf(spec.labels.size, spec.values.size)
                val vals = spec.values.take(count)
                val maxVal = (vals.maxOrNull() ?: 1.0).coerceAtLeast(0.0001)
                val minVal = (vals.minOrNull() ?: 0.0).coerceAtMost(0.0)
                val range = (maxVal - minVal).coerceAtLeast(0.0001)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(vertical = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    // Gridlines
                    for (g in 0..3) {
                        val y = h * (g / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (spec.chartType.equals("LINE", ignoreCase = true) && count > 1) {
                        val path = Path()
                        val stepX = w / (count - 1).coerceAtLeast(1)
                        vals.forEachIndexed { idx, value ->
                            val x = idx * stepX
                            val norm = ((value - minVal) / range).toFloat()
                            val y = h - (norm * (h * 0.88f)) - (h * 0.06f)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = accentColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        vals.forEachIndexed { idx, value ->
                            val x = idx * stepX
                            val norm = ((value - minVal) / range).toFloat()
                            val y = h - (norm * (h * 0.88f)) - (h * 0.06f)
                            drawCircle(color = barColor, radius = 5.dp.toPx(), center = Offset(x, y))
                        }
                    } else {
                        val slotW = w / count
                        val barW = slotW * 0.56f
                        vals.forEachIndexed { idx, value ->
                            val norm = ((value - minVal) / range).toFloat().coerceIn(0.05f, 1f)
                            val barH = norm * (h * 0.9f)
                            val x = idx * slotW + (slotW - barW) / 2f
                            drawRoundRect(
                                color = if (idx % 2 == 0) barColor else accentColor,
                                topLeft = Offset(x, h - barH),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    spec.labels.take(count).forEachIndexed { idx, label ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", vals[idx]),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (spec.tableHeaders.isNotEmpty() && spec.tableRows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        spec.tableHeaders.forEach { header ->
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    spec.tableRows.take(8).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            spec.tableHeaders.indices.forEach { colIdx ->
                                Text(
                                    text = row.getOrElse(colIdx) { "" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageActionRow(
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onSpeakToggle: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy response",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSpeakToggle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                contentDescription = if (isSpeaking) "Stop speaking" else "Read aloud",
                modifier = Modifier.size(18.dp),
                tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share response",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
