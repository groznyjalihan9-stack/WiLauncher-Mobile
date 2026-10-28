package com.wilauncher.mobile

import android.content.Context
import android.widget.Toast
import com.tungsten.fcl.R
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.util.Logging.LOG
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level

/** "Open with WiLauncher" for a mod .jar: copies it into the mods folder of the selected game. */
object ModFileImport {

    @JvmStatic
    fun install(context: Context, file: File) {
        val toast = { text: String -> Schedulers.androidUIThread().execute { Toast.makeText(context, text, Toast.LENGTH_LONG).show() } }
        Schedulers.io().execute {
            try {
                val target = Catalog.target(Profiles.getSelectedProfile().repository, Profiles.getSelectedVersion())
                when {
                    target == null -> toast(context.getString(R.string.wl_catalog_no_game))
                    target.loaders.isEmpty() -> toast(context.getString(R.string.wl_import_mod_no_loader, target.versionId))
                    else -> {
                        // Only the bare file name is used, so a crafted name can't leave the mods folder.
                        val name = File(file.name).name
                        Files.createDirectories(target.modsDir)
                        Files.copy(file.toPath(), target.modsDir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
                        toast(context.getString(R.string.wl_import_mod_done, name, target.versionId))
                    }
                }
            } catch (e: Exception) {
                LOG.log(Level.WARNING, "Failed to import mod ${file.name}", e)
                toast(context.getString(R.string.wl_import_mod_failed, file.name))
            } finally {
                file.delete()
            }
        }
    }
}
