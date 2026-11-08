package com.wwwescape.medicinereminder.ui.screens.medicine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.ui.components.StoredImage
import com.wwwescape.medicinereminder.ui.components.openStoredFile

/** The pack and dose photos side by side, each taken with the camera or picked from the gallery. */
@Composable
fun MedicinePhotos(editor: MedicineEditorViewModel, modifier: Modifier = Modifier) {
    // At most two 180dp tiles: square tiles half a tablet's width would fill the screen.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.widthIn(max = 372.dp).fillMaxWidth()) {
        PhotoTile(
            editor = editor,
            slot = PhotoSlot.PACKAGE,
            fileName = editor.packagePhoto,
            title = stringResource(R.string.photo_package),
            hint = stringResource(R.string.photo_package_hint),
            modifier = Modifier.weight(1f),
        )
        PhotoTile(
            editor = editor,
            slot = PhotoSlot.DOSE,
            fileName = editor.dosePhoto,
            title = stringResource(R.string.photo_dose),
            hint = stringResource(R.string.photo_dose_hint),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PhotoTile(
    editor: MedicineEditorViewModel,
    slot: PhotoSlot,
    fileName: String?,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    // The camera writes straight into app storage; remembered across the camera app taking over.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val captured = pendingCapture ?: return@rememberLauncherForActivityResult
        pendingCapture = null
        if (taken) editor.setPhoto(slot, captured) else MedicineRepository.photoFile(context, captured).delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.addPhoto(slot, uri)
    }
    fun takePhoto() {
        val file = MedicineRepository.newPhotoFile(context)
        pendingCapture = file.name
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
    }

    Column(modifier = modifier) {
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .clickable { menuOpen = true },
            ) {
                if (fileName != null) {
                    StoredImage(MedicineRepository.photoFile(context, fileName), contentDescription = title, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (fileName != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_view)) },
                        leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            openStoredFile(context, MedicineRepository.photoFile(context, fileName), "image/jpeg", title)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_take_photo)) },
                    leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        takePhoto()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_choose_photo)) },
                    leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                if (fileName != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            editor.setPhoto(slot, null)
                        },
                    )
                }
            }
        }
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}
