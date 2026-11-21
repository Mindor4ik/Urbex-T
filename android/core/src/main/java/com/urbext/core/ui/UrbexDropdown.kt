package com.urbext.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class UrbexDropdownOption<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val accentColor: Color? = null
)

@Composable
fun <T> UrbexDropdownField(
    selectedValue: T,
    options: List<UrbexDropdownOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Выбрать...",
    compact: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val selectedOption = remember(selectedValue, options) {
        options.firstOrNull { it.value == selectedValue }
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> UrbexColors.Border
            expanded -> UrbexColors.GreenBright
            else -> UrbexColors.Border
        },
        animationSpec = tween(150),
        label = "dropdownBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (expanded) UrbexColors.Elevated else UrbexColors.Background,
        animationSpec = tween(150),
        label = "dropdownBg"
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(160),
        label = "dropdownArrow"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = UrbexColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { anchorWidthPx = it.width }
                    .heightIn(min = if (compact) 36.dp else 42.dp)
                    .urbexGlow(
                        color = UrbexColors.GreenBright,
                        radius = if (expanded) 13.dp else 8.dp,
                        cornerRadius = 8.dp,
                        alpha = if (expanded) 0.28f else 0.08f,
                        enabled = enabled
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor, RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedOption?.accentColor != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(selectedOption.accentColor, CircleShape)
                    )
                }
                Text(
                    text = selectedOption?.label ?: placeholder,
                    color = if (enabled) UrbexColors.TextPrimary else UrbexColors.TextDim,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!selectedOption?.badge.isNullOrBlank()) {
                    Text(
                        text = selectedOption!!.badge!!,
                        color = UrbexColors.GreenBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .background(UrbexColors.GreenSoft, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Открыть список",
                    tint = if (expanded) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                    modifier = Modifier
                        .size(if (compact) 17.dp else 18.dp)
                        .rotate(arrowRotation)
                )
            }

            val menuWidthDp = with(density) { anchorWidthPx.toDp() }.coerceAtLeast(180.dp)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = UrbexColors.Surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, UrbexColors.BorderStrong),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .width(menuWidthDp)
                    .heightIn(max = 280.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selectedValue
                    DropdownMenuItem(
                        onClick = {
                            onSelect(option.value)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) UrbexColors.GreenSoft else Color.Transparent),
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (option.accentColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(option.accentColor, CircleShape)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = option.label,
                                            color = if (isSelected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (!option.badge.isNullOrBlank()) {
                                            Text(
                                                text = option.badge,
                                                color = UrbexColors.GreenBright,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .background(UrbexColors.Background, RoundedCornerShape(4.dp))
                                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (!option.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = option.subtitle,
                                            color = UrbexColors.TextDim,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = UrbexColors.GreenBright,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
