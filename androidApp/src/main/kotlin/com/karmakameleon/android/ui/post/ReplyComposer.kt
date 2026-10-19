package com.karmakameleon.android.ui.post

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karmakameleon.android.ui.components.ReplyBar
import com.karmakameleon.shared.ui.comment.CommentUiState

/** Whether closing the reply or edit in progress would lose text that isn't saved as a draft. */
internal val CommentUiState.hasUnsavedReply: Boolean
    get() {
        val draft = savedDraftText.orEmpty()
        return if (editingCommentId != null) {
            replyText != editingOriginalText && replyText != draft
        } else {
            replyText.isNotBlank() && replyText != draft
        }
    }

/** The confirmations the reply bar asks for: discarding unsaved text, and replacing text with a saved draft. */
@Stable
internal class ReplyDraftDialogState {
    var pendingDiscard by mutableStateOf<(() -> Unit)?>(null)
        private set
    var pendingDraft by mutableStateOf<String?>(null)
        private set

    /** Closes the reply, after asking when that would lose unsaved text. */
    fun requestClose(
        hasUnsavedReply: Boolean,
        close: () -> Unit,
    ) {
        if (hasUnsavedReply) pendingDiscard = close else close()
    }

    /** Puts the draft in the reply, after asking when that would replace other text. */
    fun requestLoadDraft(
        draft: String,
        replyText: String,
        apply: (String) -> Unit,
    ) {
        if (replyText.isNotBlank() && replyText != draft) pendingDraft = draft else apply(draft)
    }

    fun dismissDiscard() {
        pendingDiscard = null
    }

    fun dismissDraft() {
        pendingDraft = null
    }
}

@Composable
internal fun rememberReplyDraftDialogState(): ReplyDraftDialogState = remember { ReplyDraftDialogState() }

/** What the reply bar does. requestClose asks before losing unsaved text; loadDraft before replacing text. */
internal data class ReplyActions(
    val onReplyTextChange: (String) -> Unit,
    val onSubmitEdit: () -> Unit,
    val onSubmitReply: () -> Unit,
    val onQuote: () -> Unit,
    val requestClose: () -> Unit,
    val saveDraft: () -> Unit,
    val loadDraft: (String) -> Unit,
)

/** The reply bar, while a reply or an edit is in progress. */
@Composable
internal fun PostReplyBar(
    commentState: CommentUiState,
    actions: ReplyActions,
) {
    val isEditing = commentState.editingCommentId != null
    if (!isEditing && commentState.replyingTo == null) return
    val draft = commentState.savedDraftText
    ReplyBar(
        replyText = commentState.replyText,
        onReplyTextChange = actions.onReplyTextChange,
        onSubmit = if (isEditing) actions.onSubmitEdit else actions.onSubmitReply,
        onCancel = actions.requestClose,
        onSaveDraft = actions.saveDraft,
        onLoadDraft = { draft?.let(actions.loadDraft) },
        onQuote = actions.onQuote,
        hasDraft = draft != null,
        showDraftControls = true,
        placeholder = if (isEditing) "Edit comment..." else "Write a reply...",
    )
}

/** The dialogs [ReplyDraftDialogState] asks for. */
@Composable
internal fun ReplyDraftDialogs(
    state: ReplyDraftDialogState,
    onSaveDraft: () -> Unit,
    onApplyDraft: (String) -> Unit,
) {
    state.pendingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = state::dismissDraft,
            title = { Text("Load Draft") },
            text = { Text("Loading the draft will overwrite your current text. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    onApplyDraft(draft)
                    state.dismissDraft()
                }) {
                    Text("Load Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = state::dismissDraft) {
                    Text("Cancel")
                }
            },
        )
    }

    state.pendingDiscard?.let { close ->
        AlertDialog(
            onDismissRequest = state::dismissDiscard,
            title = { Text("Discard Changes") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        onSaveDraft()
                        close()
                        state.dismissDiscard()
                    }) {
                        Text("Save Draft")
                    }
                    TextButton(onClick = {
                        close()
                        state.dismissDiscard()
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = state::dismissDiscard) {
                    Text("Cancel")
                }
            },
        )
    }
}
