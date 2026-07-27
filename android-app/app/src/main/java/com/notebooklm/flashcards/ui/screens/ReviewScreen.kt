package com.notebooklm.flashcards.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
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
import com.notebooklm.flashcards.data.model.Rating
import com.notebooklm.flashcards.data.model.SrsCardRecord
import com.notebooklm.flashcards.data.srs.FSRSScheduler
import com.notebooklm.flashcards.ui.components.MathMarkdownText
import com.notebooklm.flashcards.ui.theme.RatingAgainColor
import com.notebooklm.flashcards.ui.theme.RatingEasyColor
import com.notebooklm.flashcards.ui.theme.RatingGoodColor
import com.notebooklm.flashcards.ui.theme.RatingHardColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    dueCards: List<SrsCardRecord>,
    onRecordReview: (cardId: String, rating: Rating) -> Unit,
    onFinishSession: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isAnswerRevealed by remember { mutableStateOf(false) }

    if (dueCards.isEmpty() || currentIndex >= dueCards.size) {
        ReviewCompletedScreen(onFinishSession = onFinishSession)
        return
    }

    val currentCard = dueCards[currentIndex]
    val intervalPreview = remember(currentCard) {
        FSRSScheduler.previewIntervals(currentCard)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentCard.deck,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Carte ${currentIndex + 1} sur ${dueCards.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinishSession) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Bar
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / dueCards.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Flashcard View
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable {
                        if (!isAnswerRevealed) {
                            isAnswerRevealed = true
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "QUESTION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MathMarkdownText(
                        text = currentCard.question,
                        textSizeSp = 19f
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(
                        visible = isAnswerRevealed,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            Text(
                                text = "RÉPONSE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            MathMarkdownText(
                                text = currentCard.answer,
                                textSizeSp = 18f
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions Area
            if (!isAnswerRevealed) {
                Button(
                    onClick = { isAnswerRevealed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Afficher la Réponse", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RatingButton(
                        label = "À Revoir",
                        interval = intervalPreview.again,
                        color = RatingAgainColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRecordReview(currentCard.id, Rating.AGAIN)
                            isAnswerRevealed = false
                            currentIndex++
                        }
                    )
                    RatingButton(
                        label = "Difficile",
                        interval = intervalPreview.hard,
                        color = RatingHardColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRecordReview(currentCard.id, Rating.HARD)
                            isAnswerRevealed = false
                            currentIndex++
                        }
                    )
                    RatingButton(
                        label = "Bon",
                        interval = intervalPreview.good,
                        color = RatingGoodColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRecordReview(currentCard.id, Rating.GOOD)
                            isAnswerRevealed = false
                            currentIndex++
                        }
                    )
                    RatingButton(
                        label = "Facile",
                        interval = intervalPreview.easy,
                        color = RatingEasyColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRecordReview(currentCard.id, Rating.EASY)
                            isAnswerRevealed = false
                            currentIndex++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingButton(
    label: String,
    interval: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
        modifier = modifier.height(64.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = interval,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ReviewCompletedScreen(onFinishSession: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Session Terminée !",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Toutes les cartes dues pour cette session ont été révisées avec succès.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onFinishSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Retour aux Paquets", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
