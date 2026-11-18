package com.urbext.feature.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.BaseSection
import com.urbext.domain.repository.BaseRepository
import kotlinx.coroutines.launch

@Composable
fun BaseScreen(baseRepository: BaseRepository) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf<BaseArticle?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val articles by baseRepository.observeArticles(query)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    AnimatedContent(
        targetState = reading,
        contentKey = { it?.id },
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))) togetherWith fadeOut(tween(140))
        },
        label = "BaseArticleTransition"
    ) { activeArticle ->
        if (activeArticle != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(UrbexColors.Surface, RoundedCornerShape(8.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) { reading = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = UrbexColors.TextPrimary)
                        Text("К списку", color = UrbexColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Row {
                        IconButton(onClick = {
                            val toggled = activeArticle.copy(pinned = !activeArticle.pinned, updatedAt = System.currentTimeMillis())
                            reading = toggled
                            scope.launch { baseRepository.upsertArticle(toggled) }
                        }) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Закрепить",
                                tint = if (activeArticle.pinned) UrbexColors.Amber else UrbexColors.TextSecondary
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                baseRepository.deleteArticle(activeArticle.id)
                                reading = null
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                        }
                    }
                }
                Text(
                    text = activeArticle.section.label,
                    color = if (activeArticle.pinned) UrbexColors.Amber else UrbexColors.GreenBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = activeArticle.title,
                    color = UrbexColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeArticle.content,
                    color = UrbexColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        } else {
            Scaffold(
                containerColor = UrbexColors.Background,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showCreate = true },
                        containerColor = UrbexColors.Green,
                        contentColor = UrbexColors.Background
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить запись")
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск по справочнику и заметкам...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(articles, key = { it.id }) { art ->
                            Column(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                    .urbexBounceClick(0.98f) { reading = art }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = art.section.label,
                                        color = if (art.pinned) UrbexColors.Amber else UrbexColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                    if (art.pinned) {
                                        Text("Закреплено", color = UrbexColors.Amber, fontSize = 11.sp)
                                    }
                                }
                                Text(
                                    text = art.title,
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(art.summary, color = UrbexColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Новая запись в базу", color = UrbexColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Заголовок") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Текст") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        if (title.trim().length < 2 || body.trim().length < 3) return@Button
                        val now = System.currentTimeMillis()
                        scope.launch {
                            baseRepository.upsertArticle(
                                BaseArticle(
                                    id = "base-${now.toString(36)}",
                                    title = title.trim(),
                                    section = BaseSection.FIELD_NOTE,
                                    summary = body.trim().take(80),
                                    content = body.trim(),
                                    updatedAt = now,
                                    pinned = false
                                )
                            )
                            showCreate = false
                        }
                    }
                ) {
                    Text("Сохранить", color = UrbexColors.Background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text("Отмена", color = UrbexColors.TextSecondary)
                }
            }
        )
    }
}
