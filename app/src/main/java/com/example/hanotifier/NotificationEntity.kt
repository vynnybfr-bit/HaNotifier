package com.example.hanotifier

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val imageUrl: String?,
    val cameraUrl: String?,
    val cameraUrl2: String?,
    val cameraName: String?,
    val cameraName2: String?,
    val timestamp: Long
)
