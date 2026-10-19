package ch.marulabs.owili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
    var draft by remember { mutableStateOf<String?>(null) }
    var pullPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 88.dp.toPx() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(draft) {
        if (draft != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun submit() {
        val value = draft?.trim().orEmpty()
        if (value.isNotEmpty()) add(value)
        draft = null
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ocean)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Pull down here to open a new inline row, as in Clear.
        Box(
            modifier = Modifier.fillMaxWidth().height(104.dp)
                .pointerInput(draft, threshold) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, drag ->
                            if (draft == null) {
                                change.consume()
                                pullPx = (pullPx + drag).coerceIn(0f, threshold * 1.6f)
                            }
                        },
                        onDragEnd = {
                            if (pullPx >= threshold && draft == null) draft = ""
                            pullPx = 0f
                        },
                        onDragCancel = { pullPx = 0f }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Text("owili", color = Color.White, fontSize = 43.sp, fontWeight = FontWeight.Light)
                Text(
                    if (pullPx >= threshold) "LOS LASSEN: NEUER EINTRAG"
                    else "↓ HERUNTERZIEHEN: NEUER EINTRAG",
                    color = sand, fontSize = 11.sp
                )
            }
        }

        if (pullPx > 1f) {
            Box(
                Modifier.fillMaxWidth()
                    .height(with(density) { pullPx.toDp() }.coerceAtMost(100.dp))
                    .background(Color(0xFF19B8AA)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (pullPx >= threshold) "+  Neuer Eintrag" else "↓",
                    color = Color.White, fontSize = 20.sp)
            }
        }

        if (draft != null) {
            BasicTextField(
                value = draft.orEmpty(),
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp)
                    .background(Color(0xFFF6C78F))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = ocean, fontSize = 22.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { inner ->
                    Box {
                        if (draft.isNullOrEmpty()) Text("Was steht an?", color = ocean.copy(alpha = 0.55f))
                        inner()
                    }
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Cards touch each other; the list reads as one fluid surface.
            contentPadding = PaddingValues(0.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                OwiliEntry(entry, toggle, remove)
            }
            if (entries.isEmpty() && draft == null) {
                item {
                    Text("Zieh oben herunter, um deine erste Zeile anzulegen.",
                        modifier = Modifier.fillMaxWidth().padding(26.dp),
                        color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun OwiliEntry(entry: ListEntry, toggle: (String) -> Unit, remove: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var dragAngle by remember(entry.id) { mutableFloatStateOf(0f) }
    val base = if (entry.done) Color(0xFF267E85) else Color(0xFF20B9AE)
    // A front face with a shaded edge: unlike a flat swipe, the entire row tilts in perspective.
    Box(
        modifier = Modifier.fillMaxWidth().height(78.dp)
            .pointerInput(entry.id) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        total += delta
                        dragAngle = (total / size.width * 130f).coerceIn(-72f, 72f)
                    },
                    onDragEnd = {
                        val distance = total / size.width
                        dragAngle = 0f
                        when {
                            distance >= 0.33f -> toggle(entry.id)
                            distance <= -0.33f -> remove(entry.id)
                        }
                    },
                    onDragCancel = { dragAngle = 0f }
                )
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    rotationY = dragAngle
                    cameraDistance = 9f * density
                    transformOrigin = TransformOrigin.Center
                }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            if (dragAngle > 0f) base.copy(alpha = 0.65f) else base,
                            base,
                            if (dragAngle < 0f) base.copy(alpha = 0.65f) else base
                        )
                    )
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                entry.text,
                modifier = Modifier.padding(horizontal = 25.dp),
                fontSize = 21.sp,
                color = if (entry.done) Color.White.copy(alpha = 0.55f) else Color.White,
                fontWeight = FontWeight.Normal
            )
            Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter)
                .background(Color(0xFF004752).copy(alpha = 0.5f)))
            if (entry.done) {
                Text("✓", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
                    color = sand, fontSize = 23.sp)
            }
        }
    }
}
