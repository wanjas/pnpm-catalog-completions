package com.github.wanjas.pnpmcatalogcompletions

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.yaml.YAMLLanguage

/**
 * Keeps the version popup open while range punctuation is typed.
 *
 * By default a char like `^` or `.` is not part of a lookup prefix, so typing `^18.` would dismiss
 * the popup. The platform has the same problem in `package.json` and solves it the same way — see
 * `PackageJsonCompletionContributor.DependencyCharFilter`.
 *
 * Char filters are application-wide, so this is scoped to YAML inside `pnpm-workspace.yaml`.
 */
internal class PnpmCatalogCharFilter : CharFilter() {

    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
        if (!lookup.isCompletion) return null
        val element = lookup.psiElement ?: return null
        if (element.language != YAMLLanguage.INSTANCE) return null
        if (!PnpmWorkspaceCatalogs.isPnpmWorkspaceFile(element.containingFile)) return null

        // `@/-` for scoped and hyphenated names, `.^~<>=*` for the range syntax itself.
        return if (StringUtil.containsChar(RANGE_CHARS, c)) Result.ADD_TO_PREFIX else null
    }

    private companion object {
        private const val RANGE_CHARS = "@/-.^~<>=*"
    }
}
