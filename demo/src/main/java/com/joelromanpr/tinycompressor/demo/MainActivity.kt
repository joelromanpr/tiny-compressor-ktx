/*
 * Copyright (C) 2025 joelromanpr (Joel Roman)
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joelromanpr.tinycompressor.demo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Progress
import com.joelromanpr.tinycompressor.Source
import com.joelromanpr.tinycompressor.demo.ui.theme.DemoTheme
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CompressionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun CompressionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var originalUri by remember { mutableStateOf<Uri?>(null) }
    var compressedFile by remember { mutableStateOf<File?>(null) }
    var compressionProgress by remember { mutableStateOf<Progress?>(null) }
    var originalSize by remember { mutableStateOf(0L) }
    var compressedSize by remember { mutableStateOf(0L) }

    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                if (uri != null) {
                    originalUri = uri
                    compressedFile = null
                    compressedSize = 0L
                    originalSize = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0

                    coroutineScope.launch {
                        ImageCompressor
                            .compressAsFlow(context, Source.Uri(uri))
                            .onEach { progress ->
                                compressionProgress = progress
                                if (progress.step.isDone()) {
                                    compressedFile = progress.file
                                    compressedSize = progress.file?.length() ?: 0
                                }
                            }.launchIn(coroutineScope)
                    }
                }
            },
        )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = { imagePicker.launch("image/*") }) {
            Text("Select and Compress Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        compressionProgress?.let {
            Text("Progress: ${it.step} - ${it.percent}%")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            originalUri?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Original Size: ${originalSize / 1024} KB")
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = "Original Image",
                        modifier =
                            Modifier
                                .size(150.dp)
                                .padding(8.dp),
                    )
                }
            }

            compressedFile?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Compressed Size: ${compressedSize / 1024} KB")
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = "Compressed Image",
                        modifier =
                            Modifier
                                .size(150.dp)
                                .padding(8.dp),
                    )
                }
            }
        }
    }
}
