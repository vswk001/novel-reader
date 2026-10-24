package com.moyue.reader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moyue.reader.bookshelf.BookshelfScreen
import com.moyue.reader.reader.ReaderScreen
import com.moyue.reader.settings.SettingsScreen
import com.moyue.reader.stats.StatsScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "bookshelf/0") {
        composable("bookshelf/{space}") { entry ->
            val space = entry.arguments?.getString("space")?.toIntOrNull() ?: 0
            BookshelfScreen(
                onOpenBook = { id -> nav.navigate("reader/$id") },
                onOpenSettings = { nav.navigate("settings/$space") },
                onOpenStats = { s -> nav.navigate("stats/$s") },
                onEnterPrivate = { nav.navigate("bookshelf/1") },
                onBack = if (space == 1) ({ nav.popBackStack() }) else null
            )
        }
        composable("reader/{bookId}") {
            ReaderScreen(onBack = { nav.popBackStack() })
        }
        composable("settings/{space}") { entry ->
            val space = entry.arguments?.getString("space")?.toIntOrNull() ?: 0
            SettingsScreen(space = space, onBack = { nav.popBackStack() })
        }
        composable("stats/{space}") { entry ->
            val space = entry.arguments?.getString("space")?.toIntOrNull() ?: 0
            StatsScreen(space = space, onBack = { nav.popBackStack() })
        }
    }
}
