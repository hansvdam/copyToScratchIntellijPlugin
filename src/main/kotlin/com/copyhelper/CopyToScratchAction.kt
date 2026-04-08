package com.copyhelper

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
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
        val scratchFile = findLastOpenedScratch(project, currentFile)

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

    private fun findLastOpenedScratch(project: Project, currentFile: VirtualFile?): VirtualFile? {
        // Check open editors for scratch files (most recently used order from editor history)
        val fem = FileEditorManagerEx.getInstanceEx(project)

        // Try open files first — look for scratch files that are not the current file
        val openScratch = fem.openFiles
            .filter { it != currentFile && isScratchFile(it) }
            .firstOrNull()

        if (openScratch != null) return openScratch

        // Fallback: search editor history for recently accessed scratch files
        try {
            val historyClass = Class.forName("com.intellij.openapi.fileEditor.impl.EditorHistoryManager")
            val getInstance = historyClass.getMethod("getInstance", Project::class.java)
            val historyManager = getInstance.invoke(null, project)
            val getFileList = historyClass.getMethod("getFileList")
            @Suppress("UNCHECKED_CAST")
            val fileList = getFileList.invoke(historyManager) as List<VirtualFile>
            return fileList.asReversed()
                .firstOrNull { it != currentFile && it.isValid && isScratchFile(it) }
        } catch (_: Exception) {
            return null
        }
    }

    private fun isScratchFile(file: VirtualFile): Boolean {
        return ScratchFileService.findRootType(file) is ScratchRootType
    }

    private fun insertTextIntoScratch(project: Project, scratchFile: VirtualFile, text: String) {
        val fem = FileEditorManager.getInstance(project)
        val editors = fem.getEditors(scratchFile)
        val textEditor = editors.filterIsInstance<TextEditor>().firstOrNull()

        if (textEditor != null) {
            val targetEditor = textEditor.editor
            val offset = targetEditor.caretModel.offset
            val document = targetEditor.document

            WriteCommandAction.runWriteCommandAction(project, "Copy to Scratch", null, {
                document.insertString(offset, text)
                targetEditor.caretModel.moveToOffset(offset + text.length)
            })

            showNotification(project, "Copied to ${scratchFile.name}", NotificationType.INFORMATION)
        } else {
            // Scratch file is in history but not currently open — append to end
            val document = FileDocumentManager.getInstance().getDocument(scratchFile)
            if (document == null) {
                showNotification(project, "Could not open document for ${scratchFile.name}", NotificationType.ERROR)
                return
            }

            WriteCommandAction.runWriteCommandAction(project, "Copy to Scratch", null, {
                document.insertString(document.textLength, text)
            })

            showNotification(project, "Appended to end of ${scratchFile.name} (file was not open)", NotificationType.INFORMATION)
        }
    }

    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CopyToScratch")
            .createNotification(content, type)
            .notify(project)
    }
}
