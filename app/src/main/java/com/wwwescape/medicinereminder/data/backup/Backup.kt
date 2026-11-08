package com.wwwescape.medicinereminder.data.backup

import android.content.Context
import android.net.Uri
import com.wwwescape.medicinereminder.data.db.MedicineDatabase
import com.wwwescape.medicinereminder.data.photos
import com.wwwescape.medicinereminder.data.reminders.AppointmentReminders
import com.wwwescape.medicinereminder.data.reminders.Reminders
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A checked backup, unpacked and ready to replace the app's data, or to be discarded. */
class BackupPreview internal constructor(
    val data: BackupData,
    /** When the backup was made, if it says. */
    val exportedAt: Instant?,
    internal val staging: File,
)

class BackupException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION, INCOMPLETE, UNREADABLE }
}

/**
 * Export and import of everything in the app as one ZIP:
 *
 *     manifest.json            what made it, when, and the format version
 *     data.json                every course, medicine, dose, visit, document and setting
 *     files/prescriptions/…    course prescription files
 *     files/photos/…           medicine photos
 *     files/documents/…        Documents
 *
 * Import replaces what's in the app. It unpacks and checks the whole backup first, so a bad or
 * partial file never touches the existing data.
 */
object Backup {

    private const val APP_ID = "com.wwwescape.medicinereminder"
    private const val MANIFEST = "manifest.json"
    private const val DATA = "data.json"
    private const val FILES = "files/"
    /** The app-storage folders a backup may hold files for; nothing else is ever written. */
    private val FILE_DIRS = listOf("prescriptions", "photos", "documents")
    private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    suspend fun export(context: Context, target: Uri, appVersion: String): BackupData = withContext(Dispatchers.IO) {
        val dao = MedicineDatabase.get(context).backup()
        val data = BackupData(
            courses = dao.courses(),
            medicines = dao.medicines(),
            prescriptionFiles = dao.prescriptionFiles(),
            doseLogs = dao.doseLogs(),
            appointments = dao.appointments(),
            documents = dao.documents(),
            settings = SettingsRepository.allPreferences(context),
        )
        val manifest = JSONObject()
            .put("app", APP_ID)
            .put("format", BackupFormat.FORMAT_VERSION)
            .put("appVersion", appVersion)
            .put("exportedAt", Instant.now().toString())
        val output = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("Can't write $target")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putText(MANIFEST, manifest.toString(2))
            zip.putText(DATA, BackupFormat.toJson(data).toString(2))
            referencedFiles(data).forEach { (dir, name) ->
                val file = File(File(context.filesDir, dir), name)
                if (!file.isFile) return@forEach
                zip.putNextEntry(ZipEntry("$FILES$dir/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        data
    }

    /** Unpacks and checks [source] without changing anything. Call [restore] or [discard] after. */
    suspend fun read(context: Context, source: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "backup-import").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            var manifest: JSONObject? = null
            var dataJson: JSONObject? = null
            val input = context.contentResolver.openInputStream(source) ?: throw BackupException(BackupException.Reason.UNREADABLE)
            ZipInputStream(input.buffered()).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when (val name = entry.name) {
                        MANIFEST -> manifest = JSONObject(zip.readText())
                        DATA -> dataJson = JSONObject(zip.readText())
                        else -> {
                            val target = stagedFileFor(staging, name) ?: continue
                            total += zip.copyLimited(target)
                            if (total > MAX_TOTAL_BYTES) throw BackupException(BackupException.Reason.UNREADABLE)
                        }
                    }
                }
            }
            val header = manifest ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            if (header.optString("app") != APP_ID) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            if (header.optInt("format", 0) > BackupFormat.FORMAT_VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
            val data = BackupFormat.fromJson(dataJson ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP))
            val missing = referencedFiles(data).any { (dir, name) -> !File(File(staging, dir), name).isFile }
            if (missing) throw BackupException(BackupException.Reason.INCOMPLETE)
            BackupPreview(data, header.optString("exportedAt").let { runCatching { Instant.parse(it) }.getOrNull() }, staging)
        } catch (e: BackupException) {
            staging.deleteRecursively()
            throw e
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw BackupException(BackupException.Reason.UNREADABLE, e)
        }
    }

    /** Replaces everything in the app with [preview]: data in one transaction, then the files,
     * then the settings, and finally the reminders are set again from the new data. */
    suspend fun restore(context: Context, preview: BackupPreview) = withContext(Dispatchers.IO) {
        val data = preview.data
        MedicineDatabase.get(context).backup().replaceAll(
            courses = data.courses,
            medicines = data.medicines,
            prescriptionFiles = data.prescriptionFiles,
            doseLogs = data.doseLogs,
            appointments = data.appointments,
            documents = data.documents,
        )
        FILE_DIRS.forEach { dir ->
            val live = File(context.filesDir, dir)
            live.deleteRecursively()
            live.mkdirs()
            File(preview.staging, dir).listFiles()?.forEach { staged ->
                val target = File(live, staged.name)
                if (!staged.renameTo(target)) staged.copyTo(target, overwrite = true)
            }
        }
        if (data.settings.isNotEmpty()) SettingsRepository.restorePreferences(context, data.settings)
        preview.staging.deleteRecursively()
        Reminders.reschedule(context)
        AppointmentReminders.reschedule(context)
    }

    fun discard(preview: BackupPreview) {
        preview.staging.deleteRecursively()
    }

    /** Every (folder, file name) the data refers to. */
    private fun referencedFiles(data: BackupData): Set<Pair<String, String>> = buildSet {
        data.prescriptionFiles.forEach { add("prescriptions" to it.fileName) }
        data.documents.forEach { add("documents" to it.fileName) }
        data.medicines.forEach { m -> m.photos().forEach { add("photos" to it) } }
    }

    /** Where a ZIP entry goes in staging, or null if it isn't one of ours. Only a plain file
     * name directly inside one of [FILE_DIRS] is accepted, so a crafted entry like
     * `files/../../x` can never write outside the staging folder. */
    private fun stagedFileFor(staging: File, entryName: String): File? {
        if (!entryName.startsWith(FILES)) return null
        val parts = entryName.removePrefix(FILES).split('/')
        if (parts.size != 2) return null
        val (dir, name) = parts
        if (dir !in FILE_DIRS || name.isBlank() || name == "." || name == ".." || '\\' in name) return null
        return File(File(staging, dir).apply { mkdirs() }, name)
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun InputStream.readText(): String = readBytes().toString(Charsets.UTF_8)

    /** Copies the current entry to [target], refusing anything over [MAX_ENTRY_BYTES]. */
    private fun ZipInputStream.copyLimited(target: File): Long {
        var written = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                written += read
                if (written > MAX_ENTRY_BYTES) throw BackupException(BackupException.Reason.UNREADABLE)
                out.write(buffer, 0, read)
            }
        }
        return written
    }
}
