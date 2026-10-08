package com.pandagallery.app.data.stories

import com.pandagallery.app.data.local.dao.StoryDao
import com.pandagallery.app.data.local.entity.StoryClipEntity
import com.pandagallery.app.data.local.entity.StoryEntity
import com.pandagallery.app.data.story.StoryEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StoryEngineTest {

    private lateinit var fakeStoryDao: FakeStoryDao
    private lateinit var storyEngine: StoryEngine

    @Before
    fun setUp() {
        fakeStoryDao = FakeStoryDao()
        storyEngine = StoryEngine(fakeStoryDao)
    }

    @Test
    fun createHighlightReel_assemblesClipsAndSetsCorrectMetadata() = runBlocking {
        val mediaIds = listOf(101L, 102L, 103L)
        val storyId = storyEngine.createHighlightReel(
            title = "Weekend Memories",
            mediaIds = mediaIds,
            templateName = "Nostalgia",
            clipDurationMs = 2000L
        )

        assertTrue(storyId > 0)
        assertEquals(1, fakeStoryDao.stories.size)
        val savedStory = fakeStoryDao.stories[0]
        assertEquals("Weekend Memories", savedStory.title)
        assertEquals(6, savedStory.durationSeconds) // 3 clips * 2s = 6s
        assertEquals(101L, savedStory.coverMediaId)
        assertEquals("Nostalgia", savedStory.templateName)

        assertEquals(3, fakeStoryDao.clips.size)
        assertEquals(101L, fakeStoryDao.clips[0].mediaId)
        assertEquals(0, fakeStoryDao.clips[0].clipOrder)
        assertEquals(2000L, fakeStoryDao.clips[0].durationMs)
    }

    @Test
    fun deleteStory_removesStoryAndClips() = runBlocking {
        val storyId = storyEngine.createHighlightReel(
            title = "Trip",
            mediaIds = listOf(1L, 2L)
        )
        assertEquals(1, fakeStoryDao.stories.size)
        assertEquals(2, fakeStoryDao.clips.size)

        storyEngine.deleteStory(storyId)
        assertEquals(0, fakeStoryDao.stories.size)
        assertEquals(0, fakeStoryDao.clips.size)
    }

    private class FakeStoryDao : StoryDao {
        val stories = mutableListOf<StoryEntity>()
        val clips = mutableListOf<StoryClipEntity>()

        override fun observeAllStories(): Flow<List<StoryEntity>> = flowOf(stories)
        override suspend fun getStoryById(id: Long): StoryEntity? = stories.find { it.id == id }
        override suspend fun insertStory(story: StoryEntity): Long {
            stories.add(story.copy(id = (stories.size + 1).toLong()))
            return stories.size.toLong()
        }
        override suspend fun deleteStory(story: StoryEntity) { stories.remove(story) }
        override suspend fun deleteStoryById(id: Long) { stories.removeAll { it.id == id } }
        override fun observeClipsForStory(storyId: Long): Flow<List<StoryClipEntity>> =
            flowOf(clips.filter { it.storyId == storyId })
        override suspend fun getClipsForStory(storyId: Long): List<StoryClipEntity> =
            clips.filter { it.storyId == storyId }
        override suspend fun insertClips(clips: List<StoryClipEntity>) { this.clips.addAll(clips) }
        override suspend fun deleteClipsForStory(storyId: Long) { clips.removeAll { it.storyId == storyId } }
    }
}
