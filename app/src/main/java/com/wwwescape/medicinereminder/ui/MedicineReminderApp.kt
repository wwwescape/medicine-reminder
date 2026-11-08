package com.wwwescape.medicinereminder.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.ui.components.CenteredCollapsingTopBar
import com.wwwescape.medicinereminder.ui.navigation.Destination
import com.wwwescape.medicinereminder.ui.screens.courses.CourseDetailScreen
import com.wwwescape.medicinereminder.ui.screens.courses.CourseEditorScreen
import com.wwwescape.medicinereminder.ui.screens.courses.CoursesScreen
import com.wwwescape.medicinereminder.ui.screens.documents.DocumentEditorScreen
import com.wwwescape.medicinereminder.ui.screens.documents.DocumentsScreen
import com.wwwescape.medicinereminder.ui.screens.medicine.MedicineEditorScreen
import com.wwwescape.medicinereminder.ui.screens.settings.LicensesScreen
import com.wwwescape.medicinereminder.ui.screens.settings.SettingsScreen
import com.wwwescape.medicinereminder.ui.screens.today.TodayScreen
import com.wwwescape.medicinereminder.ui.screens.visits.AppointmentEditorScreen

private val TopLevel = listOf(
    Triple(Destination.Today, Icons.Rounded.Today, R.string.title_today),
    Triple(Destination.Courses, Icons.Rounded.MedicalServices, R.string.title_courses),
    Triple(Destination.Documents, Icons.Rounded.Folder, R.string.title_documents),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineReminderApp(navController: NavHostController = rememberNavController()) {
    val medicines: MedicineViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = Destination.fromRoute(currentRoute)
    val isTopLevel = currentRoute == null || TopLevel.any { it.first == currentDestination }
    val isSettingsFamily = currentDestination == Destination.Settings || currentDestination == Destination.Licenses

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val titleText = when (currentDestination) {
        Destination.CourseEditor ->
            if (backStackEntry?.arguments?.getLong(Destination.COURSE_ARG) == 0L) stringResource(R.string.title_new_course)
            else stringResource(R.string.title_edit_course)
        Destination.MedicineEditor -> when {
            backStackEntry?.arguments?.getLong(Destination.COPY_OF_ARG)?.let { it != 0L } == true -> stringResource(R.string.title_next_step)
            backStackEntry?.arguments?.getLong(Destination.MEDICINE_ARG) == 0L -> stringResource(R.string.title_new_medicine)
            else -> stringResource(R.string.title_medicine)
        }
        Destination.VisitEditor ->
            if (backStackEntry?.arguments?.getLong(Destination.VISIT_ARG) == 0L) stringResource(R.string.title_new_visit)
            else stringResource(R.string.title_visit)
        else -> stringResource(currentDestination.titleRes)
    }

    fun navigateTopLevel(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenteredCollapsingTopBar(
                title = titleText,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (isTopLevel) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo_mark),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(32.dp),
                        )
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (!isSettingsFamily) {
                        IconButton(onClick = { navController.navigate(Destination.Settings.route) }) {
                            Icon(imageVector = Icons.Rounded.Settings, contentDescription = stringResource(R.string.title_settings))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevel.forEach { (destination, icon, label) ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = { navigateTopLevel(destination) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Today.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(320)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(320)) },
            popExitTransition = { slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(260)) },
        ) {
            composable(Destination.Today.route) {
                TodayScreen(
                    viewModel = medicines,
                    onNewCourse = { navController.navigate(Destination.courseEditor()) },
                    onOpenVisit = { visit -> navController.navigate(Destination.visitEditor(visit.courseId ?: 0L, visit.id)) },
                )
            }
            composable(Destination.Courses.route) {
                CoursesScreen(
                    viewModel = medicines,
                    onOpenCourse = { navController.navigate(Destination.course(it)) },
                    onNewCourse = { navController.navigate(Destination.courseEditor()) },
                )
            }
            composable(
                route = Destination.Course.route,
                arguments = listOf(navArgument(Destination.COURSE_ARG) { type = NavType.LongType }),
            ) { entry ->
                val courseId = entry.arguments?.getLong(Destination.COURSE_ARG) ?: return@composable
                CourseDetailScreen(
                    courseId = courseId,
                    viewModel = medicines,
                    onEditCourse = { navController.navigate(Destination.courseEditor(courseId)) },
                    onAddMedicine = { navController.navigate(Destination.medicineEditor(courseId)) },
                    onEditMedicine = { navController.navigate(Destination.medicineEditor(courseId, it)) },
                    onAddVisit = { navController.navigate(Destination.visitEditor(courseId)) },
                    onEditVisit = { navController.navigate(Destination.visitEditor(courseId, it)) },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.CourseEditor.route,
                arguments = listOf(navArgument(Destination.COURSE_ARG) { type = NavType.LongType }),
            ) { entry ->
                val courseId = entry.arguments?.getLong(Destination.COURSE_ARG) ?: 0L
                CourseEditorScreen(
                    courseId = courseId,
                    onSaved = { savedId ->
                        if (courseId == 0L) {
                            // A new course goes straight to its page, ready for its first medicine.
                            navController.navigate(Destination.course(savedId)) {
                                popUpTo(Destination.CourseEditor.route) { inclusive = true }
                            }
                            navController.navigate(Destination.medicineEditor(savedId))
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable(
                route = Destination.MedicineEditor.route,
                arguments = listOf(
                    navArgument(Destination.COURSE_ARG) { type = NavType.LongType },
                    navArgument(Destination.MEDICINE_ARG) { type = NavType.LongType },
                    navArgument(Destination.COPY_OF_ARG) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val courseId = entry.arguments?.getLong(Destination.COURSE_ARG) ?: 0L
                MedicineEditorScreen(
                    courseId = courseId,
                    medicineId = entry.arguments?.getLong(Destination.MEDICINE_ARG) ?: 0L,
                    copyOf = entry.arguments?.getLong(Destination.COPY_OF_ARG) ?: 0L,
                    onDone = { navController.popBackStack() },
                    onAddNextStep = { navController.navigate(Destination.medicineEditor(courseId, copyOf = it)) },
                )
            }
            composable(
                route = Destination.VisitEditor.route,
                arguments = listOf(
                    navArgument(Destination.COURSE_ARG) { type = NavType.LongType },
                    navArgument(Destination.VISIT_ARG) { type = NavType.LongType },
                ),
            ) { entry ->
                AppointmentEditorScreen(
                    courseId = entry.arguments?.getLong(Destination.COURSE_ARG) ?: 0L,
                    appointmentId = entry.arguments?.getLong(Destination.VISIT_ARG) ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Documents.route) {
                DocumentsScreen(
                    viewModel = medicines,
                    onOpenDocument = { navController.navigate(Destination.document(it)) },
                )
            }
            composable(
                route = Destination.Document.route,
                arguments = listOf(navArgument(Destination.DOCUMENT_ARG) { type = NavType.LongType }),
            ) { entry ->
                DocumentEditorScreen(
                    documentId = entry.arguments?.getLong(Destination.DOCUMENT_ARG) ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(onNavigateToLicenses = { navController.navigate(Destination.Licenses.route) })
            }
            composable(Destination.Licenses.route) { LicensesScreen() }
        }
    }
}
