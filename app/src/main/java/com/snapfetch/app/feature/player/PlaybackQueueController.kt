package com.snapfetch.app.feature.player

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RepeatMode { OFF, ALL, ONE }

@Immutable
data class QueueTrack(
    val mediaKey: String,
    val title: String,
    val subtitle: String,
    val uri: String,
    val thumbnailUrl: String = "",
    val durationSec: Int = 0,
    val isLocal: Boolean = true
)

@Immutable
data class PlaybackQueueState(
    val tracks: List<QueueTrack> = emptyList(),
    val currentIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val currentTrack: QueueTrack? get() = tracks.getOrNull(currentIndex)
}

class PlaybackQueueController {
    private val _state = MutableStateFlow(PlaybackQueueState())
    val state: StateFlow<PlaybackQueueState> = _state.asStateFlow()

    fun setQueue(
        tracks: List<QueueTrack>,
        startIndex: Int = 0,
        shuffle: Boolean? = null,
        repeat: RepeatMode? = null
    ) {
        val safeIdx = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                tracks = tracks,
                currentIndex = safeIdx,
                shuffleEnabled = shuffle ?: it.shuffleEnabled,
                repeatMode = repeat ?: it.repeatMode
            )
        }
    }

    fun selectIndex(index: Int): QueueTrack? {
        val tracks = _state.value.tracks
        if (index !in tracks.indices) return null
        _state.update { it.copy(currentIndex = index) }
        return tracks[index]
    }

    fun toggleShuffle() {
        _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun cycleRepeat() {
        _state.update {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        _state.update { s ->
            if (fromIndex !in s.tracks.indices || toIndex !in s.tracks.indices || fromIndex == toIndex) return@update s
            val mutable = s.tracks.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            val currentId = s.currentTrack?.mediaKey
            val newIdx = mutable.indexOfFirst { it.mediaKey == currentId }.coerceAtLeast(0)
            s.copy(tracks = mutable, currentIndex = newIdx)
        }
    }

    fun removeAt(index: Int) {
        _state.update { s ->
            if (index !in s.tracks.indices) return@update s
            val mutable = s.tracks.toMutableList()
            mutable.removeAt(index)
            val newIdx = s.currentIndex.coerceAtMost((mutable.size - 1).coerceAtLeast(0))
            s.copy(tracks = mutable, currentIndex = newIdx)
        }
    }

    fun nextTrack(autoAdvance: Boolean): QueueTrack? {
        val s = _state.value
        if (s.tracks.isEmpty()) return null
        if (autoAdvance && s.repeatMode == RepeatMode.ONE) {
            return s.currentTrack
        }
        val nextIdx = if (s.shuffleEnabled && s.tracks.size > 1) {
            val candidates = s.tracks.indices.filter { it != s.currentIndex }
            candidates.random()
        } else {
            val candidate = s.currentIndex + 1
            if (candidate < s.tracks.size) candidate
            else if (s.repeatMode == RepeatMode.ALL) 0
            else return null
        }
        _state.update { it.copy(currentIndex = nextIdx) }
        return s.tracks[nextIdx]
    }

    fun previousTrack(): QueueTrack? {
        val s = _state.value
        if (s.tracks.isEmpty()) return null
        val prevIdx = if (s.currentIndex > 0) s.currentIndex - 1
        else if (s.repeatMode == RepeatMode.ALL) s.tracks.lastIndex
        else 0
        _state.update { it.copy(currentIndex = prevIdx) }
        return s.tracks[prevIdx]
    }
}
