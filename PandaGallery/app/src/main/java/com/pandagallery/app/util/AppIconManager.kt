package com.pandagallery.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {
    private const val BASE_ALIAS_NAME = "com.pandagallery.app.MainActivityAliasIcon"

    val iconAliases = (1..16).map { "icon_$it" }

    fun changeAppIcon(context: Context, targetIconName: String) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        val targetIndex = targetIconName.removePrefix("icon_").toIntOrNull() ?: 2

        for (i in 1..16) {
            val aliasClassName = "$BASE_ALIAS_NAME$i"
            val compName = ComponentName(packageName, aliasClassName)
            val isTarget = (i == targetIndex)

            val targetState = if (isTarget) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            val currentState = packageManager.getComponentEnabledSetting(compName)
            if (currentState != targetState) {
                packageManager.setComponentEnabledSetting(
                    compName,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
