package com.wwwescape.medicinereminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.Snapshot
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DocumentCategory
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.HealthDocument
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.PrescriptionFile
import com.wwwescape.medicinereminder.data.schedule.DoseKey
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Activity-scoped: every screen reads the same [snapshot] and derives what it shows from it. */
class MedicineViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()

    val snapshot: StateFlow<Snapshot> = MedicineRepository.snapshot(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Snapshot())

    fun prescriptions(courseId: Long): Flow<List<PrescriptionFile>> = MedicineRepository.prescriptions(app, courseId)

    val documents: StateFlow<List<HealthDocument>> = MedicineRepository.documents(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether files added to a course are also copied into Documents. */
    val saveToDocuments: StateFlow<Boolean> = SettingsRepository.settingsFlow(application)
        .map { it.saveToDocuments }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSaveToDocuments(enabled: Boolean) = launch { SettingsRepository.setSaveToDocuments(app, enabled) }
    fun copyToDocuments(file: PrescriptionFile, course: Course) = launch { MedicineRepository.copyToDocuments(app, file, course) }
    fun deleteDocument(document: HealthDocument) = launch { MedicineRepository.deleteDocument(app, document) }

    /** Adds picked files to Documents. With a single file, [onSingle] gets its id so it can be
     * named and filed straight away. */
    fun importDocuments(uris: List<android.net.Uri>, category: DocumentCategory, onSingle: (Long) -> Unit) = launch {
        val ids = uris.mapNotNull { MedicineRepository.importDocument(app, it, category) }
        ids.singleOrNull()?.let(onSingle)
    }

    fun take(key: DoseKey, amount: Double) = launch { MedicineRepository.logDose(app, key, DoseStatus.TAKEN, amount) }
    fun skip(key: DoseKey, amount: Double) = launch { MedicineRepository.logDose(app, key, DoseStatus.SKIPPED, amount) }
    fun undo(key: DoseKey) = launch { MedicineRepository.clearDose(app, key) }

    /** Logs an as-needed dose at the current minute. */
    fun takeAsNeeded(medicine: Medicine) = launch {
        val now = LocalDateTime.now()
        MedicineRepository.logDose(
            app, DoseKey(medicine.id, LocalDate.now(), now.hour * 60 + now.minute), DoseStatus.TAKEN, medicine.doseAmount,
        )
    }

    fun deleteCourse(course: Course) = launch { MedicineRepository.deleteCourse(app, course) }
    fun deletePrescription(file: PrescriptionFile) = launch { MedicineRepository.deletePrescription(app, file) }

    fun addPrescriptions(courseId: Long, uris: List<android.net.Uri>) = launch {
        uris.forEach { uri ->
            MedicineRepository.importPrescription(app, uri)?.let { MedicineRepository.attachPrescription(app, courseId, it) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
