package com.notebooklm.flashcards

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.notebooklm.flashcards.data.model.Rating
import com.notebooklm.flashcards.data.model.SrsCardRecord
import com.notebooklm.flashcards.data.storage.SafStorageManager
import com.notebooklm.flashcards.data.storage.SrsDataStore
import com.notebooklm.flashcards.data.storage.StorageAccessMode
import com.notebooklm.flashcards.ui.screens.DeckListScreen
import com.notebooklm.flashcards.ui.screens.ReviewScreen
import com.notebooklm.flashcards.ui.theme.NotebookLMFlashcardsTheme

sealed class Screen {
    object DeckList : Screen()
    data class Review(val deckName: String?) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var storageManager: SafStorageManager
    private lateinit var dataStore: SrsDataStore

    private val safPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            storageManager.saveSafUri(uri)
            Toast.makeText(this, "Dossier coffre sélectionné avec succès", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageManager = SafStorageManager(applicationContext)
        dataStore = SrsDataStore(storageManager)

        setContent {
            NotebookLMFlashcardsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.DeckList) }
                    var storageMode by remember { mutableStateOf(storageManager.getAccessMode()) }

                    val refreshTrigger = remember { mutableIntStateOf(0) }

                    LaunchedEffect(storageMode, refreshTrigger.intValue) {
                        dataStore.syncVault()
                    }

                    val decksSummary by remember(storageMode, refreshTrigger.intValue) {
                        derivedStateOf { dataStore.getDecksSummary() }
                    }

                    val totalDueCards by remember(storageMode, refreshTrigger.intValue) {
                        derivedStateOf { dataStore.getDueCards(null).size }
                    }

                    when (val screen = currentScreen) {
                        is Screen.DeckList -> {
                            DeckListScreen(
                                storageMode = storageMode,
                                decks = decksSummary,
                                totalDueAllDecks = totalDueCards,
                                onRequestStoragePermission = {
                                    if (storageManager.hasDirectStorageAccess()) {
                                        storageMode = storageManager.getAccessMode()
                                    } else {
                                        try {
                                            startActivity(storageManager.createManageStorageIntent())
                                        } catch (e: Exception) {
                                            safPickerLauncher.launch(null)
                                        }
                                    }
                                },
                                onRefreshDecks = {
                                    storageMode = storageManager.getAccessMode()
                                    refreshTrigger.intValue++
                                },
                                onStartReview = { deckName ->
                                    currentScreen = Screen.Review(deckName)
                                }
                            )
                        }
                        is Screen.Review -> {
                            val dueCards = remember(screen.deckName) {
                                dataStore.getDueCards(screen.deckName)
                            }

                            ReviewScreen(
                                dueCards = dueCards,
                                onRecordReview = { cardId, rating ->
                                    dataStore.recordReview(cardId, rating)
                                    refreshTrigger.intValue++
                                },
                                onFinishSession = {
                                    currentScreen = Screen.DeckList
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::storageManager.isInitialized) {
            // Re-check storage mode in case permissions were granted in settings
            val newMode = storageManager.getAccessMode()
            if (newMode != StorageAccessMode.None) {
                dataStore.syncVault()
            }
        }
    }
}
