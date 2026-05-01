package com.example.ai_assis.domain.model

data class CustomTemplate(
    val id: String,
    val text: String,
    val appPackage: String? = null,
    val lastUsedAtMs: Long = 0L,
)
