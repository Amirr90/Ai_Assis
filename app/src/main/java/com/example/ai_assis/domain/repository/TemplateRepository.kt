package com.example.ai_assis.domain.repository

import com.example.ai_assis.domain.model.CustomTemplate
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    val templatesFlow: Flow<List<CustomTemplate>>
    suspend fun saveTemplate(text: String, appPackage: String? = null)
    suspend fun deleteTemplate(templateId: String)
    suspend fun markTemplateUsed(templateId: String)
}
