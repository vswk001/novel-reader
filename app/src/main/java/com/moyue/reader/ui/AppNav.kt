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
    NavHost(navController = nav, startDestination = "bookshelf") {
        composable("bookshelf") {
            BookshelfScreen(
                onOpenBook = { id -> nav.navigate("reader/$id") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenStats = { nav.navigate("stats") }
            )
        }
        composable("reader/{bookId}") {
            ReaderScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("stats") {
            StatsScreen(onBack = { nav.popBackStack() })
        }
    }
}
