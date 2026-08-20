package dev.wanjas

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.javascript.nodejs.npm.AvailablePackageVersions
import com.intellij.javascript.nodejs.npm.registry.NpmRegistryService
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.text.SemVer
import java.util.concurrent.Callable

/**
 * Completes npm versions on the value of a pnpm catalog entry in `pnpm-workspace.yaml`.
 *
 * A port of the platform's `PackageJsonCompletionContributor.completeDependenciesVersions` — the
 * contributor behind version completion in `package.json` — onto YAML PSI, so both behave alike:
 * the same registry service, the same `^`/`~`/exact variants, `latest` first, and distribution tags
 * on a repeated invocation.
 */
internal class PnpmCatalogVersionCompletionContributor : CompletionContributor(), DumbAware {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!PnpmWorkspaceCatalogs.isPnpmWorkspaceFile(parameters.originalFile)) return
        val entry = PnpmWorkspaceCatalogs.findCatalogEntry(parameters.position) ?: return
        if (!PnpmWorkspaceCatalogs.isVersionPosition(entry, parameters.offset)) return

        completeVersions(parameters, result, parameters.originalFile.virtualFile, entry.packageName)
    }

    private fun completeVersions(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        contextFile: VirtualFile?,
        packageName: String,
    ) {
        val prefix = findPrefix(parameters)
        // Relevance is fully decided by the order we add items in, below.
        val sorted = result
            .withRelevanceSorter(CompletionSorter.emptySorter().weigh(PriorityWeigher))
            .withPrefixMatcher(PlainPrefixMatcher(prefix, true))

        val available = fetchVersions(parameters.position.project, contextFile, packageName)

        // With no prefix typed, show only the versions a dist-tag points at — otherwise the popup is
        // thousands of releases long. Typing anything, or invoking completion twice, shows them all.
        val showEveryVersion = prefix.isNotEmpty() || parameters.invocationCount > 1
        val candidates = ArrayList<Pair<SemVer, String?>>()
        for (version in available.versions) {
            val distTag: String? = available.findDistTagByVersion(version)
            if (distTag == null && !showEveryVersion) continue
            // `latest` is what the user almost always wants, so it goes to the very top.
            val index = if (AvailablePackageVersions.LATEST_DIST_TAG == distTag) 0 else candidates.size
            candidates.add(index, version to distTag)
        }

        var order = 0
        val versionPrefixes = versionPrefixesByRelevance(parameters)
        for ((version, distTag) in candidates) {
            for (versionPrefix in versionPrefixes) {
                addVersionItem(sorted, versionPrefix, version.rawVersion, distTag, order++)
            }
        }
        if (showEveryVersion) {
            // The tag names themselves are valid ranges: `react: latest`.
            for (tag in available.distTags) {
                addVersionItem(sorted, "", tag.first, tag.second?.rawVersion, order++)
            }
        }
        if (candidates.isNotEmpty() && prefix.isEmpty()) {
            sorted.addLookupAdvertisement(MyMessageBundle.message("completion.advertisement.latest.versions"))
        }
    }

    /**
     * The already-typed part of the value, with any surrounding YAML quotes stripped.
     *
     * Equivalent to the platform's `PackageJsonCompletionUtil.findPrefix`, which is a Kotlin
     * `internal` object and therefore not reachable from another module.
     */
    private fun findPrefix(parameters: CompletionParameters): String {
        val element = parameters.position
        val text = element.text
        val prefixLength = minOf(text.length, parameters.offset - element.textRange.startOffset)
        if (prefixLength <= 0) return ""
        return unquote(unquote(text.substring(0, prefixLength), '"'), '\'')
    }

    /** Drops a leading and trailing [quoteChar], or just the leading one for an unterminated quote. */
    private fun unquote(text: String, quoteChar: Char): String =
        StringUtil.unquoteString(text, quoteChar).removePrefix(quoteChar.toString())

    /**
     * `^`, `~` and exact, rotated so whichever the user already typed comes first — a plain port of
     * `PackageJsonCompletionContributor.getVersionPrefixesSortedByRelevance`.
     */
    private fun versionPrefixesByRelevance(parameters: CompletionParameters): Array<String> {
        val text = StringUtil.unquoteString(parameters.originalPosition?.text ?: "")
        var typed = if (text.isEmpty()) "^" else text.substring(0, 1)
        if (typed[0].isDigit()) typed = ""

        val prefixes = arrayOf("^", "~", "")
        val index = ArrayUtil.find(prefixes, typed)
        if (index >= 0) ArrayUtil.rotateRight(prefixes, 0, index)
        return prefixes
    }

    private fun addVersionItem(
        result: CompletionResultSet,
        versionPrefix: String,
        version: String,
        typeText: String?,
        order: Int,
    ) {
        val value = versionPrefix + version
        val element = LookupElementBuilder.create(value)
            .withRenderer(object : LookupElementRenderer<LookupElement>() {
                override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                    // Leading space keeps the exact-version column aligned under the ^/~ ones.
                    presentation.itemText = if (versionPrefix.isEmpty()) " $value" else value
                    presentation.isTypeGrayed = true
                    presentation.typeText = typeText
                }
            })
            .withInsertHandler(PnpmCatalogValueInsertHandler(value))
            .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        result.addElement(PrioritizedLookupElement.withPriority(element, order.toDouble()))
    }

    /**
     * Fetches from the same project service `package.json` completion uses, so the response cache and
     * any `.npmrc` registry/scope/auth configuration are shared with it.
     */
    private fun fetchVersions(
        project: Project,
        contextFile: VirtualFile?,
        packageName: String,
    ): AvailablePackageVersions {
        val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
        return try {
            ApplicationUtil.runWithCheckCanceled(Callable {
                val future = NpmRegistryService.getInstance(project)
                    .getCachedOrFetchPackageVersionsFuture(packageName, contextFile)
                ApplicationUtil.runWithCheckCanceled(future, indicator)
            }, indicator)
        } catch (e: ProcessCanceledException) {
            LOG.info("Fetching versions for '$packageName' cancelled")
            AvailablePackageVersions.createEmpty()
        } catch (e: Exception) {
            LOG.info("Cannot fetch versions for '$packageName'", e)
            AvailablePackageVersions.createEmpty()
        }
    }

    /** Orders items by the priority assigned when they were added; lower value sorts first. */
    private object PriorityWeigher : LookupElementWeigher("pnpmCatalogVersionOrder") {
        override fun weigh(element: LookupElement): Comparable<*> =
            (element as? PrioritizedLookupElement<*>)?.priority ?: 0.0
    }

    private companion object {
        private val LOG = Logger.getInstance(PnpmCatalogVersionCompletionContributor::class.java)
    }
}
