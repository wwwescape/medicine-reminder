package com.wwwescape.medicinereminder.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.ui.components.loadThumbnail
import com.wwwescape.medicinereminder.ui.components.openInOtherApp
import com.wwwescape.medicinereminder.ui.components.shareStoredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** The longest side images are decoded at: sharp when zoomed on a phone, without loading a
 * 50-megapixel photo whole. */
private const val IMAGE_MAX_PX = 2560

/**
 * A basic viewer for the app's own files: images with pinch-zoom, and PDFs page by page.
 * Share and "Open in another app" hand the file on for anything more. Only files inside the
 * app's storage are opened.
 */
class ViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)?.takeIf { it.isInAppStorage() }
        if (file == null) {
            finish()
            return
        }
        val mimeType = intent.getStringExtra(EXTRA_MIME) ?: "application/octet-stream"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: file.name
        // A medicine photo opened from the full-screen reminder stays over the lock screen,
        // like the reminder itself. Documents never do.
        if (intent.getBooleanExtra(EXTRA_OVER_LOCK_SCREEN, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
        setContent {
            MaterialTheme {
                Viewer(
                    file = file,
                    mimeType = mimeType,
                    title = title,
                    onBack = ::finish,
                    onShare = { shareStoredFile(this, file, mimeType, title) },
                    onOpenElsewhere = { openInOtherApp(this, file, mimeType) },
                )
            }
        }
    }

    private fun File.isInAppStorage(): Boolean = canonicalPath.startsWith(filesDir.canonicalPath + File.separator)

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_OVER_LOCK_SCREEN = "over_lock_screen"

        fun open(context: Context, file: File, mimeType: String, title: String, overLockScreen: Boolean = false) {
            context.startActivity(
                Intent(context, ViewerActivity::class.java)
                    .putExtra(EXTRA_PATH, file.path)
                    .putExtra(EXTRA_MIME, mimeType)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_OVER_LOCK_SCREEN, overLockScreen),
            )
        }
    }
}

@Composable
private fun Viewer(
    file: File,
    mimeType: String,
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenElsewhere: () -> Unit,
) {
    var pageLabel by remember { mutableStateOf<String?>(null) }
    Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                mimeType == "application/pdf" -> PdfPages(file, onPage = { pageLabel = it })
                mimeType.startsWith("image/") -> ImageView(file, mimeType)
                else -> NoPreview(onOpenElsewhere)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .safeDrawingPadding()
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    pageLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f)) }
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
                }
                IconButton(onClick = onOpenElsewhere) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_open_in_other_app))
                }
            }
        }
    }
}

@Composable
private fun ImageView(file: File, mimeType: String) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { runCatching { loadThumbnail(file, mimeType, IMAGE_MAX_PX) }.getOrNull() }
        failed = bitmap == null
    }
    Zoomable(modifier = Modifier.fillMaxSize()) {
        val image = bitmap
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            failed -> Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

/** Every page of a PDF, rendered to the screen's width as it scrolls into view. */
@Composable
private fun PdfPages(file: File, onPage: (String) -> Unit) {
    val pageLabelFormat = stringResource(R.string.viewer_page_of)
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    // PdfRenderer allows one page open at a time, so rendering is serialized.
    val lock = remember { Mutex() }
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    if (renderer == null) {
        Box(Modifier.fillMaxSize()) { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.align(Alignment.Center)) }
        return
    }
    val count = renderer.pageCount
    val ratios = remember(renderer) { (0 until count).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } } }
    val listState = rememberLazyListState()
    val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    LaunchedEffect(current, count) { onPage(pageLabelFormat.format(current, count)) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 88.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count) { index ->
            var page by remember(index) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(index) {
                page = withContext(Dispatchers.IO) {
                    lock.withLock {
                        runCatching {
                            renderer.openPage(index).use { p ->
                                val w = (widthPx * 1.5f).toInt()
                                Bitmap.createBitmap(w, (w / ratios[index]).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
                                    eraseColor(AndroidColor.WHITE)
                                    p.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }.getOrNull()
                    }
                }
            }
            Zoomable(modifier = Modifier.fillMaxWidth().aspectRatio(ratios[index])) {
                page?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun NoPreview(onOpenElsewhere: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        Icon(Icons.Rounded.Description, contentDescription = null)
        Text(stringResource(R.string.viewer_no_preview), modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = onOpenElsewhere) { Text(stringResource(R.string.action_open_in_other_app)) }
    }
}

/**
 * Pinch to zoom (1–5×), drag to pan once zoomed, double-tap to zoom in or back out. At 1× a
 * one-finger drag is left alone, so a page list still scrolls.
 */
@Composable
private fun Zoomable(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                        // Zoom towards the tapped point.
                        val center = Offset(size.width / 2f, size.height / 2f)
                        offset = (center - tap) * (scale - 1f)
                    }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            val moved = offset + event.calculatePan()
                            offset = if (scale == 1f) Offset.Zero else Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            content = content,
        )
    }
}
