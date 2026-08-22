package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.R
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext as withAboutContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PageBackground = Color(0xFF0E1114)

@Composable
internal fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries by produceState<List<Library>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder()
                .withAboutContext(context)
                .build()
                .libraries
                .sortedBy { it.name.lowercase() }
        }
    }
    var selected by remember { mutableStateOf<Library?>(null) }
    var lastViewedId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    BackHandler {
        if (selected != null) selected = null else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 64.dp, vertical = 48.dp)
    ) {
        val sel = selected
        val libs = libraries
        when {
            sel != null -> LicenseDetailPage(sel)
            libs == null -> Text(
                "Loading…",
                style = MaterialTheme.typography.titleMedium,
                color = PicnicColors.OnDarkMuted
            )
            else -> LicenseListPage(
                libraries = libs,
                listState = listState,
                restoreId = lastViewedId,
                onSelect = {
                    lastViewedId = it.uniqueId
                    selected = it
                }
            )
        }
    }
}

@Composable
private fun LicenseListPage(
    libraries: List<Library>,
    listState: LazyListState,
    restoreId: String?,
    onSelect: (Library) -> Unit
) {
    val restoreIndex = libraries.indexOfFirst { it.uniqueId == restoreId }.coerceAtLeast(0)
    val restoreRowFr = remember { FocusRequester() }

    Column {
        Text(
            stringResource(R.string.about_licenses_title),
            style = MaterialTheme.typography.headlineMedium,
            color = PicnicColors.OnDark
        )
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(libraries, key = { _, lib -> lib.uniqueId }) { index, library ->
                LicenseListRow(
                    library = library,
                    rowFocus = if (index == restoreIndex) restoreRowFr else null,
                    blockUp = index == 0,
                    blockDown = index == libraries.lastIndex,
                    onActivate = { onSelect(library) }
                )
            }
        }
    }

    LaunchedEffect(restoreIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == restoreIndex }) {
            listState.scrollToItem(restoreIndex)
        }
        restoreRowFr.requestFocusWhenAttached()
    }
}

@Composable
private fun LicenseListRow(
    library: Library,
    rowFocus: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val version = library.artifactVersion?.let { " $it" }.orEmpty()
    val licenseLabel = library.licenses.firstOrNull()?.let { it.spdxId ?: it.name }.orEmpty()

    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .then(if (rowFocus != null) Modifier.focusRequester(rowFocus) else Modifier)
            .focusProperties {
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onActivate()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${library.name}$version",
            style = MaterialTheme.typography.titleMedium,
            color = PicnicColors.OnDark,
            modifier = Modifier.weight(1f)
        )
        if (licenseLabel.isNotEmpty()) {
            Text(licenseLabel, style = MaterialTheme.typography.titleMedium, color = PicnicColors.Cyan)
        }
    }
}

@Composable
private fun LicenseDetailPage(library: Library) {
    val license = library.licenses.firstOrNull()
    val body = remember(library) { licenseBodyText(library) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollFr = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        Text(
            library.name,
            style = MaterialTheme.typography.headlineMedium,
            color = PicnicColors.OnDark
        )
        license?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(it.spdxId ?: it.name, library.artifactVersion).joinToString("  ·  "),
                style = MaterialTheme.typography.titleMedium,
                color = PicnicColors.Cyan
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .focusRequester(scrollFr)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val page = 240f
                    when (event.key) {
                        Key.DirectionDown -> {
                            scope.launch { scroll.animateScrollBy(page) }
                            true
                        }
                        Key.DirectionUp -> {
                            scope.launch { scroll.animateScrollBy(-page) }
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .verticalScroll(scroll)
                .padding(24.dp)
        ) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = PicnicColors.OnDarkMuted
            )
        }
    }

    LaunchedEffect(library) {
        runCatching { scrollFr.requestFocus() }
    }
}

private fun licenseBodyText(library: Library): String {
    val license = library.licenses.firstOrNull()
    val content = license?.licenseContent
        ?.replace("<br />", "\n")
        ?.replace("<br/>", "\n")
        ?.takeIf { it.isNotBlank() }
    if (content != null) return content
    return buildString {
        appendLine(license?.name ?: "License")
        license?.url?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine()
        append("The full license text is not bundled for this dependency.")
    }
}
