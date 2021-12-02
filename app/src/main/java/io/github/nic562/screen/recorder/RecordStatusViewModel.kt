package io.github.nic562.screen.recorder

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RecordStatusViewModel : ViewModel() {
    val recordingEvent = MutableLiveData<Boolean>()
}