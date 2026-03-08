package com.example.speedreader

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.speedreader.data.db.AppDatabase
import com.example.speedreader.data.db.PdfBookDao
import com.example.speedreader.data.db.UserStatsDao
import com.example.speedreader.ui.library.LibraryScreen
import com.example.speedreader.ui.reader.EyeTrackingReaderScreen
import com.example.speedreader.ui.reader.FullPdfScreen
import com.example.speedreader.ui.reader.SpeedReaderScreen
import com.example.speedreader.ui.theme.SpeedReaderTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        PDFBoxResourceLoader.init(applicationContext)
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "speedreader-db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        setContent {
            SpeedReaderTheme {
                val navController = rememberNavController()
                AppNavigation(navController, db.pdfBookDao(), db.userStatsDao())
            }
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    pdfBookDao: PdfBookDao,
    userStatsDao: UserStatsDao
) {
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            // MODIFIED: Added onUrlSelected callback here
            LibraryScreen(pdfBookDao = pdfBookDao, userStatsDao = userStatsDao, onPdfSelected = { uri, name ->
                navController.navigate("reader/pdf/${Uri.encode(uri.toString())}/$name")
            }, onUrlSelected = { url ->
                navController.navigate("reader/web/${Uri.encode(url)}/Web Article")
            })
        }

        // MODIFIED: Added {type} to the route and parameters [cite: 4]
        composable("reader/{type}/{uri}/{name}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "pdf"
            val uri = Uri.parse(backStackEntry.arguments?.getString("uri"))
            val name = backStackEntry.arguments?.getString("name") ?: "Unknown"

            SpeedReaderScreen(uri, name, type, pdfBookDao, userStatsDao, navController)
        }

        // MODIFIED: Added {type} to the route and parameters [cite: 5]
        composable("full_reader/{type}/{uri}/{name}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "pdf"
            val uri = Uri.parse(backStackEntry.arguments?.getString("uri"))
            val name = backStackEntry.arguments?.getString("name") ?: "Unknown"

            FullPdfScreen(uri, name, type, pdfBookDao, navController)
        }

        // MODIFIED: Added {type} to the route and parameters [cite: 5, 6]
        composable("eye_tracker/{type}/{uri}/{name}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "pdf"
            val uri = Uri.parse(backStackEntry.arguments?.getString("uri"))
            val name = backStackEntry.arguments?.getString("name") ?: "Unknown"

            EyeTrackingReaderScreen(uri, name, type, navController)
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_stats (
                id INTEGER NOT NULL PRIMARY KEY,
                totalWordsRead INTEGER NOT NULL,
                todayWords INTEGER NOT NULL,
                lastReadDate TEXT NOT NULL,
                streak INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE user_stats ADD COLUMN streakUpdatedDate TEXT NOT NULL DEFAULT ''"
        )
    }
}
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE user_stats ADD COLUMN yesterdayWords INT NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_stats ADD COLUMN themeColor INTEGER NOT NULL DEFAULT -10071900")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_stats ADD COLUMN isBackgroundEnabled INTEGER NOT NULL DEFAULT 0")
    }
}