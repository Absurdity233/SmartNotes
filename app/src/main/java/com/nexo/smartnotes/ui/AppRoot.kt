package com.nexo.smartnotes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexo.smartnotes.ui.account.AccountScreen
import com.nexo.smartnotes.ui.account.AuthScreen
import com.nexo.smartnotes.ui.detail.DetailScreen
import com.nexo.smartnotes.ui.editor.EditorScreen
import com.nexo.smartnotes.ui.home.ArchiveScreen
import com.nexo.smartnotes.ui.home.HomeScreen
import com.nexo.smartnotes.ui.home.ScheduleScreen
import com.nexo.smartnotes.ui.home.SearchScreen
import com.nexo.smartnotes.ui.home.TrashScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SCHEDULE = "schedule"
    const val TRASH = "trash"
    const val ARCHIVE = "archive"
    const val ACCOUNT = "account"
    const val AUTH = "auth"
    const val EDITOR = "editor/{noteId}?type={type}"
    const val DETAIL = "detail/{noteId}"

    fun editor(noteId: String, type: String? = null): String =
        if (type == null) "editor/$noteId" else "editor/$noteId?type=$type"

    fun detail(noteId: String): String = "detail/$noteId"
}

@Composable
fun AppRoot(appVm: AppViewModel) {
    val booting by appVm.booting.collectAsStateWithLifecycle()
    val message by appVm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            appVm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(!booting, enter = fadeIn(), exit = fadeOut()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                AppNavHost(
                    navController = navController,
                    appVm = appVm,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        if (booting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    appVm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                appVm = appVm,
                onOpenNote = { noteId -> navController.navigate(Routes.detail(noteId)) },
                onNewNote = { type -> navController.navigate(Routes.editor("new", type.id)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId -> navController.navigate(Routes.detail(noteId)) },
            )
        }
        composable(Routes.SCHEDULE) {
            ScheduleScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId -> navController.navigate(Routes.detail(noteId)) },
                onNewSchedule = { navController.navigate(Routes.editor("new", "SCHEDULE")) },
            )
        }
        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId -> navController.navigate(Routes.detail(noteId)) },
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId -> navController.navigate(Routes.detail(noteId)) },
            )
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(
                appVm = appVm,
                onBack = { navController.popBackStack() },
                onOpenAuth = { navController.navigate(Routes.AUTH) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
            )
        }
        composable(Routes.AUTH) {
            AuthScreen(
                appVm = appVm,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val noteId = entry.arguments?.getString("noteId").orEmpty()
            val type = entry.arguments?.getString("type").orEmpty()
            EditorScreen(
                noteId = noteId,
                initialType = type,
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.popBackStack()
                    if (noteId == "new") navController.navigate(Routes.detail(savedId))
                },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) { entry ->
            val noteId = entry.arguments?.getString("noteId").orEmpty()
            DetailScreen(
                noteId = noteId,
                appVm = appVm,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.editor(id)) },
            )
        }
    }
}
