package com.karmakameleon.android.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** Shows [actionError], a failed action like a vote or a reply, once as a Toast, then calls [onConsumeError]. */
@Composable
fun ShowActionErrors(
    actionError: String?,
    onConsumeError: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnConsumeError by rememberUpdatedState(onConsumeError)
    SideEffect(actionError) {
        actionError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            currentOnConsumeError()
        }
    }
}

/** Shows each failed action, like a vote Reddit refused, as a Toast while the app is shown. */
@Composable
fun ShowActionErrors(errors: Flow<String>) {
    val context = LocalContext.current
    LaunchedEffect(errors) {
        errors.collect { error -> Toast.makeText(context, error, Toast.LENGTH_LONG).show() }
    }
}
