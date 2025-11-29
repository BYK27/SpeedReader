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
        ).addMigrations(MIGRATION_1_2).addMigrations(MIGRATION_2_3).addMigrations(MIGRATION_3_4).build()
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
)
{
    NavHost(navController = navController, startDestination = "library")
    {
        composable("library")
        {
            LibraryScreen(pdfBookDao = pdfBookDao, userStatsDao = userStatsDao, onPdfSelected = {uri, name ->
                navController.navigate("reader/${Uri.encode(uri.toString())}/$name")
            })
        }

        composable("reader/{pdfUri}/{pdfName}") { backStackEntry ->
            val pdfUri = Uri.parse(backStackEntry.arguments?.getString("pdfUri"))
            val pdfName = backStackEntry.arguments?.getString("pdfName") ?: "Unknown.pdf"

            SpeedReaderScreen(pdfUri, pdfName, pdfBookDao, userStatsDao)
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
