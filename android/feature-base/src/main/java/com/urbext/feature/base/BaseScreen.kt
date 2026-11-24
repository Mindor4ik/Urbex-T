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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.geo.GeoUtils
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexCompactMultilineField
import com.urbext.core.ui.UrbexDropdownField
import com.urbext.core.ui.UrbexDropdownOption
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexCardGlow
import com.urbext.core.ui.urbexGlow
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.BaseSection
import com.urbext.domain.repository.BaseRepository
import kotlinx.coroutines.launch

@Composable
fun BaseScreen(
    baseRepository: BaseRepository,
    editorEnabled: Boolean = false,
    bottomContentPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    var query by remember { mutableStateOf("") }
    var sectionFilter by remember { mutableStateOf<BaseSection?>(null) }
    var reading by remember { mutableStateOf<BaseArticle?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(editorEnabled) {
        if (!editorEnabled) showCreate = false
    }

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
                    .padding(
                        start = if (isLandscape) 10.dp else 16.dp,
                        top = if (isLandscape) 8.dp else 16.dp,
                        end = if (isLandscape) 10.dp else 16.dp,
                        bottom = (if (isLandscape) 10.dp else 16.dp) + bottomContentPadding
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .urbexCardGlow(
                            color = if (activeArticle.pinned) UrbexColors.Amber else UrbexColors.GreenBright,
                            cornerRadius = 12.dp,
                            alpha = if (activeArticle.pinned) 0.18f else 0.10f
                        )
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(if (isLandscape) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .urbexGlow(UrbexColors.Green, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.16f)
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) { reading = null }
                                .padding(
                                    horizontal = if (isLandscape) 10.dp else 12.dp,
                                    vertical = if (isLandscape) 5.dp else 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = UrbexColors.TextPrimary, modifier = Modifier.size(15.dp))
                            Text("К списку", color = UrbexColors.TextPrimary, fontSize = if (isLandscape) 12.sp else 13.sp, fontWeight = FontWeight.Medium)
                        }
                        if (editorEnabled) {
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
                                    modifier = Modifier
                                        .urbexScaleOnPress(pinInteraction, 0.90f)
                                        .urbexGlow(UrbexColors.Amber, radius = 10.dp, cornerRadius = 18.dp, alpha = 0.28f, enabled = activeArticle.pinned)
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
                                    modifier = Modifier
                                        .urbexScaleOnPress(delInteraction, 0.90f)
                                        .urbexGlow(UrbexColors.Danger, radius = 10.dp, cornerRadius = 18.dp, alpha = 0.22f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = UrbexColors.Danger)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = UrbexColors.Border)

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
                        fontSize = if (isLandscape) 18.sp else 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeArticle.content,
                        color = UrbexColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .padding(
                        start = if (isLandscape) 10.dp else 12.dp,
                        top = if (isLandscape) 8.dp else 12.dp,
                        end = if (isLandscape) 10.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .urbexCardGlow(cornerRadius = 12.dp)
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(if (isLandscape) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
                ) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UrbexCompactInputField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Поиск по инструкциям и заметкам...",
                                leadingIcon = Icons.Default.Search,
                                showClearButton = true,
                                minHeight = 36.dp,
                                modifier = Modifier.weight(1f)
                            )
                            if (editorEnabled) {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                    onClick = { showCreate = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .urbexGlow(UrbexColors.GreenBright, radius = 12.dp, cornerRadius = 8.dp, alpha = 0.30f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Запись", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        UrbexCompactInputField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Поиск по инструкциям и заметкам...",
                            leadingIcon = Icons.Default.Search,
                            showClearButton = true,
                            minHeight = 40.dp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (editorEnabled) {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                                onClick = { showCreate = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .urbexGlow(UrbexColors.GreenBright, radius = 14.dp, cornerRadius = 20.dp, alpha = 0.30f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
                                Text(" Новая запись", color = UrbexColors.Background, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item(key = "all-sections") {
                            SectionFilterChip(
                                label = "Все разделы",
                                selected = sectionFilter == null,
                                compact = isLandscape,
                                onClick = { sectionFilter = null }
                            )
                        }
                        items(BaseSection.entries, key = { it.name }) { sec ->
                            SectionFilterChip(
                                label = sec.label,
                                selected = sectionFilter == sec,
                                compact = isLandscape,
                                onClick = { sectionFilter = sec }
                            )
                        }
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (isLandscape) 2 else 1),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = (if (isLandscape) 12.dp else 24.dp) + bottomContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { art ->
                                Column(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .urbexCardGlow(
                                            color = if (art.pinned) UrbexColors.Amber else UrbexColors.Green,
                                            cornerRadius = 10.dp,
                                            alpha = if (art.pinned) 0.16f else 0.08f
                                        )
                                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                        .urbexBounceClick(0.98f) { reading = art }
                                        .padding(if (isLandscape) 10.dp else 12.dp),
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
                                        fontSize = if (isLandscape) 14.sp else 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = art.summary,
                                        color = UrbexColors.TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = if (isLandscape) 2 else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorEnabled && showCreate) {
        var title by remember { mutableStateOf("") }
        var section by remember { mutableStateOf(BaseSection.FIELD_NOTE) }
        var summary by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var formError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Добавить материал в локальную базу", color = UrbexColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    AnimatedVisibility(visible = formError != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                        Text(formError.orEmpty(), color = Color(0xFFF1ACA7), fontSize = 12.sp)
                    }
                    UrbexCompactInputField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Заголовок материала",
                        placeholder = "Например: Контроль загазованности в коллекторе",
                        minHeight = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexDropdownField(
                        selectedValue = section,
                        label = "Раздел",
                        compact = true,
                        options = BaseSection.entries.map { sec ->
                            UrbexDropdownOption(value = sec, label = sec.label)
                        },
                        onSelect = { section = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexCompactInputField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = "Краткая аннотация (необязательно)",
                        minHeight = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    UrbexCompactMultilineField(
                        value = body,
                        onValueChange = { body = it },
                        label = "Текст справочника",
                        minLines = if (isLandscape) 2 else 3,
                        minHeight = if (isLandscape) 54.dp else 72.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                    modifier = Modifier.urbexGlow(UrbexColors.GreenBright, radius = 12.dp, cornerRadius = 20.dp, alpha = 0.30f),
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
private fun SectionFilterChip(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
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
        fontSize = if (compact) 11.sp else 12.sp,
        modifier = Modifier
            .urbexGlow(UrbexColors.GreenBright, radius = 9.dp, cornerRadius = 8.dp, alpha = 0.25f, enabled = selected)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick)
            .padding(
                horizontal = if (compact) 9.dp else 10.dp,
                vertical = if (compact) 5.dp else 7.dp
            )
    )
}
