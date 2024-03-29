package com.dev.mywebrtc.util

import com.dev.mywebrtc.models.MessageModel

interface NewMessageInterface {
    fun onNewMessage(message:MessageModel)
}