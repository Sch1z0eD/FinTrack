package com.findev.fintrack.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Long-press-and-drag reordering for a [androidx.compose.foundation.lazy.LazyColumn], without a
 * third-party library.
 *
 * The list keeps rendering the caller's own items; this only reports moves. [onMove] should
 * reorder the backing list optimistically (so the drag follows the finger); persistence happens
 * once, on [Modifier.dragContainer]'s onDragEnd. Rows carry [DraggableItem] so the lifted one
 * follows the finger and the rest animate out of its way.
 */
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    /** Index of the row under the finger, or null when nothing is being dragged. */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    private val draggingItemInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    /** How far the lifted row should be shifted from its laid-out position to track the finger. */
    val draggingItemOffset: Float
        get() = draggingItemInfo?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    internal fun onDragStart(offset: Offset) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
            ?.also {
                draggingItemIndex = it.index
                initialOffset = it.offset
            }
    }

    internal fun onDrag(delta: Float) {
        draggedDelta += delta
        val dragging = draggingItemInfo ?: return
        val current = draggingItemIndex ?: return

        // The middle of the lifted row decides which row it is hovering over.
        val startOffset = dragging.offset + draggingItemOffset
        val middle = startOffset + dragging.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != current && middle.toInt() in it.offset..(it.offset + it.size)
        } ?: return

        onMove(current, target.index)
        draggingItemIndex = target.index
    }

    internal fun onDragEnd() {
        draggingItemIndex = null
        draggedDelta = 0f
        initialOffset = 0
    }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): DragDropState {
    // rememberUpdatedState keeps the move fresh without recreating the state (which would drop
    // an in-flight drag) when onMove closes over a list that changes each recomposition.
    val latestOnMove by rememberUpdatedState(onMove)
    return remember(listState) {
        DragDropState(listState) { from, to -> latestOnMove(from, to) }
    }
}

/**
 * Long-press anywhere on the list to pick up the row under the finger and drag it. [onDragEnd]
 * fires once the finger lifts - the place to persist the new order.
 *
 * composed + rememberUpdatedState so the persist callback is always the latest one: the
 * pointerInput block is set up once (keyed on the stable state), but [onDragEnd] closes over the
 * live reordered list, which changes every recomposition. Persisted on cancel too - by then the
 * list has already been reordered optimistically, so dropping the result would lose the move.
 */
fun Modifier.dragContainer(state: DragDropState, onDragEnd: () -> Unit): Modifier = composed {
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(it) },
            onDrag = { change, amount ->
                change.consume()
                state.onDrag(amount.y)
            },
            onDragEnd = {
                latestOnDragEnd()
                state.onDragEnd()
            },
            onDragCancel = {
                latestOnDragEnd()
                state.onDragEnd()
            },
        )
    }
}

/** Wraps a row so the lifted one floats above and follows the finger; others reflow. */
@Composable
fun LazyItemScope.DraggableItem(
    state: DragDropState,
    index: Int,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val dragging = index == state.draggingItemIndex
    val modifier = if (dragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = state.draggingItemOffset }
    } else {
        Modifier.animateItem()
    }
    androidx.compose.foundation.layout.Box(modifier) { content(dragging) }
}
