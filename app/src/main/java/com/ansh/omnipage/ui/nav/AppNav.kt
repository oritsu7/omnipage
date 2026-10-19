package com.ansh.omnipage.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ansh.omnipage.ui.detail.DetailScreen
import com.ansh.omnipage.ui.library.LibraryScreen
import com.ansh.omnipage.ui.notes.AnnotationsScreen
import com.ansh.omnipage.ui.reader.ReaderScreen

private const val LIBRARY = "library"
private const val DETAIL = "book/{id}"
private const val READER = "reader/{id}?loc={loc}"
private const val NOTES = "notes"

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    val readerArgs = idArg + navArgument("loc") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }

    NavHost(navController = nav, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                onOpenBook = { nav.navigate("reader/$it") },
                onOpenDetails = { nav.navigate("book/$it") },
                onOpenNotes = { nav.navigate(NOTES) },
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
        composable(READER, arguments = readerArgs) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val loc = entry.arguments?.getString("loc")
            ReaderScreen(bookId = id, startLocator = loc, onBack = { nav.popBackStack() })
        }
        composable(NOTES) {
            AnnotationsScreen(
                onBack = { nav.popBackStack() },
                onOpen = { bookId, locator -> nav.navigate("reader/$bookId?loc=${Uri.encode(locator)}") },
            )
        }
    }
}