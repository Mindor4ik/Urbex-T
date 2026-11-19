package com.urbext.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.urbext.core.backup.ParseUrbexDbResult
import com.urbext.core.backup.UrbexDbSerializer
import com.urbext.domain.model.UrbexDbBundle

/**
 * Interactive .urbexdb manual code authoring template + complete field reference dialog,
 * matching Web DatabaseTemplateModal.tsx.
 */
@Composable
fun DatabaseTemplateDialog(
    onDismiss: () -> Unit,
    onCreateFromTemplate: ((UrbexDbBundle) -> Unit)? = null
) {
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val clipboard = LocalClipboardManager.current
    val templateJson = remember { UrbexDbSerializer.buildTemplateJson() }
    var tab by remember { mutableStateOf("template") }
    var copiedBanner by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isLandscape) 820.dp else 560.dp)
                .heightIn(max = (cfg.screenHeightDp * 0.92f).dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp),
            color = UrbexColors.Surface,
            border = BorderStroke(1.dp, UrbexColors.Border),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pinned Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(18.dp))
                        Column {
                            Text(
                                text = "Схема и шаблон базы .urbexdb",
                                color = UrbexColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ручное создание региональных баз кодом (v1.0.0)",
                                color = UrbexColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = UrbexColors.TextSecondary)
                    }
                }

                HorizontalDivider(color = UrbexColors.Border)

                // Tab bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isTemplateTab = tab == "template"
                    Text(
                        text = "Шаблон JSON",
                        color = if (isTemplateTab) UrbexColors.Background else UrbexColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(if (isTemplateTab) UrbexColors.Green else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (isTemplateTab) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) { tab = "template" }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    Text(
                        text = "Справочник полей",
                        color = if (!isTemplateTab) UrbexColors.Background else UrbexColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(if (!isTemplateTab) UrbexColors.Green else UrbexColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, if (!isTemplateTab) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                            .urbexBounceClick(0.95f) { tab = "schema" }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tab == "template") {
                        Text(
                            text = "При создании файла вручную укажите checksum: \"manual\" — при импорте Urbex-T автоматически пересчитает хэш, подставит недостающие поля и примет данные.",
                            color = UrbexColors.TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.Amber.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )

                        Text(
                            text = templateJson,
                            color = UrbexColors.GreenBright,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    } else {
                        UrbexDbSerializer.SCHEMA_DOCS.forEach { block ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Background, RoundedCornerShape(10.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = block.section,
                                    color = UrbexColors.GreenBright,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                block.fields.forEach { field ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(UrbexColors.Surface, RoundedCornerShape(7.dp))
                                            .border(1.dp, UrbexColors.Border.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = field.name,
                                                color = UrbexColors.TextPrimary,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = field.type,
                                                color = UrbexColors.Amber,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(
                                            text = field.desc,
                                            color = UrbexColors.TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = UrbexColors.Border)

                // Pinned Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(templateJson))
                            copiedBanner = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (copiedBanner) " Скопировано" else " Копировать JSON",
                            fontSize = 11.sp
                        )
                    }

                    if (onCreateFromTemplate != null) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp).weight(1f),
                            onClick = {
                                val parsed = UrbexDbSerializer.parseAndVerify(templateJson)
                                if (parsed is ParseUrbexDbResult.Valid) {
                                    onCreateFromTemplate(parsed.bundle)
                                    onDismiss()
                                }
                            }
                        ) {
                            Text(
                                text = "Создать БД из шаблона",
                                color = UrbexColors.Background,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Закрыть", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
