package com.guardpulse.parentcontrol.shared

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

data class FirebaseStatus(
    val configured: Boolean,
    val message: String
)

object FirebaseBootstrap {
    fun initialize(context: Context): FirebaseStatus {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return FirebaseStatus(configured = true, message = "Firebase initialized")
        }

        val appId = readString(context, "firebase_app_id")
        val apiKey = readString(context, "firebase_api_key")
        val projectId = readString(context, "firebase_project_id")
        val databaseUrl = readString(context, "firebase_database_url")

        val missing = listOf(
            "firebase_app_id" to appId,
            "firebase_api_key" to apiKey,
            "firebase_project_id" to projectId,
            "firebase_database_url" to databaseUrl
        ).filter { (_, value) -> value.isBlank() || value.startsWith("replace_") }

        if (missing.isNotEmpty()) {
            return FirebaseStatus(
                configured = false,
                message = "Replace Firebase values in res/values/firebase_config.xml"
            )
        }

        val options = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .setDatabaseUrl(databaseUrl)
            .build()
        FirebaseApp.initializeApp(context, options)
        return FirebaseStatus(configured = true, message = "Firebase initialized")
    }

    private fun readString(context: Context, name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) "" else context.getString(id).trim()
    }
}
