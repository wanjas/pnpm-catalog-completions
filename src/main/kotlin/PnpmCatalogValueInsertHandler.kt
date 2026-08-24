package com.github.wanjas.pnpmcatalogcompletions

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Replaces the whole catalog entry value with [value].
 *
 * The platform only swaps out the typed prefix, so completing `^18.3.1` over an existing `^17.0.2`
 * would otherwise leave `^18.3.10.2` behind. This is the YAML counterpart of
 * `com.intellij.json.codeinsight.JsonStringPropertyInsertHandler`, which solves the same problem for
 * `package.json`.
 */
internal class PnpmCatalogValueInsertHandler(private val value: String) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.commitDocument()

        val element = context.file.findElementAt(context.startOffset)
        val scalar = PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java, false)
        val start = scalar?.textRange?.startOffset ?: context.startOffset
        val end = scalar?.textRange?.endOffset ?: context.tailOffset

        val text = if (scalar is YAMLQuotedText) {
            // Keep whatever quoting style is already there rather than rewriting it.
            quote(value, scalar.text.firstOrNull() ?: '\'')
        } else {
            toYamlLiteral(value)
        }

        context.document.replaceString(start, end, text)
        context.editor.caretModel.moveToOffset(start + text.length)
        context.commitDocument()
    }

    companion object {
        /**
         * YAML indicators that may not start a plain scalar. Of these, npm version ranges realistically
         * only ever begin with `*` (any version) or `>` (`>=1.0.0`), but the rest cost nothing to cover.
         */
        private const val LEADING_INDICATORS = "*&!%@`|>{}[],#'\"?"

        fun toYamlLiteral(value: String): String =
            if (needsQuoting(value)) quote(value, '\'') else value

        private fun needsQuoting(value: String): Boolean {
            if (value.isEmpty()) return true
            if (value[0] in LEADING_INDICATORS) return true
            // A plain scalar may contain neither a key separator nor a comment marker.
            return value.contains(": ") || value.contains(" #") || value.endsWith(":")
        }

        // Respect the existing quoting style
        private fun quote(value: String, quoteChar: Char): String = when (quoteChar) {
            '"' -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            else -> "'" + value.replace("'", "''") + "'"
        }
    }
}
