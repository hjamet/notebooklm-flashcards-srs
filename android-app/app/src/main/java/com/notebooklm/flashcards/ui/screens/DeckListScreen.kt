package com.notebooklm.flashcards.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notebooklm.flashcards.data.model.DeckSummary
import com.notebooklm.flashcards.data.storage.StorageAccessMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    storageMode: StorageAccessMode,
    decks: List<DeckSummary>,
    totalDueAllDecks: Int,
    onRequestStoragePermission: () -> Unit,
    onRefreshDecks: () -> Unit,
    onStartReview: (deckName: String?) -> Unit,
    onImportCsvUri: (Uri) -> Unit,
    onDeleteDeck: (deckName: String) -> Unit
) {
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportCsvUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NotebookLM Flashcards SRS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = onRefreshDecks) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Storage Status Card
            StorageStatusCard(
                storageMode = storageMode,
                onRequestStoragePermission = onRequestStoragePermission
            )

            Spacer(modifier = Modifier.height(12.dp))

            // NotebookLM CSV Import Button
            FilledTonalButton(
                onClick = { csvPickerLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Importer CSV NotebookLM",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Global Review Button
            if (totalDueAllDecks > 0) {
                Button(
                    onClick = { onStartReview(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Réviser toutes les cartes dues ($totalDueAllDecks)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                "Paquets de Cartes (${decks.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (decks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Aucun paquet trouvé.\nUtilisez le bouton 'Importer CSV NotebookLM' ci-dessus pour importer des cartes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(decks, key = { it.deckName }) { deck ->
                        DeckItemCard(
                            deck = deck,
                            onStartReview = { onStartReview(deck.deckName) },
                            onDeleteDeck = { onDeleteDeck(deck.deckName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStatusCard(
    storageMode: StorageAccessMode,
    onRequestStoragePermission: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (storageMode) {
                is StorageAccessMode.Direct -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                is StorageAccessMode.Saf -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                StorageAccessMode.None -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (storageMode is StorageAccessMode.None) Icons.Default.Folder else Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (storageMode) {
                        is StorageAccessMode.Direct -> "Accès Direct Coffre (/sdcard/Documents/VoiceNotes)"
                        is StorageAccessMode.Saf -> "Accès Coffre via SAF"
                        StorageAccessMode.None -> "Stockage Interne Autonome"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (storageMode) {
                        is StorageAccessMode.Direct -> "Permission All Files Access active"
                        is StorageAccessMode.Saf -> "Mode de repli SAF configuré"
                        StorageAccessMode.None -> "Les cartes sont sauvegardées localement. Cliquez pour synchroniser avec un dossier Obsidian."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (storageMode is StorageAccessMode.None) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestStoragePermission,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Connecter Coffre", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DeckItemCard(
    deck: DeckSummary,
    onStartReview: () -> Unit,
    onDeleteDeck: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer le paquet") },
            text = { Text("Voulez-vous vraiment supprimer le paquet '${deck.deckName}' et ses ${deck.totalCards} cartes ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteDeck()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = deck.deckName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer le paquet",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onStartReview,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Réviser", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeChip(label = "Total: ${deck.totalCards}", color = Color.Gray)
                if (deck.newCards > 0) {
                    BadgeChip(label = "Nouveau: ${deck.newCards}", color = Color(0xFF1E88E5))
                }
                if (deck.dueCards > 0) {
                    BadgeChip(label = "À réviser: ${deck.dueCards}", color = Color(0xFFFB8C00))
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
