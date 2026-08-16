package com.example.flashcards.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects a flow in a lifecycle-aware manner using repeatOnLifecycle.
 */
fun <T> Flow<T>.collectIn(
    lifecycleOwner: LifecycleOwner,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (value: T) -> Unit
) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(state) {
            collect { action(it) }
        }
    }
}

/**
 * Simplifies showing a Toast from a context.
 */
fun Context.toast(messageRes: Int, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, messageRes, length).show()
}

/**
 * Sets view visibility based on a predicate.
 */
fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}
