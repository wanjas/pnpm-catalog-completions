package com.github.wanjas.pnpmcatalogcompletions

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Pops the version list up on its own after `react: `, the way `package.json` does — see
 * `PackageJsonCompletionTypedHandler`. Without this the list would only ever appear on an explicit
 * completion invocation.
 *
 * Scheduling a popup is not the same as showing one: the request is only honored if a contributor
 * actually produces items, so [PnpmCatalogVersionCompletionContributor]'s own guards decide whether
 * anything appears. That keeps this check cheap and free of PSI commits.
 */
internal class PnpmCatalogTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped == ' ' && PnpmWorkspaceCatalogs.isPnpmWorkspaceFile(file)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return Result.CONTINUE
    }
}
