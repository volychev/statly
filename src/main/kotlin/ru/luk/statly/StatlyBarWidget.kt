package ru.luk.statly

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.util.Consumer
import com.intellij.util.messages.MessageBusConnection
import java.awt.event.MouseEvent
import kotlin.math.log10
import kotlin.math.pow

/**
 * Factory class for the Statly status bar widget.
 * Responsible for creating the widget, registering it with the status bar,
 * and providing metadata such as ID and display name.
 */
class StatlyBarWidgetFactory : StatusBarWidgetFactory {

    /**
     * Returns the unique ID of the widget.
     */
    override fun getId(): String = "Statly"

    /**
     * Returns the display name shown in Settings.
     */
    override fun getDisplayName(): String = "File Info Widget"

    /**
     * Creates a new instance of the widget for the given project.
     */
    override fun createWidget(project: Project): StatusBarWidget = StatlyBarWidget(project)
}

/**
 * Status bar widget that shows the number of lines, character count,
 * and size of the currently selected file in the project.
 *
 * @param project the IDE project this widget belongs to
 */
class StatlyBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    /** UI component that displays text in the status bar. */
    private val textPanel = TextPanel()

    /** Reference to the status bar where this widget is installed. */
    private var statusBar: StatusBar? = null

    /** Connection to the project message bus for listening to file editor events. */
    private var connection: MessageBusConnection? = null

    /** Listener for document changes to update the widget text. */
    private var docListener: DocumentListener? = null

    /**
     * Returns the unique ID of this widget.
     */
    override fun ID(): String = "Statly"

    /**
     * Returns the widget's presentation, which defines how it is displayed in the status bar.
     */
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    /**
     * Returns the tooltip text shown when hovering over the widget.
     */
    override fun getTooltipText(): String = "Number of lines and file size"

    /**
     * Returns the alignment of the widget text.
     * 0.0f = left aligned, 0.5f = centered, 1.0f = right aligned.
     */
    override fun getAlignment(): Float = 0.0f

    /**
     * Returns the text to display in the status bar.
     */
    override fun getText(): String = textPanel.text.toString()

    /**
     * Called when the widget is installed into the status bar.
     * Registers listeners for file selection changes and document edits
     * to keep the displayed text up-to-date.
     *
     * @param statusBar the status bar this widget is installed on
     */
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        updateText()

        connection = project.messageBus.connect()
        connection?.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    updateText()
                }
            }
        )

        docListener = object : DocumentListener {
            override fun beforeDocumentChange(event: DocumentEvent) {}
            override fun documentChanged(event: DocumentEvent) {
                updateText()
            }
        }
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(docListener!!, project)
    }

    /**
     * Updates the widget's text based on the currently selected file.
     * Displays the character count, line count, and file size.
     */
    private fun updateText() {
        val editorManager = FileEditorManager.getInstance(project)
        val file = editorManager.selectedFiles.firstOrNull()

        textPanel.text = run {
            if (file == null) {
                ""
            } else {
                val editor = editorManager.selectedTextEditor
                val document = editor?.document

                val lineCount = document?.lineCount ?: 0
                val length = document?.textLength ?: 0

                val bytes = file.length
                val fileSize = if (bytes < 1024) {
                    "$bytes B"
                } else {
                    val exp = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                    val pre = "KMG"[exp - 1]
                    String.format("%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
                }

                "$length:$lineCount / $fileSize"
            }
        }

        statusBar?.updateWidget(ID())
    }

    /**
     * Disposes of the widget, removing all listeners and references to the status bar.
     */
    override fun dispose() {
        try {
            connection?.disconnect()
        } catch (_: Exception) {}
        try {
            docListener?.let {
                EditorFactory.getInstance().eventMulticaster.removeDocumentListener(it)
            }
        } catch (_: Exception) {}
        statusBar = null
    }
}
