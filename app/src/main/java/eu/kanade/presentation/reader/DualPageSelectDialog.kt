package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.components.ModeSelectionDialog

/**
 * Dual-page mode selector. Mode values are persisted by the reader activity.
 * 0 = Spread, 1 = Split Scroll.
 */
@Composable
fun DualPageSelectDialog(
    selectedMode: Int,
    onDismissRequest: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var selection by remember(selectedMode) { mutableStateOf(selectedMode.coerceIn(0, 1)) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        ModeSelectionDialog(onApply = { onApply(selection) }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Dual-page", style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = { selection = 0 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selection == 0) "✓  Spread" else "Spread")
                }
                TextButton(
                    onClick = { selection = 1 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selection == 1) "✓  Split Scroll" else "Split Scroll")
                }
            }
        }
    }
}
