package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PwaDao {
  @Query("SELECT * FROM pwas ORDER BY createdAt DESC")
  fun getAllPwas(): Flow<List<PwaEntity>>

  @Query("SELECT * FROM pwas WHERE id = :id LIMIT 1")
  suspend fun getPwaById(id: String): PwaEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPwa(pwa: PwaEntity)

  @Update
  suspend fun updatePwa(pwa: PwaEntity)

  @Delete
  suspend fun deletePwa(pwa: PwaEntity)

  @Query("DELETE FROM pwas WHERE id = :id")
  suspend fun deleteById(id: String)
}
