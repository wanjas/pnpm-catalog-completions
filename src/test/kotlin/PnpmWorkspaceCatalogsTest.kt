package dev.wanjas

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Context detection: which positions in `pnpm-workspace.yaml` are catalog entry versions.
 * This is the part that decides whether the registry is hit at all, so it is checked without a
 * completion session in the way.
 */
class PnpmWorkspaceCatalogsTest : BasePlatformTestCase() {

    fun testDefaultCatalogEntryIsRecognized() {
        assertPackageName("react", "catalog:\n  react: ^18<caret>.0.0\n")
    }

    fun testNamedCatalogEntryIsRecognized() {
        assertPackageName("react-dom", "catalogs:\n  react17:\n    react-dom: ^17<caret>.0.2\n")
    }

    fun testEmptyValueIsRecognized() {
        // The most common case: the user has typed `react: ` and nothing else yet. Completion never
        // sees a truly empty value — the platform substitutes a dummy identifier at the caret first —
        // so that is the shape asserted here. The real editor path is covered end-to-end by
        // PnpmCatalogVersionCompletionTest.
        val dummy = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
        assertPackageName("react", "catalog:\n  react: <caret>$dummy\n")
    }

    fun testQuotedValueIsRecognized() {
        assertPackageName("react", "catalog:\n  react: '^18<caret>.0.0'\n")
    }

    fun testScopedPackageNameIsRecognized() {
        assertPackageName("@types/node", "catalog:\n  \"@types/node\": ^22<caret>.0.0\n")
    }

    fun testDottedPackageNameIsRecognized() {
        // YAMLUtil.getConfigFullNameParts keeps raw key text, so a dot in the name is not a path separator.
        assertPackageName("socket.io", "catalog:\n  socket.io: ^4<caret>.0.0\n")
    }

    fun testPackagesListIsRejected() {
        assertNotACatalogEntry("packages:\n  - 'packages/<caret>*'\n")
    }

    fun testOverridesEntryIsRejected() {
        assertNotACatalogEntry("overrides:\n  react: ^18<caret>.0.0\n")
    }

    fun testTopLevelKeyIsRejected() {
        assertNotACatalogEntry("nodeLinker: hoi<caret>sted\n")
    }

    fun testCatalogNameItselfIsRejected() {
        // `react17` is a catalog name, not a package.
        assertNotACatalogEntry("catalogs:\n  react1<caret>7:\n    react: ^17.0.2\n")
    }

    fun testNestedCatalogIsRejected() {
        assertNotACatalogEntry("catalog:\n  react:\n    nested: val<caret>ue\n")
    }

    fun testOtherYamlFileIsRejected() {
        myFixture.configureByText("docker-compose.yaml", "catalog:\n  react: ^18<caret>.0.0\n")
        assertFalse(PnpmWorkspaceCatalogs.isPnpmWorkspaceFile(myFixture.file))
    }

    fun testCaretInKeyIsNotAVersionPosition() {
        val entry = configureAndFindEntry("catalog:\n  rea<caret>ct: ^18.0.0\n")
        assertNotNull(entry)
        assertFalse(PnpmWorkspaceCatalogs.isVersionPosition(entry!!, myFixture.caretOffset))
    }

    fun testCaretInValueIsAVersionPosition() {
        val entry = configureAndFindEntry("catalog:\n  react: ^18<caret>.0.0\n")
        assertNotNull(entry)
        assertTrue(PnpmWorkspaceCatalogs.isVersionPosition(entry!!, myFixture.caretOffset))
    }

    private fun assertPackageName(expected: String, text: String) {
        val entry = configureAndFindEntry(text)
        assertNotNull("expected a catalog entry for '$expected'", entry)
        assertEquals(expected, entry!!.packageName)
        assertTrue(PnpmWorkspaceCatalogs.isVersionPosition(entry, myFixture.caretOffset))
    }

    private fun assertNotACatalogEntry(text: String) {
        val entry = configureAndFindEntry(text)
        if (entry != null) {
            assertFalse(
                "expected no version completion here, got package '${entry.packageName}'",
                PnpmWorkspaceCatalogs.isVersionPosition(entry, myFixture.caretOffset),
            )
        }
    }

    private fun configureAndFindEntry(text: String): PnpmWorkspaceCatalogs.CatalogEntry? {
        myFixture.configureByText("pnpm-workspace.yaml", text)
        assertTrue(PnpmWorkspaceCatalogs.isPnpmWorkspaceFile(myFixture.file))
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("no PSI at caret", element)
        return PnpmWorkspaceCatalogs.findCatalogEntry(element!!)
    }
}
