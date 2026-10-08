package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.RawFormatSupportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawFormatSupportDao {
    @Query("SELECT * FROM raw_format_support")
    fun observeSupportedFormats(): Flow<List<RawFormatSupportEntity>>

    @Query("SELECT * FROM raw_format_support WHERE extension = :extension LIMIT 1")
    suspend fun getFormatInfo(extension: String): RawFormatSupportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(format: RawFormatSupportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(formats: List<RawFormatSupportEntity>)

    @Query("DELETE FROM raw_format_support WHERE extension = :extension")
    suspend fun deleteFormat(extension: String)
}
