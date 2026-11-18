package com.urbext.feature.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.geo.GeoUtils
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.BaseSection
import com.urbext.domain.repository.BaseRepository
import kotlinx.coroutines.launch

@Composable
fun BaseScreen(
    baseRepository: BaseRepository,
    bottomContentPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var sectionFilter by remember { mutableStateOf<BaseSection?>(null) }
    var reading by remember { mutableStateOf<BaseArticle?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val allArticles by baseRepository.observeArticles(query)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val filtered = remember(allArticles, sectionFilter) {
        allArticles
            .filter { sectionFilter == null || it.section == sectionFilter }
            .sortedWith(
                compareByDescending<BaseArticle> { it.pinned }
                    .thenByDescending { it.updatedAt }
            )
    }

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
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
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
                        val pinTint by animateColorAsState(
                            targetValue = if (activeArticle.pinned) UrbexColors.Amber else UrbexColors.TextSecondary,
                            animationSpec = tween(180),
                            label = "pinTint"
                        )
                        val pinInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                val toggled = activeArticle.copy(pinned = !activeArticle.pinned, updatedAt = System.currentTimeMillis())
                                reading = toggled
                                scope.launch { baseRepository.upsertArticle(toggled) }
                            },
                            interactionSource = pinInteraction,
                            modifier = Modifier.urbexScaleOnPress(pinInteraction, 0.90f)
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Закрепить",
                                tint = pinTint
                            )
                        }
                        val delInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    baseRepository.deleteArticle(activeArticle.id)
                                    reading = null
                                }
                            },
                            interactionSource = delInteraction,
                            modifier = Modifier.urbexScaleOnPress(delInteraction, 0.90f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                        }
                    }
                }
                val sectionColor by animateColorAsState(
                    targetValue = if (activeArticle.pinned) UrbexColors.Amber else UrbexColors.GreenBright,
                    animationSpec = tween(180),
                    label = "baseSectionColor"
                )
                Text(
                    text = "${activeArticle.section.label} · Обновлено ${GeoUtils.formatShortDate(activeArticle.updatedAt)}",
                    color = sectionColor,
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
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                floatingActionButton = {
                    val fabInteraction = remember { MutableInteractionSource() }
                    FloatingActionButton(
                        onClick = { showCreate = true },
                        interactionSource = fabInteraction,
                        containerColor = UrbexColors.Green,
                        contentColor = UrbexColors.Background,
                        modifier = Modifier
                            .padding(bottom = bottomContentPadding)
                            .urbexScaleOnPress(fabInteraction, 0.91f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить запись")
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 12.dp, top = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск по инструкциям и заметкам...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all-sections") {
                            SectionFilterChip(
                                label = "Все разделы",
                                selected = sectionFilter == null,
                                onClick = { sectionFilter = null }
                            )
                        }
                        items(BaseSection.entries, key = { it.name }) { sec ->
                            SectionFilterChip(
                                label = sec.label,
                                selected = sectionFilter == sec,
                                onClick = { sectionFilter = sec }
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = filtered.isEmpty(),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
                        label = "BaseEmptyTransition"
                    ) { isEmpty ->
                        if (isEmpty) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Записи не найдены.",
                                    color = UrbexColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 76.dp + bottomContentPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.id }) { art ->
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
                                            Text(
                                                text = (if (art.pinned) "Закреплено · " else "") + GeoUtils.formatShortDate(art.updatedAt),
                                                color = if (art.pinned) UrbexColors.Amber else UrbexColors.TextDim,
                                                fontSize = 11.sp
                                            )
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
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        var section by remember { mutableStateOf(BaseSection.FIELD_NOTE) }
        var summary by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var formError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Добавить материал в локальную базу", color = UrbexColors.TextPrimary, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(visible = formError != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                        Text(formError.orEmpty(), color = Color(0xFFF1ACA7), fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Заголовок материала") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Раздел", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BaseSection.entries, key = { it.name }) { sec ->
                            SectionFilterChip(
                                label = sec.label,
                                selected = section == sec,
                                onClick = { section = sec }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = { Text("Краткая аннотация (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Текст справочника") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    onClick = {
                        if (title.trim().length < 2 || body.trim().length < 4) {
                            formError = "Заполните заголовок и текст справочной записи."
                            return@Button
                        }
                        val now = System.currentTimeMillis()
                        val created = BaseArticle(
                            id = "base-${now.toString(36)}",
                            title = title.trim(),
                            section = section,
                            summary = summary.trim().ifEmpty { body.trim().take(90) },
                            content = body.trim(),
                            updatedAt = now,
                            pinned = false
                        )
                        scope.launch {
                            baseRepository.upsertArticle(created)
                            reading = created
                            showCreate = false
                        }
                    }
                ) {
                    Text("Сохранить в базу", color = UrbexColors.Background)
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

@Composable
private fun SectionFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
        animationSpec = tween(160),
        label = "baseChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
        animationSpec = tween(160),
        label = "baseChipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
        animationSpec = tween(160),
        label = "baseChipText"
    )
    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}
