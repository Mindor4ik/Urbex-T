package com.urbext.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UrbexCompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    showClearButton: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = TextStyle(
        color = UrbexColors.TextPrimary,
        fontSize = 13.sp
    ),
    minHeight: Dp = 36.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) UrbexColors.Green else UrbexColors.Border,
        animationSpec = tween(150),
        label = "compactInputBorder"
    )
    val resolvedTextStyle = textStyle.copy(color = UrbexColors.TextPrimary)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (focused) UrbexColors.GreenBright else UrbexColors.TextDim,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = UrbexColors.TextDim,
                        fontSize = resolvedTextStyle.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = resolvedTextStyle,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(UrbexColors.GreenBright),
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showClearButton && value.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Очистить",
                    tint = UrbexColors.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onValueChange("") }
                )
            }
        }
    }
}

@Composable
fun UrbexCompactMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    minLines: Int = 3,
    minHeight: Dp = 68.dp,
    textStyle: TextStyle = TextStyle(
        color = UrbexColors.TextPrimary,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) UrbexColors.Green else UrbexColors.Border,
        animationSpec = tween(150),
        label = "compactMultilineBorder"
    )
    val resolvedTextStyle = textStyle.copy(color = UrbexColors.TextPrimary)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = UrbexColors.TextDim,
                    fontSize = resolvedTextStyle.fontSize
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                minLines = minLines,
                textStyle = resolvedTextStyle,
                cursorBrush = SolidColor(UrbexColors.GreenBright),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
