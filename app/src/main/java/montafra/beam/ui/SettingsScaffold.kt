package montafra.beam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import montafra.beam.R
import montafra.beam.ui.theme.BeamMaxContentWidth
import montafra.beam.ui.theme.LocalCardSpacing

/**
 * Shared scaffold for settings screens: Android 16 Settings style large top bar
 * whose title shrinks into the pinned bar as the content scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: LazyListScope.() -> Unit,
) {
    val haptic = LocalTapHaptics.current
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        // Keep the header expanded when the content is too short to scroll - but never once it has
        // already collapsed. canScroll gates the whole nested-scroll connection, the expand path
        // included, and collapsing hands 88dp of viewport back to the list: if the content then
        // fits, both canScroll* go false and the header is stranded small with nothing left to
        // scroll. A fling can't recover it either - settleAppBar bails at collapsedFraction == 1f.
        canScroll = {
            listState.canScrollForward || listState.canScrollBackward ||
                topAppBarState.collapsedFraction > 0f
        },
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // safeDrawing rather than the systemBars default: it also covers the display cutout, which
        // sits on a long edge in landscape and would otherwise clip row text.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // The expressive flexible bar rather than the plain large one: same collapse
            // behaviour, but the title gets the emphasized type scale and the taller expanded
            // height Android 16 Settings uses.
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ico_back),
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                // The bar owns its insets separately from the scaffold's; the default is systemBars,
                // which misses the cutout the back arrow would otherwise sit under in landscape.
                windowInsets = WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        // Centred and width-capped so a landscape phone or tablet gets margins instead of rows
        // stretched across the whole display. The top bar stays full width, as Settings does.
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                // Only the top inset is layout padding; the nav-bar inset is folded into
                // contentPadding so rows scroll under the transparent gesture pill.
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = BeamMaxContentWidth)
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding()),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(LocalCardSpacing.current.group),
                contentPadding = PaddingValues(
                    // The scaffold's own start/end insets are added on top of the caller's padding
                    // so rows clear a landscape side nav bar or cutout.
                    start = contentPadding.calculateStartPadding(layoutDirection) +
                        padding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection) +
                        padding.calculateEndPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + padding.calculateBottomPadding(),
                ),
            ) { content() }
        }
    }
}
