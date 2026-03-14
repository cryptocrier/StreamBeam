package com.streambeam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambeam.model.Subtitle
import com.streambeam.ui.theme.TextSecondary

/**
 * Subtitle selector dropdown component for the video player
 */
@Composable
fun SubtitleSelector(
    subtitles: List<Subtitle>,
    selectedSubtitle: Subtitle?,
    isLoading: Boolean,
    onSelectSubtitle: (Subtitle?) -> Unit,
    onDownloadSubtitle: ((Subtitle) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Header button
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = selectedSubtitle?.let { 
                            "${it.getFlag()} ${it.languageName}"
                        } ?: "Subtitles Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    if (selectedSubtitle?.isHearingImpaired == true) {
                        Text(
                            text = "(CC)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Dropdown list
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                )
            ) {
                LazyColumn(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    // Off option
                    item {
                        SubtitleListItem(
                            title = "Off",
                            isSelected = selectedSubtitle == null,
                            onClick = {
                                onSelectSubtitle(null)
                                expanded = false
                            }
                        )
                    }
                    
                    // Divider
                    item {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                    
                    // Subtitle options
                    items(subtitles) { subtitle ->
                        SubtitleListItem(
                            title = "${subtitle.getFlag()} ${subtitle.languageName}",
                            subtitle = subtitle.filename.takeIf { it != "Unknown" },
                            isSelected = selectedSubtitle?.id == subtitle.id,
                            isHearingImpaired = subtitle.isHearingImpaired,
                            downloadCount = subtitle.downloadCount,
                            onClick = {
                                onSelectSubtitle(subtitle)
                                expanded = false
                            },
                            onDownload = onDownloadSubtitle?.let { { it(subtitle) } }
                        )
                    }
                    
                    // Empty state
                    if (subtitles.isEmpty() && !isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No subtitles available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleListItem(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    isHearingImpaired: Boolean = false,
    downloadCount: Int = 0,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else 
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    if (isHearingImpaired) {
                        Text(
                            text = " CC",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (downloadCount > 0) {
                    Text(
                        text = "${formatDownloadCount(downloadCount)} downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else if (onDownload != null) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatDownloadCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}

/**
 * Compact subtitle button for player overlay
 */
@Composable
fun SubtitleButton(
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ClosedCaption,
            contentDescription = "Subtitles",
            tint = if (isActive) 
                MaterialTheme.colorScheme.primary 
            else 
                Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Subtitle settings dialog for styling options
 */
@Composable
fun SubtitleSettingsDialog(
    onDismiss: () -> Unit,
    textSize: Float,
    onTextSizeChange: (Float) -> Unit,
    textColor: Color,
    onTextColorChange: (Color) -> Unit,
    backgroundOpacity: Float,
    onBackgroundOpacityChange: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Text size slider
                Text("Text Size", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = textSize,
                    onValueChange = onTextSizeChange,
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )
                
                // Background opacity slider
                Text("Background Opacity", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = backgroundOpacity,
                    onValueChange = onBackgroundOpacityChange,
                    valueRange = 0f..1f
                )
                
                // Color options
                Text("Text Color", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .clickable { onTextColorChange(color) }
                                .then(
                                    if (textColor == color) {
                                        Modifier.padding(2.dp)
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
