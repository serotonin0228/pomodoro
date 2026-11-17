package com.pomodorotimer.app.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable view of the foreground media service, used by the music panel. */
object MusicPlayback {
    var playing by mutableStateOf(false)
        internal set
    var title by mutableStateOf("")
        internal set
    var path by mutableStateOf("")
        internal set
}
