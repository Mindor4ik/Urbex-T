package com.urbext.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbext.core.geo.MapPlaceSearch
import com.urbext.core.geo.MapSearchKind
import com.urbext.core.geo.MapSearchResult
import com.urbext.core.geo.MapSearchSource
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.domain.model.UrbexObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun MapSearchCard(
    objects: List<UrbexObject>,
    isLandscape: Boolean,
    onSelectResult: (MapSearchResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var onlineResults by remember { mutableStateOf<List<MapSearchResult>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    val localResults = remember(query, objects) {
        MapPlaceSearch.searchOffline(query, objects, limit = 8)
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || MapPlaceSearch.parseCoordinates(trimmed) != null) {
            onlineResults = emptyList()
            isSearchingOnline = false
            return@LaunchedEffect
        }
        isSearchingOnline = true
        delay(340)
        val remote = withContext(Dispatchers.IO) {
            MapPlaceSearch.fetchOnlineNominatim(trimmed)
        }
        onlineResults = remote
        isSearchingOnline = false
    }

    val combinedResults = remember(query, localResults, onlineResults) {
        if (query.trim().length >= 2) {
            MapPlaceSearch.mergeResults(localResults, onlineResults, limit = 9)
        } else {
            localResults
        }
    }

    val listEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    Column(
        modifier = modifier
            .widthIn(max = if (isLandscape) 360.dp else 340.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UrbexColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
            .animateContentSize(animationSpec = tween(durationMillis = 175, easing = listEase))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Background.copy(alpha = 0.72f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UrbexCompactInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Город, место или координаты...",
                leadingIcon = Icons.Default.Search,
                showClearButton = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        combinedResults.firstOrNull()?.let(onSelectResult)
                    }
                ),
                trailingContent = {
                    AnimatedVisibility(
                        visible = isSearchingOnline,
                        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.75f, animationSpec = tween(120)),
                        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.75f, animationSpec = tween(100))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.8.dp,
                            color = UrbexColors.GreenBright
                        )
                    }
                },
                minHeight = 34.dp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть поиск по карте",
                    tint = UrbexColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(color = UrbexColors.Border)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (isLandscape) 185.dp else 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (combinedResults.isEmpty()) {
                Text(
                    text = if (isSearchingOnline) {
                        "Ищем населённые пункты и места..."
                    } else {
                        "Ничего не найдено. Введите название города или координаты (например: 55.7558, 37.6173)."
                    },
                    color = UrbexColors.TextSecondary,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                )
            } else {
                combinedResults.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        HorizontalDivider(color = UrbexColors.Border.copy(alpha = 0.55f))
                    }
                    MapSearchResultRow(
                        item = item,
                        onClick = { onSelectResult(item) }
                    )
                }
            }
        }

        HorizontalDivider(color = UrbexColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Background.copy(alpha = 0.86f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    query.trim().isEmpty() -> "Города, объекты или широта/долгота"
                    isSearchingOnline -> "Уточняем через OSM Nominatim..."
                    else -> "Офлайн-база + OSM Nominatim"
                },
                color = UrbexColors.TextDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Поиск ↵",
                color = UrbexColors.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MapSearchResultRow(
    item: MapSearchResult,
    onClick: () -> Unit
) {
    val icon = when (item.kind) {
        MapSearchKind.CITY -> Icons.Default.Public
        MapSearchKind.COORD -> Icons.Default.MyLocation
        MapSearchKind.OBJECT -> Icons.Default.Radar
        MapSearchKind.PLACE -> Icons.Default.Place
    }
    val iconTint = when (item.kind) {
        MapSearchKind.CITY, MapSearchKind.OBJECT -> UrbexColors.GreenBright
        MapSearchKind.COORD -> UrbexColors.Amber
        MapSearchKind.PLACE -> UrbexColors.TextSecondary
    }
    val badgeText = if (item.source == MapSearchSource.ONLINE) {
        "OSM"
    } else {
        when (item.kind) {
            MapSearchKind.COORD -> "Коорд."
            MapSearchKind.OBJECT -> "Объект"
            MapSearchKind.CITY -> "Город"
            MapSearchKind.PLACE -> "Место"
        }
    }
    val badgeBg = when {
        item.source == MapSearchSource.ONLINE -> Color(0xFF172124)
        item.kind == MapSearchKind.COORD -> Color(0xFF251E12)
        else -> Color(0xFF18261E)
    }
    val badgeBorder = when {
        item.source == MapSearchSource.ONLINE -> Color(0xFF2C4249)
        item.kind == MapSearchKind.COORD -> Color(0xFF4A381D)
        else -> Color(0xFF2C4737)
    }
    val badgeColor = when {
        item.source == MapSearchSource.ONLINE -> Color(0xFF8AC4D0)
        item.kind == MapSearchKind.COORD -> UrbexColors.Amber
        else -> UrbexColors.GreenBright
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    color = UrbexColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(4.dp))
                        .border(1.dp, badgeBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp
                    )
                }
            }
            Text(
                text = item.subtitle,
                color = UrbexColors.TextSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
