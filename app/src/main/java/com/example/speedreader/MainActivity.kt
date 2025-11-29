package com.example.speedreader

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.speedreader.data.db.AppDatabase
import com.example.speedreader.data.db.PdfBookDao
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
        ).build()
        setContent {
            SpeedReaderTheme {
                val navController = rememberNavController()
                AppNavigation(navController, db.pdfBookDao())
            }
        }
    }
}

@Composable
fun AppNavigation(navController: NavHostController, pdfBookDao: PdfBookDao)
{
    NavHost(navController = navController, startDestination = "library")
    {
        composable("library")
        {
            LibraryScreen(pdfBookDao = pdfBookDao, onPdfSelected = {uri, name ->
                navController.navigate("reader/${Uri.encode(uri.toString())}/$name")
            })
        }

        composable("reader/{pdfUri}/{pdfName}") { backStackEntry ->
            val pdfUri = Uri.parse(backStackEntry.arguments?.getString("pdfUri"))
            val pdfName = backStackEntry.arguments?.getString("pdfName") ?: "Unknown.pdf"

            SpeedReaderScreen(pdfUri, pdfName, pdfBookDao)
        }
    }
}