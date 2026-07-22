@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

/**
 * Centred pill tab row atop a library pane. Tabs activate on FOCUS (standard TV
 * pattern) — the pane beneath switches while focus stays on the row; D-pad Down
 * enters the selected tab's content. The group sits in a translucent capsule so the
 * labels stay readable over light hero artwork.
 */
@Composable
internal fun LibraryTabRow(
    tabs: List<LibraryTab>,
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    /** Attached to the SELECTED tab — content's Up-exit lands on it. */
    selectedTabFocus: FocusRequester,
    /** Where Down from the selected tab lands; null = default search (a populated grid enters
     *  its first card spatially). Set to the zero-result Clear button so it stays reachable. */
    contentDownFocus: FocusRequester? = null,
    onFocusedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    Box(
        modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusedChange(it.hasFocus) },
        contentAlignment = Alignment.Center
    ) {
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                key(tab) {
                    Tab(
                        selected = index == selectedIndex,
                        onFocus = { onSelect(tab) },
                        modifier = if (index == selectedIndex) {
                            Modifier
                                .focusRequester(selectedTabFocus)
                                .focusProperties { down = contentDownFocus ?: FocusRequester.Default }
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
