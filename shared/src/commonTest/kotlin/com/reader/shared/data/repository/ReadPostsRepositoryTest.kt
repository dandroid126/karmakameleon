package com.reader.shared.data.repository

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadPostsRepositoryTest {

    private fun createRepo(settings: MapSettings = MapSettings()) = ReadPostsRepository(settings)

    @Test
    fun readPostIds_defaultsToEmpty() {
        val repo = createRepo()
        assertTrue(repo.readPostIds.value.isEmpty())
    }

    @Test
    fun isRead_returnsFalseForUnreadPost() {
        val repo = createRepo()
        assertFalse(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_marksPostAsRead() {
        val repo = createRepo()
        repo.markAsRead("post1")
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_multiplePosts() {
        val repo = createRepo()
        repo.markAsRead("post1")
        repo.markAsRead("post2")
        repo.markAsRead("post3")
        assertTrue(repo.isRead("post1"))
        assertTrue(repo.isRead("post2"))
        assertTrue(repo.isRead("post3"))
        assertFalse(repo.isRead("post4"))
    }

    @Test
    fun markAsRead_updatesStateFlow() {
        val repo = createRepo()
        repo.markAsRead("post1")
        assertTrue(repo.readPostIds.value.contains("post1"))
    }

    @Test
    fun markAsRead_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = ReadPostsRepository(settings)
        repo1.markAsRead("post1")
        repo1.markAsRead("post2")

        val repo2 = ReadPostsRepository(settings)
        assertTrue(repo2.isRead("post1"))
        assertTrue(repo2.isRead("post2"))
    }

    @Test
    fun markAsRead_idempotent() {
        val repo = createRepo()
        repo.markAsRead("post1")
        repo.markAsRead("post1")
        assertTrue(repo.isRead("post1"))
        // Should still only have one entry
        assertTrue(repo.readPostIds.value.size == 1)
    }
}
