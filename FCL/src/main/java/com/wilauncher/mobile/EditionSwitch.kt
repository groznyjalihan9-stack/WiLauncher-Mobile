package com.wilauncher.mobile

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.tungsten.fcl.R
import com.tungsten.fcl.databinding.ActivityMainBinding
import com.tungsten.fcllibrary.component.theme.ThemeEngine
import com.tungsten.fcllibrary.component.view.FCLTextView

/**
 * Java / Bedrock switch on the home screen. In Bedrock mode the version card shows the official
 * Bedrock app and the launch button starts it (or opens Google Play when it isn't installed).
 */
class EditionSwitch(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val showJava: Runnable,
) {

    val isBedrock: Boolean get() = Bedrock.isSelected(activity)

    fun setup() {
        binding.editionJava.setOnClickListener { choose(false) }
        binding.editionBedrock.setOnClickListener { choose(true) }
        ThemeEngine.getInstance().registerEvent(binding.editionSwitch) { style() }
        style()
    }

    private fun choose(bedrock: Boolean) {
        if (bedrock == isBedrock) return
        Bedrock.select(activity, bedrock)
        style()
        refresh()
    }

    /** Fills the version card and launch button for the selected edition. */
    fun refresh() {
        if (!isBedrock) {
            binding.textStart.setText(R.string.launch)
            binding.goSetting.isVisible = true
            showJava.run()
            return
        }
        val version = Bedrock.installedVersion(activity)
        binding.versionProgress.visibility = View.GONE
        binding.versionName.text = activity.getString(R.string.wl_bedrock_title)
        binding.versionName.isSelected = false
        binding.versionHint.text = if (version != null) {
            activity.getString(R.string.wl_bedrock_installed, version)
        } else {
            activity.getString(R.string.wl_bedrock_not_installed)
        }
        binding.versionHint.isVisible = true
        binding.icon.background = Bedrock.icon(activity) ?: AppCompatResources.getDrawable(activity, R.drawable.img_grass)
        binding.textStart.setText(if (version != null) R.string.launch else R.string.wl_bedrock_get)
        binding.goSetting.isVisible = false
    }

    private fun style() {
        val theme = ThemeEngine.getInstance().getTheme()
        val density = activity.resources.displayMetrics.density
        fun paint(view: FCLTextView, selected: Boolean) {
            view.background = GradientDrawable().apply {
                cornerRadius = density * 10
                setColor(if (selected) theme.getColor() else theme.ltColor)
                setStroke((density * 1.5f).toInt(), theme.getColor())
            }
            view.setTextColor(if (selected) theme.autoTint else theme.getColor())
            view.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        paint(binding.editionJava, !isBedrock)
        paint(binding.editionBedrock, isBedrock)
    }
}
