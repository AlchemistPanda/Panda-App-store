package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.FaceGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceGroupDao {
    @Query("SELECT * FROM face_groups WHERE isDismissed = 0 ORDER BY isPinned DESC, faceCount DESC")
    fun observeActiveGroups(): Flow<List<FaceGroupEntity>>

    @Query("SELECT * FROM face_groups WHERE personKey = :personKey LIMIT 1")
    suspend fun getGroupByPersonKey(personKey: String): FaceGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(group: FaceGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<FaceGroupEntity>)

    @Query("UPDATE face_groups SET name = :name, updatedAt = :updatedAt WHERE personKey = :personKey")
    suspend fun updateName(personKey: String, name: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE face_groups SET isPinned = :isPinned WHERE personKey = :personKey")
    suspend fun updatePinned(personKey: String, isPinned: Boolean)

    @Query("UPDATE face_groups SET isDismissed = 1 WHERE personKey = :personKey")
    suspend fun dismissGroup(personKey: String)

    @Query("DELETE FROM face_groups WHERE personKey = :personKey")
    suspend fun deleteGroup(personKey: String)
}
