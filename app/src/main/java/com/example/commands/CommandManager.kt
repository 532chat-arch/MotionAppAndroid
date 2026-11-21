package com.example.commands

import com.example.schema.MotionProject

data class ProjectCommand(
    val label: String,
    val validate: ((MotionProject) -> String?)? = null,
    val execute: (MotionProject) -> MotionProject,
)

private data class HistoryEntry(
    val label: String,
    val before: MotionProject,
    val after: MotionProject,
)

class CommandManager(private val maxHistory: Int = 100) {
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun execute(
        current: MotionProject,
        command: ProjectCommand,
    ): Pair<MotionProject, String?> {
        val err = command.validate?.invoke(current)
        if (err != null) {
            return current to err
        }
        val next = command.execute(current)
        if (next == current) return current to null

        undoStack.addLast(HistoryEntry(command.label, current, next))
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        return next to null
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun undoLabel(): String? = undoStack.lastOrNull()?.label
    fun redoLabel(): String? = redoStack.lastOrNull()?.label

    fun undo(): Pair<MotionProject, String>? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry.before to entry.label
    }

    fun redo(): Pair<MotionProject, String>? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry.after to entry.label
    }
}
