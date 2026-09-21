package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SchoolPrimary
import com.example.util.ExportFormat

@Composable
fun MultiFormatExportDialog(
    title: String = "Export Data",
    subtitle: String = "Download formatted reports compatible with Office & Google Workspace",
    recordCount: Int = 0,
    datasetTitle: String = title,
    onDismiss: () -> Unit,
    onExport: (ExportFormat) -> Unit
) {
    val displayTitle = if (datasetTitle.isNotBlank() && datasetTitle != "Export Data") datasetTitle else title
    var selectedFormat by remember { mutableStateOf(ExportFormat.EXCEL_XLS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = SchoolPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$subtitle • $recordCount record${if (recordCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SELECT DOWNLOAD FORMAT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SchoolPrimary,
                    letterSpacing = 1.sp
                )

                FormatOptionItem(
                    format = ExportFormat.EXCEL_XLS,
                    isSelected = selectedFormat == ExportFormat.EXCEL_XLS,
                    icon = Icons.Default.TableChart,
                    accentColor = Color(0xFF15803D),
                    onSelect = { selectedFormat = ExportFormat.EXCEL_XLS },
                    testTag = "export_option_excel_xls"
                )

                FormatOptionItem(
                    format = ExportFormat.WORD_DOC,
                    isSelected = selectedFormat == ExportFormat.WORD_DOC,
                    icon = Icons.Default.Description,
                    accentColor = Color(0xFF2563EB),
                    onSelect = { selectedFormat = ExportFormat.WORD_DOC },
                    testTag = "export_option_word_doc"
                )

                FormatOptionItem(
                    format = ExportFormat.EXCEL_CSV,
                    isSelected = selectedFormat == ExportFormat.EXCEL_CSV,
                    icon = Icons.Default.TableChart,
                    accentColor = Color(0xFF0D9488),
                    onSelect = { selectedFormat = ExportFormat.EXCEL_CSV },
                    testTag = "export_option_excel_csv"
                )

                FormatOptionItem(
                    format = ExportFormat.TEXT_REPORT,
                    isSelected = selectedFormat == ExportFormat.TEXT_REPORT,
                    icon = Icons.Default.Description,
                    accentColor = Color(0xFF475569),
                    onSelect = { selectedFormat = ExportFormat.TEXT_REPORT },
                    testTag = "export_option_text_report"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(selectedFormat) },
                enabled = recordCount > 0,
                modifier = Modifier.testTag("button_confirm_export_download")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download ${selectedFormat.extension.uppercase()}")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("button_cancel_export")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FormatOptionItem(
    format: ExportFormat,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onSelect: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = format.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = format.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}
