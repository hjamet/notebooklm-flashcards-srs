package com.notebooklm.flashcards.ui.components

import android.text.SpannableStringBuilder
import android.widget.TextView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

@Composable
fun MathMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 18f
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    val processedText = remember(text) {
        formatMathInMarkdown(text)
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = textSizeSp
                setTextColor(textColor)
                setLineSpacing(8f, 1.15f)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.textSize = textSizeSp
            val markdownSpannable = markwon.toMarkdown(processedText)
            textView.text = markdownSpannable
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Pre-processes LaTeX math blocks ($...$ and $$...$$) to ensure clean Unicode math rendering
 * when KaTeX native libraries are not bundled.
 */
private fun formatMathInMarkdown(raw: String): String {
    if (!raw.contains("$")) return raw

    var result = raw

    // Process display math $$...$$
    result = result.replace(Regex("""\$\$\s*([\s\S]*?)\s*\$\$""")) { match ->
        val mathContent = formatMathSymbols(match.groupValues[1].trim())
        "\n\n> 📐 **$mathContent**\n\n"
    }

    // Process inline math $...$
    result = result.replace(Regex("""\$([^\$\n]+)\$""")) { match ->
        val mathContent = formatMathSymbols(match.groupValues[1].trim())
        " *$mathContent* "
    }

    return result
}

private fun formatMathSymbols(latex: String): String {
    return latex
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\delta", "δ")
        .replace("\\epsilon", "ε")
        .replace("\\theta", "θ")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\pi", "π")
        .replace("\\sigma", "σ")
        .replace("\\omega", "ω")
        .replace("\\Delta", "Δ")
        .replace("\\Sigma", "Σ")
        .replace("\\Omega", "Ω")
        .replace("\\infty", "∞")
        .replace("\\le", "≤")
        .replace("\\ge", "≥")
        .replace("\\neq", "≠")
        .replace("\\approx", "≈")
        .replace("\\times", "×")
        .replace("\\div", "÷")
        .replace("\\pm", "±")
        .replace("\\cdot", "·")
        .replace("\\in", "∈")
        .replace("\\notin", "∉")
        .replace("\\subset", "⊂")
        .replace("\\subseteq", "⊆")
        .replace("\\cup", "∪")
        .replace("\\cap", "∩")
        .replace("\\to", "→")
        .replace("\\Rightarrow", "⇒")
        .replace("\\Leftrightarrow", "⇔")
        .replace("\\forall", "∀")
        .replace("\\exists", "∃")
        .replace("\\sqrt{", "√(")
        .replace("\\frac{", "(")
        .replace("}{", " / ")
        .replace("}", ")")
        .replace("^{2}", "²")
        .replace("^{3}", "³")
        .replace("_{", "_")
}
