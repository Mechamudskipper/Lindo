package com.example.flashcards.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.flashcards.model.Flashcard

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE isKnown = 0 AND collectionName = :collectionName")
    suspend fun getUnknownCards(collectionName: String): List<Flashcard>

    @Query("SELECT * FROM flashcards WHERE isKnown = 1 AND collectionName = :collectionName")
    suspend fun getKnownCards(collectionName: String): List<Flashcard>

    @Query("SELECT DISTINCT collectionName FROM flashcards")
    suspend fun getAllCollections(): List<String>

    @Query("DELETE FROM flashcards WHERE collectionName = :collectionName")
    suspend fun deleteCollection(collectionName: String)

    @Query("UPDATE flashcards SET collectionName = :newName WHERE collectionName = :oldName")
    suspend fun renameCollection(oldName: String, newName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<Flashcard>)

    @Update
    suspend fun updateCard(card: Flashcard)
}
