package io.njdldkl.android.adbtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CommandOutputBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        SelectionContainer {
            Text(
                text = value.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun StatusLine(label: String, value: String) {
    Text(text = "$label: $value")
}
