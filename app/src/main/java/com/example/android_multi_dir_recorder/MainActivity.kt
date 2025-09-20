package com.example.android_multi_dir_recorder

import android.Manifest
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.android_multi_dir_recorder.ui.theme.AndroidMultiDirRecorderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidMultiDirRecorderTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val tabs = listOf("Categorias", "Grabadora")
    var selectedTab by remember { mutableStateOf(0) }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> CategoriesScreen()
                1 -> RecorderScreen()
            }
        }
    }
}

private val CATEGORIES_KEY = stringSetPreferencesKey("categories")

@Composable
fun CategoriesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    val categoriesFlow: Flow<Set<String>> = remember {
        context.dataStore.data.map { prefs -> prefs[CATEGORIES_KEY] ?: emptySet() }
    }
    var categories by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) {
        categoriesFlow.collect { categories = it.toSortedSet(String.CASE_INSENSITIVE_ORDER) }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Nueva categoría") },
            placeholder = { Text("p.ej. escuela") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (input.isNotBlank()) {
                    val name = input.trim()
                    scope.launch {
                        context.dataStore.edit { prefs ->
                            val current = prefs[CATEGORIES_KEY] ?: emptySet()
                            prefs[CATEGORIES_KEY] = (current + name).toSet()
                        }
                        input = ""
                    }
                }
            }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(16.dp))
        Text("Guardadas:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        if (categories.isEmpty()) {
            Text("Aún no hay categorías.")
        } else {
            categories.forEach { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cat)
                    TextButton(onClick = {
                        scope.launch {
                            context.dataStore.edit { prefs ->
                                val current = prefs[CATEGORIES_KEY] ?: emptySet()
                                prefs[CATEGORIES_KEY] = (current - cat).toSet()
                            }
                        }
                    }) { Text("Eliminar") }
                }
            }
        }
    }
}

@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load categories
    val categoriesFlow: Flow<Set<String>> = remember {
        context.dataStore.data.map { prefs -> prefs[CATEGORIES_KEY] ?: emptySet() }
    }
    var categories by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        categoriesFlow.collect { set ->
            val sorted = set.toMutableList().apply { sortWith(String.CASE_INSENSITIVE_ORDER) }
            categories = sorted
            if (selectedCategory.isBlank() && sorted.isNotEmpty()) selectedCategory = sorted.first()
        }
    }

    var hasMicPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = granted
        if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        CategoryDropdown(categories)
        Spacer(Modifier.size(24.dp))
        RecorderControls(
            canRecord = hasMicPermission && selectedCategory.isNotBlank(),
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )
    }
}

private var mediaRecorder: MediaRecorder? = null
private var isRecording by mutableStateOf(false)
private var selectedCategory by mutableStateOf("")
private var currentRecordingUri: Uri? = null
private var currentPfd: ParcelFileDescriptor? = null

@Composable
fun CategoryDropdown(options: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(selectedCategory) }

    LaunchedEffect(options) {
        if (current.isBlank() && options.isNotEmpty()) {
            current = options.first()
            selectedCategory = current
        } else if (!options.contains(current)) {
            current = ""
            selectedCategory = ""
        }
    }

    Column {
        Text("Categoría:")
        Card(modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = options.isNotEmpty()) { expanded = !expanded }) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (current.isBlank()) "Selecciona" else current, modifier = Modifier.weight(1f))
                Text("▼")
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                options.forEach { opt ->
                    Text(
                        opt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = opt
                                selectedCategory = opt
                                expanded = false
                            }
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecorderControls(canRecord: Boolean, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun startRecording() {
        try {
            val category = selectedCategory.ifBlank { "General" }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val displayName = "REC_${'$'}timestamp.m4a"

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Recordings/MultiDirRecorder/${'$'}category")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Media.DATE_TAKEN, System.currentTimeMillis())
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear el archivo en MediaStore")
            currentRecordingUri = uri
            val pfd = resolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("No se pudo abrir el descriptor de archivo")
            currentPfd = pfd

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            mediaRecorder = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.prepare()
            recorder.start()
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
            // Clean up any pending file
            try {
                currentPfd?.close()
            } catch (_: Exception) {}
            currentPfd = null
            currentRecordingUri?.let { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Mark not pending and then delete
                    val resolver = context.contentResolver
                    try {
                        resolver.update(uri, ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }, null, null)
                    } catch (_: Exception) {}
                }
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }
            currentRecordingUri = null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
            isRecording = false
            try { currentPfd?.close() } catch (_: Exception) {}
            currentPfd = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                currentRecordingUri?.let { uri ->
                    try {
                        context.contentResolver.update(uri, ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }, null, null)
                    } catch (_: Exception) {}
                }
            }
            currentRecordingUri = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                if (!canRecord) {
                    onRequestPermission(); return@Button
                }
                if (!isRecording) startRecording() else stopRecording()
            },
            enabled = canRecord
        ) {
            Text(if (isRecording) "Detener" else "Grabar")
        }
        Spacer(Modifier.size(12.dp))
        Text(
            when {
                !canRecord -> "Permiso de micrófono requerido"
                selectedCategory.isBlank() -> "Selecciona una categoría"
                isRecording -> "Grabando..."
                else -> "Listo"
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AndroidMultiDirRecorderTheme { App() }
}