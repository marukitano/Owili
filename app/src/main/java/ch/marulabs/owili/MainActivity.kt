package ch.marulabs.owili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val ocean = Color(0xFF073D4B)
private val aqua = Color(0xFF29C5B9)
private val sand = Color(0xFFF6C78F)

private data class ListEntry(val id: String, val text: String, val done: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("owili", MODE_PRIVATE)
        val initial = mutableListOf<ListEntry>()
        runCatching {
            val array = JSONArray(prefs.getString("entries", "[]"))
            repeat(array.length()) {
                val obj = array.getJSONObject(it)
                initial += ListEntry(obj.getString("id"), obj.getString("text"), obj.optBoolean("done"))
            }
        }

        setContent {
            val entries = remember { mutableStateListOf<ListEntry>().apply { addAll(initial) } }
            fun save() {
                val array = JSONArray()
                entries.forEach {
                    array.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done))
                }
                prefs.edit().putString("entries", array.toString()).apply()
            }
            OwiliScreen(
                entries = entries,
                add = { value ->
                    if (value.isNotBlank()) {
                        entries.add(0, ListEntry(UUID.randomUUID().toString(), value.trim()))
                        save()
                    }
                },
                toggle = { id ->
                    val index = entries.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        entries[index] = entries[index].copy(done = !entries[index].done)
                        save()
                    }
                },
                remove = { id ->
                    entries.removeAll { it.id == id }
                    save()
                }
            )
        }
    }
}

@Composable
private fun OwiliScreen(
    entries: List<ListEntry>,
    add: (String) -> Unit,
    toggle: (String) -> Unit,
    remove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(ocean, Color(0xFF0A6672), Color(0xFF159D9F))))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            "owili", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            "Less tapping. More flowing.", color = sand, fontSize = 13.sp,
            modifier = Modifier.padding(start = 27.dp, bottom = 28.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(start = 18.dp, end = 8.dp)
        ) {
            BasicTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f).padding(vertical = 15.dp),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = Brush.verticalGradient(listOf(sand, sand)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    add(input); input = ""; keyboard?.hide()
                }),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text("Neuer Eintrag …", color = Color.White.copy(alpha = 0.65f))
                        inner()
                    }
                }
            )
            TextButton(onClick = { add(input); input = ""; keyboard?.hide() }) {
                Text("+", color = sand, fontSize = 28.sp)
            }
        }
        Text(
            "NACH RECHTS: ERLEDIGT  ·  NACH LINKS: LÖSCHEN",
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 14.dp),
            color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp
        )
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Deine Liste beginnt hier.", color = Color.White.copy(alpha = 0.85f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    OwiliEntry(entry, toggle, remove)
                }
            }
        }
    }
}

@Composable
private fun OwiliEntry(entry: ListEntry, toggle: (String) -> Unit, remove: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val rotation = remember(entry.id) { Animatable(0f) }
    val background = if (entry.done) Color(0xFF417A80) else aqua
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 14f * density
            }
            .background(background, RoundedCornerShape(12.dp))
            .pointerInput(entry.id) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        totalDrag += drag
                        scope.launch { rotation.snapTo((totalDrag / size.width * 55f).coerceIn(-40f, 40f)) }
                    },
                    onDragEnd = {
                        val distance = totalDrag / size.width
                        when {
                            distance > 0.32f -> toggle(entry.id)
                            distance < -0.32f -> remove(entry.id)
                        }
                        scope.launch { rotation.animateTo(0f, spring()) }
                    },
                    onDragCancel = { scope.launch { rotation.animateTo(0f, spring()) } }
                )
            }
            .padding(horizontal = 20.dp, vertical = 23.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            (if (entry.done) "✓  " else "") + entry.text,
            fontSize = 20.sp,
            color = if (entry.done) Color.White.copy(alpha = 0.7f) else ocean,
            fontWeight = FontWeight.Medium
        )
    }
}
