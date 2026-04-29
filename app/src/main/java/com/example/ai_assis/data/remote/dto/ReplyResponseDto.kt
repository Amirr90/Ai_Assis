package com.example.ai_assis.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ReplyResponseDto(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
data class ChoiceDto(
    val message: MessageDto = MessageDto(role = "", content = ""),
    @SerialName("finish_reason")
    val finishReason: String? = null,
)
