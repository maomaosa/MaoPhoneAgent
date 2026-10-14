/*
 * File: PhoneInputService.kt
 * Module: application.service
 *
 * Description:
 * Writes text and submits editor actions through the connected accessibility service.
 * Targets only the focused editable control on the selected display.
 */
package com.example.maophoneagent.application.service

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneInputService {
    /** Replaces the focused field's entire value, preserving Unicode and spaces. */
    suspend fun inputText(displayId: Int, text: String): Boolean {
        require(text.length <= 2000) { "Input text must contain at most 2000 characters" }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return performEditorAction(displayId, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /** Invokes the editor's configured IME action, such as Search, Go, or Done. */
    suspend fun submitInput(displayId: Int): Boolean = performEditorAction(
        displayId, AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id, null
    )

    /**
     * Finds input focus within application windows on this display only.
     * Missing or ambiguous focus fails instead of choosing an unrelated editable field.
     * The caller must observe the screen to verify the app's response to an accepted action.
     */
    @Suppress("DEPRECATION")
    private suspend fun performEditorAction(
        displayId: Int, action: Int, arguments: Bundle?
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        require(displayId > 0) { "Main display is not allowed" }
        val service = checkNotNull(PhoneAccessibilityService.currentInstance()) {
            "Accessibility service is not connected"
        }
        val windows = service.windowsOnAllDisplays.get(displayId).orEmpty()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (window in windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                try {
                    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
                    if (focused.isFocused && focused.isEditable && focused.isEnabled && focused.isVisibleToUser) {
                        candidates.add(focused)
                    } else {
                        focused.recycle()
                    }
                } finally {
                    root.recycle()
                }
            }
            check(candidates.size == 1) {
                "Expected one focused editable field on display $displayId; found ${candidates.size}. Tap the input field and observe first."
            }
            val node = candidates.single()
            check(node.actionList.any { it.id == action }) {
                "Focused field does not support this action. For submission, tap the visible search or submit button instead."
            }
            node.performAction(action, arguments)
        } finally {
            candidates.forEach { it.recycle() }
            windows.forEach { it.recycle() }
        }
    }
}
