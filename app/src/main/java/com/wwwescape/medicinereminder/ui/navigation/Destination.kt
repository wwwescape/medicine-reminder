package com.wwwescape.medicinereminder.ui.navigation

import com.wwwescape.medicinereminder.R

/** Every screen reachable from the app's own navigation. Ids of 0 mean "new". */
enum class Destination(val route: String, val titleRes: Int) {
    Today("today", R.string.app_name),
    Courses("courses", R.string.title_courses),
    Documents("documents", R.string.title_documents),
    Document("document/{documentId}", R.string.title_document),
    Course("course/{courseId}", R.string.title_course),
    CourseEditor("course_edit/{courseId}", R.string.title_edit_course),
    MedicineEditor("medicine_edit/{courseId}/{medicineId}?copyOf={copyOf}", R.string.title_medicine),
    VisitEditor("visit_edit/{courseId}/{visitId}", R.string.title_visit),
    Settings("settings", R.string.title_settings),
    Licenses("licenses", R.string.section_open_source_licenses);

    companion object {
        const val COURSE_ARG = "courseId"
        const val MEDICINE_ARG = "medicineId"
        const val VISIT_ARG = "visitId"
        const val COPY_OF_ARG = "copyOf"
        const val DOCUMENT_ARG = "documentId"

        fun fromRoute(route: String?): Destination = entries.find { it.route == route } ?: Today

        fun course(courseId: Long) = "course/$courseId"
        fun document(documentId: Long) = "document/$documentId"
        fun courseEditor(courseId: Long = 0) = "course_edit/$courseId"
        /** [copyOf]: start a new medicine as the next step of that one (a taper). */
        fun medicineEditor(courseId: Long, medicineId: Long = 0, copyOf: Long = 0) = "medicine_edit/$courseId/$medicineId?copyOf=$copyOf"

        /** [courseId] 0 = a visit not tied to a course. */
        fun visitEditor(courseId: Long, visitId: Long = 0) = "visit_edit/$courseId/$visitId"
    }
}
