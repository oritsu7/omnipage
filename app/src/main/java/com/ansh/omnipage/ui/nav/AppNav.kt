package com.ansh.omnipage.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ansh.omnipage.ui.detail.DetailScreen
import com.ansh.omnipage.ui.library.LibraryScreen
import com.ansh.omnipage.ui.reader.ReaderScreen

private const val LIBRARY = "library"
private const val DETAIL = "book/{id}"
private const val READER = "reader/{id}"

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(navController = nav, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                onOpenBook = { nav.navigate("reader/$it") },
                onOpenDetails = { nav.navigate("book/$it") },
            )
        }
        composable(DETAIL, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            DetailScreen(
                bookId = id,
                onBack = { nav.popBackStack() },
                onRead = { nav.navigate("reader/$id") },
            )
        }
        composable(READER, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ReaderScreen(bookId = id, onBack = { nav.popBackStack() })
        }
    }
}
