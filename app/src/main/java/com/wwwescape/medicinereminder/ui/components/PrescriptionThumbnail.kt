package com.wwwescape.medicinereminder.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.ui.viewer.ViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMB_PX = 360

/** A preview tile for a stored file: the image itself, or a PDF's first page. */
@Composable
fun PrescriptionThumbnail(
    file: File,
    displayName: String,
    mimeType: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { runCatching { loadThumbnail(file, mimeType, THUMB_PX) }.getOrNull() }
    }
    Column(modifier = modifier.width(112.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            val image = bitmap
            if (image != null) {
                Image(image.asImageBitmap(), contentDescription = displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Description, contentDescription = displayName, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

/** A downscaled preview: an image the right way up, or a PDF's first page. */
internal fun loadThumbnail(file: File, mimeType: String, maxPx: Int): Bitmap? = when {
    mimeType == "application/pdf" -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            renderer.openPage(0).use { page ->
                val scale = maxPx.toFloat() / maxOf(page.width, page.height)
                Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    .apply {
                        eraseColor(AndroidColor.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
            }
        }
    }
    mimeType.startsWith("image/") -> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { uprightFor(file, it) }
    }
    else -> null
}

/** Camera photos are stored as shot, with the rotation in their EXIF data. */
private fun uprightFor(file: File, bitmap: Bitmap): Bitmap {
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}

/** A stored image, or a PDF's first page, loaded off the main thread at up to [maxPx] on its
 * longer side. Anything that can't be previewed shows a document icon. */
@Composable
fun StoredImage(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxPx: Int = 720,
    mimeType: String = "image/jpeg",
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var loaded by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { runCatching { loadThumbnail(file, mimeType, maxPx) }.getOrNull() }
        loaded = true
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val image = bitmap
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            loaded -> Icon(Icons.Rounded.Description, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Opens a stored prescription in the app's own viewer. */
fun openPrescription(context: Context, fileName: String, mimeType: String, title: String) =
    openStoredFile(context, MedicineRepository.prescriptionFile(context, fileName), mimeType, title)

/** Opens a file from app storage in the app's own viewer (images zoom, PDFs page through).
 * [overLockScreen] keeps it above the lock screen, for photos opened from the reminder. */
fun openStoredFile(context: Context, file: File, mimeType: String, title: String, overLockScreen: Boolean = false) =
    ViewerActivity.open(context, file, mimeType, title, overLockScreen)

/** Hands a file from app storage to another app — a chat, email, an optician — read-only. */
fun shareStoredFile(context: Context, file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.toast_no_app, Toast.LENGTH_SHORT).show()
    }
}

/** Opens a file from app storage in another app that can show it, read-only. */
fun openInOtherApp(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.prescription_no_viewer, Toast.LENGTH_SHORT).show()
    }
}
