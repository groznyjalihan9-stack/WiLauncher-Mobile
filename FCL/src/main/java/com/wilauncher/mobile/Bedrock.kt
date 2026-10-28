package com.wilauncher.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import com.tungsten.fcl.R

/**
 * Minecraft: Bedrock Edition is a separate game sold on Google Play. WiLauncher doesn't ship or
 * patch it: it only starts the official app when it is installed, or opens its store page.
 */
object Bedrock {

    const val PACKAGE = "com.mojang.minecraftpe"
    private const val PREF_EDITION = "wl_edition"

    @JvmStatic
    fun isSelected(context: Context): Boolean =
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString(PREF_EDITION, "java") == "bedrock"

    @JvmStatic
    fun select(context: Context, bedrock: Boolean) {
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit {
            putString(PREF_EDITION, if (bedrock) "bedrock" else "java")
        }
    }

    /** Installed version name, or null when the official app is not installed. */
    @JvmStatic
    fun installedVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @JvmStatic
    fun icon(context: Context): Drawable? = try {
        context.packageManager.getApplicationIcon(PACKAGE)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Starts Bedrock if it is installed, otherwise opens its Google Play page. */
    @JvmStatic
    fun launch(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (intent != null) {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            openStore(activity)
        }
    }

    @JvmStatic
    fun openStore(activity: Activity) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
        try {
            activity.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(web)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.wl_bedrock_no_store, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
