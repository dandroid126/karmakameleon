package com.karmakameleon.shared.data.repository

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentDraftRepositoryTest {

    private fun createRepo(settings: MapSettings = MapSettings()) = CommentDraftRepository(settings)

    @Test
    fun loadDraft_returnsNullWhenNoDraft() {
        val repo = createRepo()
        assertNull(repo.loadDraft("parent1"))
    }

    @Test
    fun saveDraft_persistsDraft() {
        val repo = createRepo()
        repo.saveDraft("parent1", "draft text")
        assertEquals("draft text", repo.loadDraft("parent1"))
    }

    @Test
    fun saveDraft_overwritesPreviousDraft() {
        val repo = createRepo()
        repo.saveDraft("parent1", "first draft")
        repo.saveDraft("parent1", "second draft")
        assertEquals("second draft", repo.loadDraft("parent1"))
    }

    @Test
    fun deleteDraft_removesDraft() {
        val repo = createRepo()
        repo.saveDraft("parent1", "draft text")
        repo.deleteDraft("parent1")
        assertNull(repo.loadDraft("parent1"))
    }

    @Test
    fun deleteDraft_noOpWhenNoDraft() {
        val repo = createRepo()
        repo.deleteDraft("parent1") // Should not throw
        assertNull(repo.loadDraft("parent1"))
    }

    @Test
    fun saveDraft_multipleDrafts() {
        val repo = createRepo()
        repo.saveDraft("parent1", "draft 1")
        repo.saveDraft("parent2", "draft 2")
        repo.saveDraft("parent3", "draft 3")
        assertEquals("draft 1", repo.loadDraft("parent1"))
        assertEquals("draft 2", repo.loadDraft("parent2"))
        assertEquals("draft 3", repo.loadDraft("parent3"))
    }

    @Test
    fun saveDraft_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = CommentDraftRepository(settings)
        repo1.saveDraft("parent1", "persistent draft")

        val repo2 = CommentDraftRepository(settings)
        assertEquals("persistent draft", repo2.loadDraft("parent1"))
    }

    @Test
    fun saveDraft_emptyString() {
        val repo = createRepo()
        repo.saveDraft("parent1", "")
        assertEquals("", repo.loadDraft("parent1"))
    }

    @Test
    fun saveDraft_specialCharacters() {
        val repo = createRepo()
        val text = "> quoted\n\n**bold** *italic* [link](url)"
        repo.saveDraft("parent1", text)
        assertEquals(text, repo.loadDraft("parent1"))
    }
}
