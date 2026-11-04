package com.malaxg.miloagent

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class MiloModelProfile(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

internal class MiloModelProfileStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("milo_settings", Context.MODE_PRIVATE)

    fun profiles(): List<MiloModelProfile> {
        val savedProfiles = preferences.getString(PROFILES_KEY, null)
        if (savedProfiles != null) {
            return runCatching {
                val array = JSONArray(savedProfiles)
                List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    MiloModelProfile(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        provider = item.optString("provider"),
                        baseUrl = item.optString("baseUrl"),
                        model = item.optString("model"),
                        apiKey = item.optString("apiKey"),
                    )
                }.filter { it.id.isNotBlank() }
            }.getOrDefault(emptyList())
        }

        val legacy = legacyProfile() ?: return emptyList()
        val profile = legacy.copy(id = "legacy")
        writeProfiles(listOf(profile))
        preferences.edit().putString(ACTIVE_PROFILE_KEY, profile.id).apply()
        return listOf(profile)
    }

    fun activeProfile(): MiloModelProfile? {
        val all = profiles()
        val activeId = preferences.getString(ACTIVE_PROFILE_KEY, null)
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    fun save(profile: MiloModelProfile, activate: Boolean = true): MiloModelProfile {
        val all = profiles().toMutableList()
        val index = all.indexOfFirst { it.id == profile.id }
        if (index >= 0) all[index] = profile else all += profile
        writeProfiles(all)
        if (activate) setActiveProfile(profile)
        return profile
    }

    fun activate(id: String): MiloModelProfile? {
        val profile = profiles().firstOrNull { it.id == id } ?: return null
        setActiveProfile(profile)
        return profile
    }

    fun delete(id: String): List<MiloModelProfile> {
        val remaining = profiles().filterNot { it.id == id }
        writeProfiles(remaining)
        if (preferences.getString(ACTIVE_PROFILE_KEY, null) == id) {
            remaining.firstOrNull()?.let(::setActiveProfile) ?: clearActiveProfile()
        }
        return remaining
    }

    private fun legacyProfile(): MiloModelProfile? {
        val baseUrl = preferences.getString("base_url", "").orEmpty()
        val model = preferences.getString("model", "").orEmpty()
        val apiKey = preferences.getString("api_key", "").orEmpty()
        if (baseUrl.isBlank() && model.isBlank() && apiKey.isBlank()) return null
        val provider = preferences.getString("provider", "自定义").orEmpty().ifBlank { "自定义" }
        return MiloModelProfile(
            id = "legacy",
            name = provider,
            provider = provider,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
        )
    }

    private fun writeProfiles(profiles: List<MiloModelProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("provider", profile.provider)
                    .put("baseUrl", profile.baseUrl)
                    .put("model", profile.model)
                    .put("apiKey", profile.apiKey),
            )
        }
        preferences.edit().putString(PROFILES_KEY, array.toString()).apply()
    }

    private fun setActiveProfile(profile: MiloModelProfile) {
        preferences.edit()
            .putString(ACTIVE_PROFILE_KEY, profile.id)
            .putString("provider", profile.provider)
            .putString("base_url", profile.baseUrl)
            .putString("model", profile.model)
            .putString("api_key", profile.apiKey)
            .apply()
    }

    private fun clearActiveProfile() {
        preferences.edit()
            .remove(ACTIVE_PROFILE_KEY)
            .remove("provider")
            .remove("base_url")
            .remove("model")
            .remove("api_key")
            .apply()
    }

    private companion object {
        const val PROFILES_KEY = "model_profiles"
        const val ACTIVE_PROFILE_KEY = "active_model_profile"
    }
}
