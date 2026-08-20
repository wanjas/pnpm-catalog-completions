package dev.wanjas

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.javascript.nodejs.npm.AvailablePackageVersions
import com.intellij.javascript.nodejs.npm.registry.NpmRegistryService
import com.intellij.javascript.nodejs.packageJson.NodePackageBasicInfo
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.CompletableFuture

/**
 * End-to-end completion with the npm registry replaced by a fake, so these run offline and
 * deterministically. What is under test is this plugin's contributor, not the platform's HTTP client.
 */
class PnpmCatalogVersionCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.replaceService(
            NpmRegistryService::class.java,
            FakeNpmRegistryService(mapOf("react" to REACT_METADATA)),
            testRootDisposable,
        )
    }

    fun testEmptyValueOffersDistTaggedVersionsWithRangePrefixes() {
        val items = completeAt("catalog:\n  react: <caret>\n")

        // Only dist-tagged versions on a first invocation, each in ^ / ~ / exact form.
        assertContainsElements(items, "^18.3.1", "~18.3.1", "18.3.1")
        assertContainsElements(items, "^19.0.0-rc.1", "~19.0.0-rc.1", "19.0.0-rc.1")
        assertDoesntContain(items, "^18.2.0")
    }

    fun testLatestIsOfferedFirst() {
        assertEquals("^18.3.1", completeAt("catalog:\n  react: <caret>\n").first())
    }

    fun testTypedPrefixRevealsEveryVersion() {
        val items = completeAt("catalog:\n  react: ^18<caret>\n")
        assertContainsElements(items, "^18.2.0", "^18.3.1")
    }

    fun testTypedRangeKindIsOfferedFirst() {
        val items = completeAt("catalog:\n  react: ~<caret>\n")
        assertTrue("expected a ~ item first, got ${items.take(3)}", items.first().startsWith("~"))
    }

    fun testDistTagNamesAreOfferedOnSecondInvocation() {
        myFixture.configureByText("pnpm-workspace.yaml", "catalog:\n  react: <caret>\n")
        val items = myFixture.complete(CompletionType.BASIC, 2).orEmpty().map { it.lookupString }
        assertContainsElements(items, "latest", "next")
    }

    fun testNamedCatalogEntryCompletes() {
        val items = completeAt("catalogs:\n  react18:\n    react: <caret>\n")
        assertContainsElements(items, "^18.3.1")
    }

    fun testAcceptingAnItemReplacesTheWholeExistingValue() {
        // The regression this guards: the platform swaps out only the typed prefix (`^1` here), so
        // without the insert handler the tail of the old version survives as `^18.3.17.0.2`.
        selectAt("catalog:\n  react: ^1<caret>7.0.2\n", "^18.3.1")
        myFixture.checkResult("catalog:\n  react: ^18.3.1\n")
    }

    fun testQuotesArePreservedWhenCompletingInsideThem() {
        selectAt("catalog:\n  react: '^18<caret>'\n", "^18.3.1")
        myFixture.checkResult("catalog:\n  react: '^18.3.1'\n")
    }

    fun testNoCompletionOutsideCatalogs() {
        assertDoesntContain(completeAt("overrides:\n  react: <caret>\n"), "^18.3.1")
    }

    fun testUnknownPackageOffersNothing() {
        assertEmpty(completeAt("catalog:\n  no-such-package-xyz: <caret>\n"))
    }

    fun testTypingRangePunctuationKeepsThePopupOpen() {
        myFixture.configureByText("pnpm-workspace.yaml", "catalog:\n  react: <caret>\n")
        myFixture.completeBasic()
        assertNotNull("no popup to begin with", myFixture.lookup)
        // Without PnpmCatalogCharFilter, `^` and `.` are not prefix chars and dismiss the lookup.
        myFixture.type("^18.")
        assertNotNull("popup dismissed by range punctuation", myFixture.lookup)
    }

    private fun completeAt(text: String): List<String> {
        myFixture.configureByText("pnpm-workspace.yaml", text)
        return myFixture.completeBasic().orEmpty().map { it.lookupString }
    }

    private fun selectAt(text: String, lookupString: String) {
        myFixture.configureByText("pnpm-workspace.yaml", text)
        val items = myFixture.completeBasic().orEmpty()
        val item = items.firstOrNull { it.lookupString == lookupString }
        assertNotNull("no '$lookupString' among ${items.map { it.lookupString }.take(10)}", item)
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
    }

    /** Serves canned registry metadata; every other entry point is unused by version completion. */
    private class FakeNpmRegistryService(metadata: Map<String, String>) : NpmRegistryService() {
        private val versions: Map<String, AvailablePackageVersions> = metadata.mapValues {
            AvailablePackageVersions.parseFromPackageMetadata(JsonParser.parseString(it.value).asJsonObject)
        }

        private fun versionsOf(packageName: String) =
            versions[packageName] ?: AvailablePackageVersions.createEmpty()

        override fun getCachedOrFetchPackageVersionsFuture(
            packageName: String,
            contextFileOrDir: VirtualFile?,
        ): CompletableFuture<AvailablePackageVersions> =
            CompletableFuture.completedFuture(versionsOf(packageName))

        override suspend fun getCachedOrFetchPackageVersions(
            packageName: String,
            contextFileOrDir: VirtualFile?,
        ): AvailablePackageVersions = versionsOf(packageName)

        override fun fetchPackageJsonFuture(
            packageName: String,
            versionOrRange: String,
            contextFileOrDir: VirtualFile?,
        ): CompletableFuture<JsonObject?> = CompletableFuture.completedFuture(null)

        override suspend fun fetchPackageJson(
            packageName: String,
            versionOrRange: String,
            contextFileOrDir: VirtualFile?,
        ): JsonObject? = null

        override fun findPackages(
            searchQuery: SearchQuery,
            limit: Int,
            contextFileOrDir: VirtualFile?,
            filter: Condition<NodePackageBasicInfo>,
            consumer: java.util.function.Consumer<NodePackageBasicInfo>,
        ) = Unit

        override fun findPackages(
            searchQuery: SearchQuery,
            limit: Int,
            contextFileOrDir: VirtualFile?,
            filter: Condition<NodePackageBasicInfo>,
        ): Flow<NodePackageBasicInfo> = emptyFlow()
    }

    private companion object {
        private val REACT_METADATA = """
            {
              "dist-tags": { "latest": "18.3.1", "next": "19.0.0-rc.1" },
              "versions": {
                "17.0.2": { "name": "react", "version": "17.0.2" },
                "18.2.0": { "name": "react", "version": "18.2.0" },
                "18.3.1": { "name": "react", "version": "18.3.1" },
                "19.0.0-rc.1": { "name": "react", "version": "19.0.0-rc.1" }
              }
            }
        """.trimIndent()
    }
}
