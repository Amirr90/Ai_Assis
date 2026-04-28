package com.example.ai_assis.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReplyRequestDto(
    val model: String,
    val messages: List<MessageDto>,
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String,
)
