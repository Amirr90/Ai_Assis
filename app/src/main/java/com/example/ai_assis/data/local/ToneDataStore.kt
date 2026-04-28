package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.ToneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toneDataStore by preferencesDataStore(name = "tone_settings")

@Singleton
class ToneDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToneRepository {
    private companion object {
        val toneKey: Preferences.Key<String> = stringPreferencesKey("reply_tone")
    }

    override val toneFlow: Flow<ReplyTone> = context.toneDataStore.data.map { prefs ->
        prefs[toneKey]?.let { saved ->
            ReplyTone.entries.firstOrNull { it.name == saved }
        } ?: ReplyTone.CASUAL
    }

    override suspend fun saveTone(tone: ReplyTone) {
        context.toneDataStore.edit { prefs ->
            prefs[toneKey] = tone.name
        }
    }
}
