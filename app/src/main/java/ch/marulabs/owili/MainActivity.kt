package ch.marulabs.owili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Matrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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

private data class ListEntry(val id: String, val text: String, val done: Boolean = false, val deleteArmed: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("owili", MODE_PRIVATE)
        val initial = mutableListOf<ListEntry>()
        runCatching {
            val array = JSONArray(prefs.getString("entries", "[]"))
            repeat(array.length()) {
                val obj = array.getJSONObject(it)
                initial += ListEntry(obj.getString("id"), obj.getString("text"), obj.optBoolean("done"), obj.optBoolean("deleteArmed"))
            }
        }

        setContent {
            val entries = remember { mutableStateListOf<ListEntry>().apply { addAll(initial) } }
            fun save() {
                val array = JSONArray()
                entries.forEach {
                    array.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("deleteArmed", it.deleteArmed))
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
                        entries[index] = entries[index].copy(done = !entries[index].done, deleteArmed = false)
                        save()
                    }
                },
                cancelDelete = { id ->
                    val index = entries.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        entries[index] = entries[index].copy(deleteArmed = false)
                        save()
                    }
                },
                remove = { id ->
                    val index = entries.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        if (entries[index].deleteArmed) entries.removeAt(index)
                        else entries[index] = entries[index].copy(deleteArmed = true)
                        save()
                    }
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
    remove: (String) -> Unit,
    cancelDelete: (String) -> Unit
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
                OwiliEntry(entry, toggle, remove, cancelDelete)
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

/**
 * Four faces of a long square prism. Unlike a card flip, we project all four
 * physical faces and render whichever surfaces face the viewer.
 *
 * Swipe right: front -> checked (bottom); swipe left: front -> red warning (top).
 * A second left swipe on red removes the entry. Swipe right on red cancels.
 */
@Composable
private fun OwiliEntry(entry: ListEntry, toggle: (String) -> Unit, remove: (String) -> Unit, cancelDelete: (String) -> Unit) {
    var drag by remember(entry.id) { mutableFloatStateOf(0f) }
    val restingAngle = when {
        entry.deleteArmed -> -90f
        entry.done -> 90f
        else -> 0f
    }
    val angle by animateFloatAsState(
        targetValue = (restingAngle + drag).coerceIn(-180f, 180f),
        animationSpec = tween(durationMillis = 270),
        label = "cubeTurn"
    )
    val density = LocalDensity.current
    val textSizePx = with(density) { 21.sp.toPx() }

    Canvas(
        modifier = Modifier.fillMaxWidth().height(78.dp)
            .pointerInput(entry.id, entry.deleteArmed, entry.done) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        total += delta
                        drag = (total / size.width * 110f).coerceIn(-85f, 85f)
                    },
                    onDragEnd = {
                        val distance = total / size.width
                        // Snap back on short gestures, settle on the next face on release.
                        if (distance > 0.28f) {
                            if (entry.deleteArmed) cancelDelete(entry.id)
                            else toggle(entry.id)
                        } else if (distance < -0.28f) {
                            if (entry.done) toggle(entry.id)
                            else remove(entry.id)
                        }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val halfW = width / 2f
        val halfH = height / 2f
        val radians = angle * PI.toFloat() / 180f
        val camera = width * 3.5f

        // Four vertical faces of a prism with a square X/Z footprint.
        // Y stays constant during rotation: the prism turns LEFT/RIGHT.
        data class Face(val id: Int, val corners: List<Triple<Float, Float, Float>>, val label: String, val color: Int, val strike: Boolean)
        fun quad(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>) = listOf(
            Triple(a.first, -halfH, a.third),
            Triple(b.first, -halfH, b.third),
            Triple(b.first, halfH, b.third),
            Triple(a.first, halfH, a.third)
        )
        val faces = listOf(
            Face(0, quad(Triple(-halfW, 0f, halfW), Triple(halfW, 0f, halfW)), entry.text, 0xFF20B9AE.toInt(), false),
            // Positive rotation (swipe right) reveals the LEFT face.
            Face(1, quad(Triple(-halfW, 0f, -halfW), Triple(-halfW, 0f, halfW)), entry.text, 0xFF257F82.toInt(), true),
            Face(2, quad(Triple(halfW, 0f, -halfW), Triple(-halfW, 0f, -halfW)), entry.text, 0xFF0C5965.toInt(), false),
            // Negative rotation (swipe left) reveals the RIGHT red face.
            Face(3, quad(Triple(halfW, 0f, halfW), Triple(halfW, 0f, -halfW)), "Löschen?  " + entry.text, 0xFFDC4352.toInt(), false)
        )

        fun rotatedZ(p: Triple<Float, Float, Float>): Float =
            -p.first * sin(radians) + p.third * cos(radians)

        fun project(p: Triple<Float, Float, Float>): Pair<Float, Float> {
            val xx = p.first * cos(radians) + p.third * sin(radians)
            val zz = rotatedZ(p)
            val scale = camera / (camera - zz).coerceAtLeast(camera * 0.5f)
            return Pair(halfW + xx * scale, halfH + p.second * scale)
        }

        // Only front-facing planes belong on screen; otherwise the back side
        // could cover the currently visible face.
        fun isVisible(id: Int): Boolean = when (id) {
            0 -> cos(radians) > 0.001f
            1 -> sin(radians) > 0.001f
            2 -> cos(radians) < -0.001f
            else -> sin(radians) < -0.001f
        }

        drawIntoCanvas { drawCanvas ->
            val native = drawCanvas.nativeCanvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // Draw back-to-front; the visible adjacent face meets the front at an edge.
            faces.filter { isVisible(it.id) }.sortedBy { face -> face.corners.map(::rotatedZ).average() }.forEach { face ->
                val points = face.corners.map(::project)
                val path = Path().apply {
                    moveTo(points[0].first, points[0].second)
                    for (i in 1..3) lineTo(points[i].first, points[i].second)
                    close()
                }
                paint.color = face.color
                paint.style = Paint.Style.FILL
                native.drawPath(path, paint)
                paint.color = 0x33001D26
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = with(density) { 1.dp.toPx() }
                native.drawPath(path, paint)
                paint.style = Paint.Style.FILL

                // Map the face's text plane onto the projected quadrilateral.
                val src = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
                val dst = FloatArray(8) { index ->
                    if (index % 2 == 0) points[index / 2].first else points[index / 2].second
                }
                val matrix = Matrix()
                if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                    native.save()
                    native.concat(matrix)
                    native.clipRect(0f, 0f, width, height)
                    paint.style = Paint.Style.FILL
                    paint.textSize = textSizePx
                    paint.color = android.graphics.Color.WHITE
                    val maxChars = (width / (textSizePx * 0.55f)).toInt().coerceAtLeast(1)
                    val text = face.label.take(maxChars)
                    native.drawText(text, with(density) { 24.dp.toPx() }, height / 2f + textSizePx * .35f, paint)
                    if (face.strike) {
                        val textWidth = paint.measureText(text)
                        paint.strokeWidth = with(density) { 2.dp.toPx() }
                        native.drawLine(with(density) { 24.dp.toPx() }, half, with(density) { 24.dp.toPx() } + textWidth, half, paint)
                    }
                    native.restore()
                }
            }
        }
    }
}
