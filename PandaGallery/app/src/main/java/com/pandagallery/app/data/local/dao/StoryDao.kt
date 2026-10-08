package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.StoryClipEntity
import com.pandagallery.app.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    fun observeAllStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE id = :id")
    suspend fun getStoryById(id: Long): StoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity): Long

    @Delete
    suspend fun deleteStory(story: StoryEntity)

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStoryById(id: Long)

    @Query("SELECT * FROM story_clips WHERE storyId = :storyId ORDER BY clipOrder ASC")
    fun observeClipsForStory(storyId: Long): Flow<List<StoryClipEntity>>

    @Query("SELECT * FROM story_clips WHERE storyId = :storyId ORDER BY clipOrder ASC")
    suspend fun getClipsForStory(storyId: Long): List<StoryClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<StoryClipEntity>)

    @Query("DELETE FROM story_clips WHERE storyId = :storyId")
    suspend fun deleteClipsForStory(storyId: Long)
}
