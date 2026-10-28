package com.wilauncher.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tungsten.fcl.R
import com.tungsten.fcl.databinding.ItemWlCatalogBinding
import com.tungsten.fcl.databinding.PageWlCatalogBinding
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.util.Logging.LOG
import com.tungsten.fcllibrary.component.theme.ThemeEngine
import com.tungsten.fcllibrary.component.ui.FCLPage
import com.tungsten.fcllibrary.component.view.FCLTextView
import java.util.logging.Level

/**
 * "Catalog" tab of the download screen: the curated WiLauncher list with one-tap install into the
 * selected game. The full Modrinth search stays one button away ("All mods").
 */
class CatalogPage(context: Context, id: Int, private val openAllMods: Runnable) :
    FCLPage(context, id, R.layout.page_wl_catalog) {

    private lateinit var binding: PageWlCatalogBinding
    private lateinit var data: Catalog.Data
    private lateinit var adapter: Adapter
    private var categoryRows: List<Pair<String?, FCLTextView>> = emptyList()

    private var category: String? = null
    private var query = ""
    private var target: Catalog.Target? = null
    private var installed: Set<String> = emptySet()
    private val installing = HashSet<String>()
    private val unavailable = HashSet<String>()
    private var refreshed = false

    // FCLPage calls onCreate() from its constructor, before Kotlin initialises the fields above,
    // so the page is set up here instead.
    init {
        setup()
    }

    private fun setup() {
        binding = PageWlCatalogBinding.bind(contentView)
        data = Catalog.load(context)
        adapter = Adapter()
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        binding.btnAllMods.setOnClickListener { openAllMods.run() }
        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = Catalog.lowercaseQuery(s?.toString() ?: "")
                applyFilter()
            }
        })
        binding.builtinNote.text = context.getString(R.string.wl_catalog_builtin_note, BuiltinMods.TITLES.joinToString(", "))
        buildCategoryRows()
        ThemeEngine.getInstance().registerEvent(binding.filterPanel) {
            binding.filterPanel.backgroundTintList = ColorStateList.valueOf(ThemeEngine.getInstance().getTheme().ltColor)
            styleCategoryRows()
        }
        applyFilter()
    }

    /** Called whenever the tab becomes visible: re-reads the selected game and what it has. */
    fun onShow() {
        val profile = Profiles.getSelectedProfile()
        val newTarget = runCatching { Catalog.target(profile.repository, Profiles.getSelectedVersion()) }.getOrNull()
        if (newTarget?.versionId != target?.versionId || newTarget?.gameVersion != target?.gameVersion) {
            unavailable.clear()
            installed = emptySet()
        }
        target = newTarget
        binding.target.text = when {
            newTarget == null -> context.getString(R.string.wl_catalog_no_game)
            newTarget.loaders.isEmpty() -> context.getString(R.string.wl_catalog_target_vanilla, newTarget.versionId)
            else -> context.getString(R.string.wl_catalog_target, newTarget.versionId, newTarget.gameVersion, loaderName(newTarget.loaders.first()))
        }
        applyFilter()
        if (newTarget != null) loadInstalled(newTarget)
        if (!refreshed) {
            refreshed = true
            val current = data
            Schedulers.io().execute {
                val fresh = Catalog.refresh(current) ?: return@execute
                Schedulers.androidUIThread().execute {
                    data = fresh
                    buildCategoryRows()
                    applyFilter()
                }
            }
        }
    }

    private fun loadInstalled(target: Catalog.Target) {
        Schedulers.io().execute {
            val projects = try {
                Catalog.installedProjects(target)
            } catch (e: Exception) {
                LOG.log(Level.INFO, "Can't identify installed files: $e")
                return@execute
            }
            Schedulers.androidUIThread().execute {
                if (this.target?.versionId == target.versionId) {
                    installed = projects
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun loaderName(loader: String) = when (loader) {
        "fabric" -> "Fabric"
        "quilt" -> "Quilt"
        "forge" -> "Forge"
        "neoforge" -> "NeoForge"
        else -> loader
    }

    private fun buildCategoryRows() {
        binding.categoryOptions.removeAllViews()
        val density = context.resources.displayMetrics.density
        val options = listOf<Pair<String?, String>>(null to context.getString(R.string.wl_catalog_all)) +
                data.categories.map { it.id to it.title(context) }
        categoryRows = options.map { (id, title) ->
            val row = FCLTextView(context).apply {
                text = title
                textSize = 14f
                isAutoTint = true
                setPadding((density * 10).toInt(), (density * 8).toInt(), (density * 10).toInt(), (density * 8).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (density * 4).toInt() }
                setOnClickListener {
                    category = id
                    styleCategoryRows()
                    applyFilter()
                }
            }
            binding.categoryOptions.addView(row)
            id to row
        }
        styleCategoryRows()
    }

    private fun styleCategoryRows() {
        val theme = ThemeEngine.getInstance().getTheme()
        val density = context.resources.displayMetrics.density
        categoryRows.forEach { (id, row) ->
            val selected = id == category
            val pill = GradientDrawable().apply {
                cornerRadius = density * 10
                setColor(if (selected) theme.getColor() else Color.TRANSPARENT)
            }
            row.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.autoTint, 40)), pill, null)
            row.setTextColor(theme.autoTint)
            row.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun applyFilter() {
        val items = data.items.filter { item ->
            (category == null || item.category == category) &&
                    (query.isEmpty() || item.name.lowercase().contains(query) || item.summary(context).lowercase().contains(query))
        }
        adapter.items = items
        adapter.notifyDataSetChanged()
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.text = context.getString(R.string.wl_catalog_empty)
    }

    private fun install(item: Catalog.Item) {
        val target = target ?: return
        installing += item.id
        adapter.notifyDataSetChanged()
        val have = installed
        Schedulers.io().execute {
            val result = runCatching { Catalog.install(item, target, have) }
            Schedulers.androidUIThread().execute {
                installing -= item.id
                result.onSuccess {
                    installed = installed + item.id + item.requires
                    Toast.makeText(context, context.getString(R.string.wl_catalog_installed_toast, item.name), Toast.LENGTH_SHORT).show()
                    loadInstalled(target)
                }.onFailure { e ->
                    LOG.log(Level.WARNING, "Catalog install of ${item.slug} failed", e)
                    val message = if (e is Catalog.NotAvailableException) {
                        unavailable += item.id
                        context.getString(R.string.wl_catalog_not_for_version, item.name, target.gameVersion)
                    } else {
                        context.getString(R.string.wl_catalog_install_failed, item.name)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        var items: List<Catalog.Item> = emptyList()

        inner class Holder(val binding: ItemWlCatalogBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemWlCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val b = holder.binding
            b.title.text = item.name
            b.summary.text = item.summary(context)
            b.badge.visibility = if (item.heavy) View.VISIBLE else View.GONE
            b.badge.text = context.getString(R.string.wl_catalog_heavy)
            if (item.icon.isNotEmpty()) {
                Glide.with(context).load(item.icon).placeholder(R.drawable.ic_cube).into(b.icon)
            } else {
                b.icon.setImageResource(R.drawable.ic_cube)
            }
            val target = target
            val (label, enabled) = when {
                target == null -> context.getString(R.string.wl_catalog_install) to false
                item.id in installing -> context.getString(R.string.wl_catalog_installing) to false
                item.id in installed -> context.getString(R.string.wl_catalog_installed) to false
                item.id in unavailable -> context.getString(R.string.wl_catalog_unavailable) to false
                else -> when (Catalog.availability(item, target)) {
                    Catalog.Availability.OK -> context.getString(R.string.wl_catalog_install) to true
                    Catalog.Availability.NEEDS_LOADER -> context.getString(R.string.wl_catalog_needs_loader) to false
                    Catalog.Availability.WRONG_LOADER -> context.getString(R.string.wl_catalog_wrong_loader) to false
                }
            }
            b.install.text = label
            b.install.isEnabled = enabled
            b.install.alpha = if (enabled) 1f else 0.6f
            b.install.setOnClickListener { install(item) }
        }
    }
}
