package com.regman.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val KEY_CAPTCHA = stringPreferencesKey("captcha_key")
    private val KEY_REMOTE = stringPreferencesKey("remote_config_url")

    val captchaKey: Flow<String> = ctx.settingsStore.data.map { it[KEY_CAPTCHA] ?: "" }
    val remoteConfigUrl: Flow<String> = ctx.settingsStore.data.map { it[KEY_REMOTE] ?: "" }

    suspend fun setCaptchaKey(v: String) { ctx.settingsStore.edit { it[KEY_CAPTCHA] = v } }
    suspend fun setRemoteConfigUrl(v: String) { ctx.settingsStore.edit { it[KEY_REMOTE] = v } }
}
