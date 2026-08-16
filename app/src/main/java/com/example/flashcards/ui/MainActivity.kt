package com.example.flashcards.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.flashcards.R
import com.example.flashcards.data.AppDatabase
import com.example.flashcards.data.UserRepository
import com.example.flashcards.utils.collectIn
import com.example.flashcards.utils.toast

class MainActivity : AppCompatActivity() {

    // UI view references
    private lateinit var tvTargetSentence: TextView
    private lateinit var etUserAnswer: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvFeedback: TextView
    private lateinit var tvSideBySide: TextView
    private lateinit var btnReviewKnown: Button
    private lateinit var btnImport: Button
    private lateinit var tvScoreTotal: TextView
    private lateinit var tvScoreKnown: TextView
    private lateinit var tvScoreUnknown: TextView
    private lateinit var tvCurrentCollection: TextView
    private lateinit var btnManageCollections: Button

    private val userRepository by lazy { UserRepository(this) }

    // Database and application state
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            flashcardDao = AppDatabase.getDatabase(application).flashcardDao(),
            userRepository = userRepository,
        )
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { readTatoebaFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initializes UI view references
        tvTargetSentence = findViewById(R.id.tvTargetSentence)
        etUserAnswer = findViewById(R.id.etUserAnswer)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvFeedback = findViewById(R.id.tvFeedback)
        tvSideBySide = findViewById(R.id.tvSideBySide)
        btnReviewKnown = findViewById(R.id.btnReviewKnown)
        btnImport = findViewById(R.id.btnImport)
        tvScoreTotal = findViewById(R.id.tvScoreTotal)
        tvScoreKnown = findViewById(R.id.tvScoreKnown)
        tvScoreUnknown = findViewById(R.id.tvScoreUnknown)
        tvCurrentCollection = findViewById(R.id.tvCurrentCollection)
        btnManageCollections = findViewById(R.id.btnManageCollections)

        // Logs current user information for verification
        val user = userRepository.getUser()
        Log.d(TAG, "Current User: ${user.name}")

        setupObservers()

        // Sets click listeners
        btnSubmit.setOnClickListener {
            if (btnSubmit.text == getString(R.string.btn_next_card)) {
                btnSubmit.text = getString(R.string.btn_submit_answer)
                viewModel.loadNextCard()
            } else {
                viewModel.submitAnswer(etUserAnswer.text.toString())
            }
        }

        btnReviewKnown.setOnClickListener {
            viewModel.toggleReviewMode()
        }

        btnImport.setOnClickListener {
            // Opens file manager for TSV files
            filePickerLauncher.launch(
                arrayOf(
                    "text/tab-separated-values",
                ),
            )
        }

        btnManageCollections.setOnClickListener {
            showCollectionManagerDialog()
        }
    }

    private fun setupObservers() {
        viewModel.currentCard.collectIn(this) { card ->
            if (card != null) {
                tvTargetSentence.text = if (viewModel.reviewMode.value) {
                    getString(R.string.review_target_format, card.targetText)
                } else {
                    card.targetText
                }
                etUserAnswer.setText("")
                etUserAnswer.isEnabled = true
                btnSubmit.isEnabled = true
            } else {
                tvTargetSentence.text = getString(R.string.queue_empty_message)
                etUserAnswer.setText("")
                etUserAnswer.isEnabled = false
                btnSubmit.isEnabled = false
            }
        }

        viewModel.feedback.collectIn(this) { feedback ->
            if (feedback != null) {
                val messageId = if (feedback.isCorrect) R.string.feedback_correct else R.string.feedback_incorrect
                tvFeedback.text = getString(messageId)
                val color = if (feedback.isCorrect) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                tvFeedback.setTextColor(ContextCompat.getColor(this, color))

                if (!viewModel.reviewMode.value) {
                    btnSubmit.text = getString(R.string.btn_next_card)
                }
            } else {
                tvFeedback.text = ""
            }
        }

        viewModel.sideBySide.collectIn(this) { text ->
            tvSideBySide.text = text ?: ""
        }

        viewModel.score.collectIn(this) { score ->
            tvScoreTotal.text = getString(R.string.score_total_format, score.total)
            tvScoreKnown.text = getString(R.string.score_known_format, score.known)
            tvScoreUnknown.text = getString(R.string.score_unknown_format, score.unknown)
        }

        viewModel.currentCollection.collectIn(this) { collection ->
            tvCurrentCollection.text = getString(R.string.current_collection_format, collection)
        }

        viewModel.reviewMode.collectIn(this) { isReview ->
            if (isReview) {
                btnReviewKnown.text = getString(R.string.btn_back_to_learning)
                toast(R.string.entering_review_mode)
            } else {
                btnReviewKnown.text = getString(R.string.btn_review_known)
                toast(R.string.resuming_learning_queue)
            }
        }

        viewModel.user.collectIn(this) { user ->
            supportActionBar?.title = "Lindo - ${user.name}"
        }
    }

    private fun showCollectionManagerDialog(sorted: Boolean = false) {
        val collections = if (sorted) {
            viewModel.availableCollections.value.sorted()
        } else {
            viewModel.availableCollections.value
        }
        val actions = arrayOf(
            getString(R.string.btn_save_as),
            getString(R.string.btn_clear_collection),
            getString(R.string.btn_sort_collections),
        )
        val displayList = actions + collections.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_collections)
            .setItems(displayList) { _, which ->
                when {
                    which == 0 -> promptSaveAs()
                    which == 1 -> clearCurrentCollection()
                    which == 2 -> showCollectionManagerDialog(sorted = true)
                    which >= 3 -> {
                        viewModel.setCollection(displayList[which])
                    }
                }
            }
            .show()
    }

    private fun promptSaveAs() {
        val input = EditText(this)
        input.hint = getString(R.string.prompt_new_collection_name)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.btn_save_as)
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameCollection(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCurrentCollection() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear Collection?")
            .setMessage("Delete all cards?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.clearCollection()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // File parsing logic

    private fun readTatoebaFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                viewModel.importFromStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: $uri", e)
            toast(R.string.error_reading_file, length = Toast.LENGTH_LONG)
        }
    }
}
