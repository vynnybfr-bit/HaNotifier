package com.example.hanotifier

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(item: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp ASC")
    fun getAllLive(): LiveData<List<NotificationEntity>>

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
