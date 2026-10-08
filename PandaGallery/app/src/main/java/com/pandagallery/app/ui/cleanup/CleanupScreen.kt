package com.pandagallery.app.ui.cleanup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandagallery.app.R
import com.pandagallery.app.data.cleanup.CleanupCategory
import com.pandagallery.app.data.cleanup.CleanupReport
import com.pandagallery.app.data.cleanup.CleanupState
import com.pandagallery.app.domain.model.DuplicateKeepRule
import com.pandagallery.app.domain.model.formatFileSize
import com.pandagallery.app.ui.components.PremiumAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    onBack: () -> Unit,
    onReview: (CleanupCategory) -> Unit,
    viewModel: CleanupViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cleanup_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        CleanupContent(
            state = state,
            onScan = viewModel::scan,
            onAutoResolveDuplicates = { rule -> viewModel.autoResolveExactDuplicates(rule) },
            onReview = onReview,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun CleanupContent(
    state: CleanupState,
    onScan: () -> Unit,
    onAutoResolveDuplicates: (DuplicateKeepRule) -> Unit,
    onReview: (CleanupCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CleanupState.NotScanned -> CleanupEmptyState(
            title = stringResource(R.string.cleanup_empty_title),
            body = stringResource(R.string.cleanup_empty_body),
            action = stringResource(R.string.cleanup_scan_action),
            onAction = onScan,
            modifier = modifier,
        )
        CleanupState.Scanning -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.cleanup_scanning_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is CleanupState.Failed -> CleanupEmptyState(
            title = stringResource(R.string.cleanup_failed_title),
            body = state.message,
            action = stringResource(R.string.cleanup_retry_action),
            onAction = onScan,
            modifier = modifier,
        )
        is CleanupState.Ready -> CleanupResults(state.report, onScan, onAutoResolveDuplicates, onReview, modifier)
    }
}

@Composable
private fun CleanupResults(
    report: CleanupReport,
    onScan: () -> Unit,
    onAutoResolveDuplicates: (DuplicateKeepRule) -> Unit,
    onReview: (CleanupCategory) -> Unit,
    modifier: Modifier,
) {
    var showQuickCleanDialog by remember { mutableStateOf(false) }
    val rows = listOf(
        CleanupRow(CleanupCategory.EXACT_DUPLICATES, stringResource(R.string.cleanup_category_exact_duplicates_title), stringResource(R.string.cleanup_category_exact_duplicates_desc), Icons.Outlined.ContentCopy),
        CleanupRow(CleanupCategory.SIMILAR, stringResource(R.string.cleanup_category_similar_title), stringResource(R.string.cleanup_category_similar_desc), Icons.Outlined.AutoAwesomeMosaic),
        CleanupRow(CleanupCategory.BURSTS, stringResource(R.string.cleanup_category_bursts_title), stringResource(R.string.cleanup_category_bursts_desc), Icons.Outlined.BurstMode),
        CleanupRow(CleanupCategory.BLURRY, stringResource(R.string.cleanup_category_blurry_title), stringResource(R.string.cleanup_category_blurry_desc), Icons.Outlined.BlurOn),
        CleanupRow(CleanupCategory.SCREENSHOTS, stringResource(R.string.cleanup_category_screenshots_title), stringResource(R.string.cleanup_category_screenshots_desc), Icons.Outlined.Screenshot),
        CleanupRow(CleanupCategory.DOCUMENTS, stringResource(R.string.cleanup_category_documents_title), stringResource(R.string.cleanup_category_documents_desc), Icons.Outlined.Description),
        CleanupRow(CleanupCategory.LARGE_FILES, stringResource(R.string.cleanup_category_large_files_title), stringResource(R.string.cleanup_category_large_files_desc), Icons.Outlined.Storage),
        CleanupRow(
            CleanupCategory.COMPRESSED_BACKED_UP,
            stringResource(R.string.cleanup_category_compressed_backed_up_title),
            stringResource(R.string.cleanup_category_compressed_backed_up_desc),
            Icons.Outlined.CloudDone,
        ),
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.cleanup_potential_savings_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        formatFileSize(
                            report.duplicateSavingsBytes +
                                report.similarSavingsBytes +
                                report.burstSavingsBytes +
                                report.compressedBackedUpSavingsBytes
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.cleanup_potential_savings_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onScan, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.cleanup_scan_again_action)) }
                }
            }
        }
        if (report.redundantDuplicateIds.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoFixHigh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Quick Clean Duplicates",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${report.redundantDuplicateIds.size} redundant copy${if (report.redundantDuplicateIds.size == 1) "" else "ies"} · Save ${formatFileSize(report.duplicateSavingsBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Text(
                            text = "Keep the best version of each exact duplicate and safely move redundant copies to Trash. All files can be restored within 30 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onReview(CleanupCategory.EXACT_DUPLICATES) }) {
                                Text("Review")
                            }
                            FilledTonalButton(
                                onClick = { showQuickCleanDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Clean now",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        items(rows, key = { it.category.name }) { row ->
            val ids = report.idsFor(row.category)
            CleanupCategoryCard(
                row = row,
                itemCount = ids.size,
                potentialBytes = report.potentialBytes(row.category),
                enabled = ids.isNotEmpty(),
                onClick = { onReview(row.category) },
            )
        }
    }

    if (showQuickCleanDialog) {
        var selectedRule by remember { mutableStateOf(DuplicateKeepRule.LARGEST) }
        PremiumAlertDialog(
            onDismissRequest = { showQuickCleanDialog = false },
            title = { Text("Quick Clean Duplicates?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Move ${report.redundantDuplicateIds.size} byte-identical duplicate files to Trash to reclaim ${formatFileSize(report.duplicateSavingsBytes)}."
                    )
                    Text(
                        "Keep copy:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedRule == DuplicateKeepRule.LARGEST,
                            onClick = { selectedRule = DuplicateKeepRule.LARGEST },
                            label = { Text("Largest") },
                        )
                        FilterChip(
                            selected = selectedRule == DuplicateKeepRule.NEWEST,
                            onClick = { selectedRule = DuplicateKeepRule.NEWEST },
                            label = { Text("Newest") },
                        )
                        FilterChip(
                            selected = selectedRule == DuplicateKeepRule.OLDEST,
                            onClick = { selectedRule = DuplicateKeepRule.OLDEST },
                            label = { Text("Oldest") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAutoResolveDuplicates(selectedRule)
                        showQuickCleanDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCleanDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CleanupCategoryCard(
    row: CleanupRow,
    itemCount: Int,
    potentialBytes: Long,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(row.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${pluralStringResource(R.plurals.cleanup_item_count, itemCount, itemCount)} · ${formatFileSize(potentialBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    row.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun CleanupEmptyState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Outlined.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.padding(top = 4.dp).defaultMinSize(minHeight = 40.dp),
            ) {
                Text(
                    text = action,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class CleanupRow(
    val category: CleanupCategory,
    val title: String,
    val description: String,
    val icon: ImageVector,
)
