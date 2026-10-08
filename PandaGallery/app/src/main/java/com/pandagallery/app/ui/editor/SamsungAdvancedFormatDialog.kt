package com.pandagallery.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pandagallery.app.data.editing.ExportFormat

private val OneUiAccent = Color(0xFF2C6CF5)
private val OneUiCardBg = Color(0xFF1E1E20)
private val OneUiSelectedCardBg = Color(0xFF2C2C2E)
private val OneUiBorderColor = Color(0xFF2C2C2E)

@Composable
fun SamsungAdvancedFormatDialog(
    initialFormat: ExportFormat,
    initialResolutionPercent: Int,
    initialStripLocation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat, Int, Boolean) -> Unit,
) {
    var format by remember { mutableStateOf(initialFormat) }
    var resolutionPercent by remember { mutableIntStateOf(initialResolutionPercent) }
    var stripLocation by remember { mutableStateOf(initialStripLocation) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF18181A),
            border = androidx.compose.foundation.BorderStroke(1.dp, OneUiBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Format and other options",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                )

                // 1. File Format Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "File format",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExportFormat.entries.forEach { f ->
                            val isSelected = f == format
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) OneUiSelectedCardBg else OneUiCardBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 0.8.dp,
                                    color = if (isSelected) OneUiAccent else OneUiBorderColor,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { format = f },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = f.displayName,
                                        color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = OneUiAccent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Resolution Downscaling Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Resolution",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(100 to "Original", 80 to "80%", 60 to "60%", 40 to "40%").forEach { (pct, label) ->
                            val isSelected = pct == resolutionPercent
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) OneUiAccent else OneUiCardBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 0.8.dp,
                                    color = if (isSelected) OneUiAccent else OneUiBorderColor,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { resolutionPercent = pct },
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Strip Location Data Toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = OneUiCardBg,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { stripLocation = !stripLocation },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOff,
                                contentDescription = null,
                                tint = if (stripLocation) OneUiAccent else Color(0xFF8E8E93),
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = "Delete location data",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = "Remove GPS tags from saved photo",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 11.sp,
                                )
                            }
                        }

                        Switch(
                            checked = stripLocation,
                            onCheckedChange = { stripLocation = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = OneUiAccent,
                                uncheckedThumbColor = Color(0xFF8E8E93),
                                uncheckedTrackColor = Color(0xFF2C2C2E),
                            ),
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF8E8E93), fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(format, resolutionPercent, stripLocation) },
                        colors = ButtonDefaults.buttonColors(containerColor = OneUiAccent),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
