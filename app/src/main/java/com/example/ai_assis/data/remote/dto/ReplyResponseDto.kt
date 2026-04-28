package com.example.ai_assis.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReplyResponseDto(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
data class ChoiceDto(
    val message: MessageDto = MessageDto(role = "", content = ""),
)
