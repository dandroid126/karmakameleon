package com.karmakameleon.shared

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.repository.ActionRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.util.markdown.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * An [ActionRepository] for a logged-in user, sending its requests to [api] on the Main dispatcher, so make it after
 * setting that. Comments it posts or edits are parsed with [markdown].
 */
fun testActionRepository(
    api: RedditApi,
    markdown: MarkdownParser = MarkdownParser(Dispatchers.Main),
): ActionRepository = ActionRepository(api, CommentRepository(api, markdown), MutableStateFlow(true))

/** The errors [repository] reports while the test runs, collected as they come. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.errorsOf(repository: ActionRepository): List<String> {
    val errors = mutableListOf<String>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.errors.toList(errors) }
    return errors
}
