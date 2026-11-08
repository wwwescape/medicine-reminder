package com.wwwescape.medicinereminder.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wwwescape.medicinereminder.data.db.MedicineDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export → import on a device, against the app's real database and files. Restoring what was
 * just exported leaves the app as it was. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun exportThenImportRestoresTheSameData() = runBlocking {
        val zip = File(context.cacheDir, "round-trip.zip")
        val exported = Backup.export(context, Uri.fromFile(zip), "test")
        assertTrue(zip.length() > 0)

        val preview = Backup.read(context, Uri.fromFile(zip))
        assertEquals(exported, preview.data)
        Backup.restore(context, preview)

        val dao = MedicineDatabase.get(context).backup()
        assertEquals(exported.courses, dao.courses())
        assertEquals(exported.medicines, dao.medicines())
        assertEquals(exported.doseLogs.toSet(), dao.doseLogs().toSet())
        assertEquals(exported.appointments, dao.appointments())
        assertEquals(exported.documents, dao.documents())
        assertEquals(exported.prescriptionFiles, dao.prescriptionFiles())
        exported.prescriptionFiles.forEach { assertTrue(File(File(context.filesDir, "prescriptions"), it.fileName).isFile) }
        exported.documents.forEach { assertTrue(File(File(context.filesDir, "documents"), it.fileName).isFile) }
    }

    @Test
    fun entriesOutsideTheAppFoldersAreIgnored() = runBlocking {
        val zip = File(context.cacheDir, "crafted.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.text("manifest.json", JSONObject().put("app", "com.wwwescape.medicinereminder").put("format", 1).toString())
            out.text("data.json", BackupFormat.toJson(BackupData()).toString())
            out.text("files/../../evil.txt", "x")
            out.text("files/prescriptions/../../evil.txt", "x")
            out.text("files/other/evil.txt", "x")
        }
        val preview = Backup.read(context, Uri.fromFile(zip))
        assertFalse(File(context.cacheDir, "evil.txt").exists())
        assertFalse(File(context.filesDir, "evil.txt").exists())
        assertTrue(preview.staging.walkTopDown().none { it.name == "evil.txt" })
        Backup.discard(preview)
    }

    @Test
    fun aZipThatIsNotABackupIsRefused() = runBlocking {
        val zip = File(context.cacheDir, "other.zip")
        ZipOutputStream(zip.outputStream()).use { it.text("hello.txt", "hi") }
        try {
            Backup.read(context, Uri.fromFile(zip))
            fail("Expected a BackupException")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason)
        }
    }

    private fun ZipOutputStream.text(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }
}
