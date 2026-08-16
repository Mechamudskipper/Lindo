package com.example.flashcards.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flashcards")
data class Flashcard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nativeText: String,
    val targetText: String,
    var isKnown: Boolean = false,
    val collectionName: String = "Default"
)
