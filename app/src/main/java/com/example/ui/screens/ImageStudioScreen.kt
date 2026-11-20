package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.readBitmapFromUri
import com.example.ui.components.shareBitmap
import com.example.viewmodel.ImageStudioUiState
import com.example.viewmodel.ImageStudioViewModel
import kotlinx.coroutines.launch

private data class StudioPreset(val label: String, val template: String)

private val imagePresets = listOf(
    StudioPreset("Logo Design", "Modern minimalist vector logo for a tech startup named 'BD Tech', clean geometric emerald and cyan emblem on dark slate background"),
    StudioPreset("Poster Design", "Bold cinematic typography-inspired event poster with vibrant neon emerald light trails and futuristic Dhaka skyline silhouette"),
    StudioPreset("Anime Character", "High-detail anime character portrait in a cyberpunk rainy street, expressive lighting, studio quality illustration"),
    StudioPreset("Creative Art", "Surreal isometric floating botanical library bathed in golden hour light, architectural concept art"),
    StudioPreset("Background Edit", "Replace the background with a sunlit tropical coastal terrace while keeping the main subject naturally lit"),
    StudioPreset("Object Edit", "Enhance lighting, remove clutter in the foreground, and add subtle cinematic depth of field")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val wideLandscape by viewModel.wideLandscape.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = readBitmapFromUri(context, uri)
                viewModel.setSourceBitmap(bmp)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Image Studio", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Create logos, posters, anime art, or upload a photo to edit backgrounds, objects, and analyze scenes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imagePresets.forEach { preset ->
                    FilterChip(
                        selected = prompt == preset.template,
                        onClick = { prompt = preset.template },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = {
                    Text(
                        if (sourceBitmap != null) "Describe how to edit or analyze this image"
                        else "Describe the image to generate"
                    )
                },
                placeholder = { Text("e.g., Futuristic Bengali calligraphy emblem in glowing emerald...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_studio_prompt_input"),
                shape = MaterialTheme.shapes.medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !wideLandscape,
                    onClick = { viewModel.setWideLandscape(false) },
                    label = { Text("Square 1:1") }
                )
                FilterChip(
                    selected = wideLandscape,
                    onClick = { viewModel.setWideLandscape(true) },
                    label = { Text("Wide 16:9") }
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("pick_reference_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Pick photo to edit or analyze",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (sourceBitmap == null) "Reference Photo" else "Change Photo")
                }
            }

            if (sourceBitmap != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = sourceBitmap!!.asImageBitmap(),
                            contentDescription = "Selected reference photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reference Image Loaded", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Tap Generate / Edit to transform it, or Analyze to inspect it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setSourceBitmap(null) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove reference image")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.generateOrEdit(prompt) },
                    enabled = uiState !is ImageStudioUiState.Loading,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("generate_image_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (sourceBitmap != null) "Edit Image" else "Generate Image")
                }

                if (sourceBitmap != null) {
                    OutlinedButton(
                        onClick = { viewModel.analyzeSourceImage(prompt) },
                        enabled = uiState !is ImageStudioUiState.Loading,
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("analyze_image_button")
                    ) {
                        Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analyze")
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                when (val state = uiState) {
                    is ImageStudioUiState.Idle -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text("Ready to Create", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Powered by Nano Banana 2.1 for studio creation & editing, and BD AI Flash for photo analysis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ImageStudioUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Synthesizing high-res artwork (may take 20–60s)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ImageStudioUiState.Generated -> {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = "Generated artwork",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { shareBitmap(context, state.bitmap, state.promptUsed) },
                                    modifier = Modifier.testTag("share_generated_image_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share / Save")
                                }
                            }
                        }
                    }
                    is ImageStudioUiState.Analyzed -> {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Photo Analysis", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = state.analysisText,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is ImageStudioUiState.Error -> {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Generation Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
