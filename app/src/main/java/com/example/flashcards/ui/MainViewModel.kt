package com.example.flashcards.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flashcards.data.FlashcardDao
import com.example.flashcards.data.UserRepository
import com.example.flashcards.model.Flashcard
import com.example.flashcards.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.LinkedList

class MainViewModel(
    private val flashcardDao: FlashcardDao,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _user = MutableStateFlow(value = userRepository.getUser())
    val user: StateFlow<User> = _user.asStateFlow()

    private val _currentCard = MutableStateFlow<Flashcard?>(null)
    val currentCard: StateFlow<Flashcard?> = _currentCard.asStateFlow()

    private val _reviewMode = MutableStateFlow(value = false)
    val reviewMode: StateFlow<Boolean> = _reviewMode.asStateFlow()

    private val _currentCollection = MutableStateFlow(value = userRepository.getUser().lastCollection)
    val currentCollection: StateFlow<String> = _currentCollection.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    private val _sideBySide = MutableStateFlow<String?>(null)
    val sideBySide: StateFlow<String?> = _sideBySide.asStateFlow()

    private val _score = MutableStateFlow(value = Score(0, 0, 0))
    val score: StateFlow<Score> = _score.asStateFlow()

    private val _availableCollections = MutableStateFlow<List<String>>(value = emptyList())
    val availableCollections: StateFlow<List<String>> = _availableCollections.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val activeQueue = LinkedList<Flashcard>()
    private val knownCards = mutableListOf<Flashcard>()

    data class ImportResult(val success: Boolean, val count: Int = 0)
    data class Feedback(val isCorrect: Boolean)
    data class Score(val total: Int, val known: Int, val unknown: Int)

    init {
        loadCards()
        loadCollections()
    }

    fun loadCards() {
        viewModelScope.launch(Dispatchers.IO) {
            val collection = _currentCollection.value
            val unknown = flashcardDao.getUnknownCards(collection).shuffled()
            val known = flashcardDao.getKnownCards(collection)

            activeQueue.clear()
            activeQueue.addAll(unknown)
            knownCards.clear()
            knownCards.addAll(known)

            _currentCard.value = null
            if (activeQueue.isNotEmpty()) {
                loadNextCard()
            }
            updateScore()
        }
    }

    private fun loadCollections() {
        viewModelScope.launch(Dispatchers.IO) {
            val collections = flashcardDao.getAllCollections().toMutableList()
            if (!collections.contains("Default")) collections.add("Default")
            _availableCollections.value = collections.sorted()
        }
    }

    fun loadNextCard() {
        if (_reviewMode.value) {
            if (knownCards.isNotEmpty()) {
                _currentCard.value = knownCards.random()
                _feedback.value = null
                _sideBySide.value = null
            } else {
                toggleReviewMode()
            }
            return
        }

        if (activeQueue.isEmpty()) {
            _currentCard.value = null
            return
        }

        _currentCard.value = activeQueue.poll()
        _feedback.value = null
        _sideBySide.value = null
        updateScore()
    }

    fun submitAnswer(input: String) {
        val card = _currentCard.value ?: return
        val userInput = input.trim()

        if (_reviewMode.value) {
            loadNextCard()
            return
        }

        if (userInput == card.nativeText) {
            _feedback.value = Feedback(isCorrect = true)
            markKnown(card)
        } else {
            _feedback.value = Feedback(isCorrect = false)
            // Re-queue card
            val minPos = 2
            val insertionIndex = if (activeQueue.size > minPos) {
                (minPos..minOf(6, activeQueue.size)).random()
            } else {
                activeQueue.size
            }
            activeQueue.add(insertionIndex, card)
        }
        _sideBySide.value = "${card.nativeText} | ${card.targetText}"
    }

    fun markKnown(card: Flashcard) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedCard = card.copy(isKnown = true)
            flashcardDao.updateCard(updatedCard)
            knownCards.add(updatedCard)
            updateScore()
        }
    }

    fun toggleReviewMode() {
        _reviewMode.value = !_reviewMode.value
        loadNextCard()
        updateScore()
    }

    fun saveImportedCards(cards: List<Flashcard>) {
        viewModelScope.launch(Dispatchers.IO) {
            val collection = _currentCollection.value
            val taggedCards = cards.map { it.copy(collectionName = collection) }
            flashcardDao.insertCards(taggedCards)
            loadCards()
        }
    }

    fun renameCollection(newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flashcardDao.renameCollection(_currentCollection.value, newName)
            _currentCollection.value = newName
            userRepository.saveLastCollection(newName)
            loadCards()
            loadCollections()
        }
    }

    fun clearCollection() {
        viewModelScope.launch(Dispatchers.IO) {
            flashcardDao.deleteCollection(_currentCollection.value)
            loadCards()
            loadCollections()
        }
    }

    fun setCollection(collectionName: String) {
        _currentCollection.value = collectionName
        userRepository.saveLastCollection(collectionName)
        loadCards()
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _importResult.value = null
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val newCards = mutableListOf<Flashcard>()
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val parts = line.split("\t")
                            if (parts.size >= 4) {
                                val nativeText = parts[1].trim().removeSurrounding("\"")
                                val targetText = parts[3].trim().removeSurrounding("\"")
                                newCards.add(Flashcard(nativeText = nativeText, targetText = targetText, isKnown = false))
                            }
                            line = reader.readLine()
                        }
                        if (newCards.isNotEmpty()) {
                            saveImportedCards(newCards)
                            _importResult.value = ImportResult(success = true, count = newCards.size)
                        } else {
                            _importResult.value = ImportResult(success = false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error importing cards from $uri", e)
                _importResult.value = ImportResult(success = false)
            }
        }
    }

    private fun updateScore() {
        val known = knownCards.size
        val unknown = activeQueue.size + (if ((_currentCard.value != null) && !(_currentCard.value!!.isKnown)) 1 else 0)
        _score.value = Score(total = known + unknown, known = known, unknown = unknown)
    }

    class Factory(
        private val flashcardDao: FlashcardDao,
        private val userRepository: UserRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(flashcardDao, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
