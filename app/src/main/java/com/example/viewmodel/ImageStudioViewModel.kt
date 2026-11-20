package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.BdAiGenerativeRepository
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Generated(val bitmap: Bitmap, val promptUsed: String) : ImageStudioUiState
    data class Analyzed(val analysisText: String) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

class ImageStudioViewModel(
    private val aiRepo: BdAiGenerativeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _wideLandscape = MutableStateFlow(false)
    val wideLandscape: StateFlow<Boolean> = _wideLandscape.asStateFlow()

    fun setSourceBitmap(bitmap: Bitmap?) {
        _sourceBitmap.value = bitmap
    }

    fun setWideLandscape(wide: Boolean) {
        _wideLandscape.value = wide
    }

    fun generateOrEdit(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _uiState.value = ImageStudioUiState.Error("Please enter a creative prompt or edit instruction.")
            return
        }
        val inputBitmap = _sourceBitmap.value
        val wide = _wideLandscape.value

        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading
            try {
                val outputBitmap = withTimeout(60_000L) {
                    if (inputBitmap != null) {
                        aiRepo.editImage(inputBitmap, cleanPrompt, wide)
                    } else {
                        aiRepo.generateImage(cleanPrompt, wide)
                    }
                }
                if (outputBitmap != null) {
                    _uiState.value = ImageStudioUiState.Generated(outputBitmap, cleanPrompt)
                } else {
                    _uiState.value = ImageStudioUiState.Error("No image was returned by the model.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = ImageStudioUiState.Error("Image generation timed out after 60 seconds.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _uiState.value = ImageStudioUiState.Error("Rate limit or Blaze quota reached. Please try again later.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageStudioUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun analyzeSourceImage(prompt: String) {
        val inputBitmap = _sourceBitmap.value ?: run {
            _uiState.value = ImageStudioUiState.Error("Select an image first to analyze.")
            return
        }
        val query = prompt.trim().ifBlank {
            "Analyze this photo in detail: describe objects, scene, colors, and any text."
        }
        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading
            try {
                val text = withTimeout(60_000L) {
                    aiRepo.analyzeImage(inputBitmap, query)
                }
                if (text.isNotBlank()) {
                    _uiState.value = ImageStudioUiState.Analyzed(text)
                } else {
                    _uiState.value = ImageStudioUiState.Error("Model returned an empty analysis.")
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = ImageStudioUiState.Error("Image analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image analysis failed", e)
                _uiState.value = ImageStudioUiState.Error(e.localizedMessage ?: "Image analysis failed.")
            }
        }
    }

    companion object {
        private const val TAG = "ImageStudioVM"

        fun provideFactory(aiRepo: BdAiGenerativeRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ImageStudioViewModel(aiRepo) }
            }
    }
}
