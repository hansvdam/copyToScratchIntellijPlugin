package com.copyhelper

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class CopyToScratchAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText

        if (selectedText.isNullOrEmpty()) {
            showNotification(project, "No text selected", NotificationType.WARNING)
            return
        }

        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val scratchFile = findLastFocusedScratch(project, currentFile)

        if (scratchFile == null) {
            showNotification(project, "No scratch file found in open editors", NotificationType.WARNING)
            return
        }

        insertTextIntoScratch(project, scratchFile, selectedText)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = e.project != null && hasSelection
    }

    private fun findLastFocusedScratch(project: Project, currentFile: VirtualFile?): VirtualFile? {
        // EditorHistoryManager tracks file access/focus order (oldest first),
        // so reversing gives most-recently-focused first
        val fileList = EditorHistoryManager.getInstance(project).fileList
        return fileList.asReversed()
            .firstOrNull { it != currentFile && it.isValid && isScratchFile(it) }
    }

    private fun isScratchFile(file: VirtualFile): Boolean {
        return ScratchFileService.findRootType(file) is ScratchRootType
    }

    private fun insertTextIntoScratch(project: Project, scratchFile: VirtualFile, text: String) {
        val document = FileDocumentManager.getInstance().getDocument(scratchFile)
        if (document == null) {
            showNotification(project, "Could not open document for ${scratchFile.name}", NotificationType.ERROR)
            return
        }

        // Use EditorFactory to find the actual Editor instance directly — this is more
        // reliable than FileEditorManager.getEditors() which may return composite wrappers
        // that don't pass filterIsInstance<TextEditor>().
        val existingEditor = EditorFactory.getInstance().getEditors(document, project)
            .firstOrNull { !it.isDisposed }

        if (existingEditor != null) {
            val offset = existingEditor.caretModel.offset
            WriteCommandAction.runWriteCommandAction(project, "Copy to Scratch", null, {
                document.insertString(offset, "\n" + text)
                existingEditor.caretModel.moveToOffset(offset + 1 + text.length)
            })
            showNotification(project, "Copied to ${scratchFile.name}", NotificationType.INFORMATION)
            return
        }

        // Scratch is not currently open — open it, then defer insertion so the
        // editor state (including caret position) has time to be restored from history.
        val fem = FileEditorManager.getInstance(project)
        val opened = fem.openFile(scratchFile, false)
        val newEditor = opened.filterIsInstance<TextEditor>().firstOrNull()

        if (newEditor != null) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && scratchFile.isValid) {
                    val editor = newEditor.editor
                    val offset = editor.caretModel.offset
                    WriteCommandAction.runWriteCommandAction(project, "Copy to Scratch", null, {
                        document.insertString(offset, "\n" + text)
                        editor.caretModel.moveToOffset(offset + 1 + text.length)
                    })
                    showNotification(project, "Copied to ${scratchFile.name}", NotificationType.INFORMATION)
                }
            }
        } else {
            // Fallback: no editor available, append to document end
            WriteCommandAction.runWriteCommandAction(project, "Copy to Scratch", null, {
                document.insertString(document.textLength, "\n" + text)
            })
            showNotification(project, "Appended to end of ${scratchFile.name}", NotificationType.INFORMATION)
        }
    }

    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CopyToScratch")
            .createNotification(content, type)
            .notify(project)
    }
}
