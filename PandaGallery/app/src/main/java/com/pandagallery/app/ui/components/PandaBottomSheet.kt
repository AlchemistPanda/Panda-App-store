package com.pandagallery.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import com.pandagallery.app.ui.theme.GlassScrim
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: a One UI glass panel rather than a flat filled one.
 *
 * Every sheet in the app should go through this. Material's own [ModalBottomSheet] paints an
 * opaque `containerColor` that would cover the backdrop blur, so the container is transparent here
 * and [Modifier.oneUiGlass] draws the panel instead — blur, top-lit hairline, specular sheen.
 *
 * ## Why the glass is on the content and not on the sheet's own `modifier`
 *
 * It cannot go there. [ModalBottomSheet] slides the sheet up by offsetting it inside
 * `draggableAnchors`, which sits *after* the caller's `modifier` in the chain — so a modifier
 * passed in becomes the offset node's parent and is measured against the full window height, not
 * the panel. A blur applied there frosts the screen from the status bar down to the bottom of the
 * sheet content instead of frosting the panel. Wrapping the content puts the glass inside the
 * offset, where its bounds are exactly the visible panel.
 *
 * That in turn means the drag handle has to move inside the wrapper, which costs the accessibility
 * actions Material attaches around its own `dragHandle` slot — so they are re-declared here.
 *
 * [contentColor] is named explicitly and never derived. `contentColorFor(Color.Transparent)` has no
 * mapping in the scheme and resolves to `LocalContentColor`, which outside a Surface is Material's
 * black default — that is how sheet text ends up black-on-black. The same trap is documented on
 * [PremiumAlertDialog].
 *
 * @param tintAlpha how much the panel hides what is behind it; text-dense sheets want more
 *   coverage than a short action list does.
 * @param tint the glass colour, following the theme by default. Sheets that draw literal white
 *   content rather than scheme colours must pin this to black, or they turn white-on-white under
 *   the light theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PandaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = OneUiGlass.SheetShape,
    tintAlpha: Float = 0.62f,
    tint: Color = if (MaterialTheme.colorScheme.isLightScheme()) Color.White else Color.Black,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    dragHandle: (@Composable () -> Unit)? = { OneUiDragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        // Material's default scrim is light enough that a black sheet on a black-backed screen has
        // nothing to sit against. A denser scrim also gives the blur something to resolve to.
        scrimColor = GlassScrim,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .oneUiGlass(shape = shape, tintAlpha = tintAlpha, tint = tint),
        ) {
            if (dragHandle != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        // Mirrors what Material wraps around its own `dragHandle` slot, which is
                        // skipped above so the handle can sit inside the glass panel.
                        .semantics(mergeDescendants = true) {
                            dismiss {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) onDismissRequest()
                                }
                                true
                            }
                            if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                                expand {
                                    scope.launch { sheetState.expand() }
                                    true
                                }
                            } else {
                                collapse {
                                    scope.launch { sheetState.partialExpand() }
                                    true
                                }
                            }
                        },
                ) { dragHandle() }
            }
            content()
        }
    }
}
