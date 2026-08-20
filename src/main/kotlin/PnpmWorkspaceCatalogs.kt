package dev.wanjas.pnpmcatalogcompletions

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * Recognizes package entries of pnpm catalogs inside `pnpm-workspace.yaml`:
 *
 * ```yaml
 * catalog:
 *   react: ^18.3.1
 * catalogs:
 *   react17:
 *     react: ^17.0.2
 * ```
 *
 * Pure PSI, no services — everything here is directly unit-testable.
 */
internal object PnpmWorkspaceCatalogs {
    /** pnpm itself only reads `pnpm-workspace.yaml`; `.yml` is accepted so a typo still gets completion. */
    private val FILE_NAMES = setOf("pnpm-workspace.yaml", "pnpm-workspace.yml")

    private const val DEFAULT_CATALOG_KEY = "catalog"
    private const val NAMED_CATALOGS_KEY = "catalogs"

    internal data class CatalogEntry(val keyValue: YAMLKeyValue, val packageName: String)

    fun isPnpmWorkspaceFile(file: PsiFile?): Boolean = file != null && file.name in FILE_NAMES

    /**
     * The `<package>: <version>` entry containing [position], or `null` if [position] is anywhere
     * else in the file (`packages:`, `overrides:`, a catalog name, a top-level key, ...).
     */
    fun findCatalogEntry(position: PsiElement): CatalogEntry? {
        val keyValue = PsiTreeUtil.getParentOfType(position, YAMLKeyValue::class.java, false) ?: return null
        val parts = YAMLUtil.getConfigFullNameParts(keyValue)
        val isCatalogEntry = when (parts.size) {
            2 -> parts[0] == DEFAULT_CATALOG_KEY
            3 -> parts[0] == NAMED_CATALOGS_KEY
            else -> false
        }
        if (!isCatalogEntry) return null

        // Strip quotes so `"@scope/pkg": ^1.0.0` resolves too.
        val packageName = StringUtil.unquoteString(keyValue.keyText).trim()
        if (packageName.isEmpty() || packageName.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) return null

        return CatalogEntry(keyValue, packageName)
    }

    /** True when [offset] sits in the entry's value rather than in its key. */
    fun isVersionPosition(entry: CatalogEntry, offset: Int): Boolean {
        val key = entry.keyValue.key ?: return false
        return offset > key.textRange.endOffset
    }
}
