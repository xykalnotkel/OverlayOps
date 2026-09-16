package app.appsperms.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.appsperms.databinding.ItemAppBinding
import app.appsperms.databinding.ItemHeaderBinding
import app.appsperms.model.AppEntry

class AppListAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onChangeOverlay: (AppEntry) -> Unit,
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemHeaderBinding.inflate(inflater, parent, false))
        } else {
            AppVH(ItemAppBinding.inflate(inflater, parent, false), onOpen, onChangeOverlay)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderVH).bind(item)
            is ListItem.App -> (holder as AppVH).bind(item.entry)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_STATUS) && holder is AppVH) {
            val item = getItem(position)
            if (item is ListItem.App) {
                holder.updateStatusOnly(item.entry)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    class HeaderVH(private val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ListItem.Header) = with(b) {
            headerTitle.text = item.title
            headerCount.text = item.count.toString()
        }
    }

    class AppVH(
        private val b: ItemAppBinding,
        private val onOpen: (AppEntry) -> Unit,
        private val onChangeOverlay: (AppEntry) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AppEntry) = with(b) {
            label.text = item.label
            pkg.text = item.packageName
            icon.setImageDrawable(item.icon)
            badgeUid.text = "uid ${item.uid}"
            badgeSystem.isVisible = item.isSystem && false
            badgeDeclares.isVisible = item.declaresOverlay
            statusChip.bindStatusChip(item.overlayStatus)

            statusChip.setOnClickListener { onChangeOverlay(item) }
            rowRoot.setOnClickListener { onOpen(item) }
            rowRoot.setOnLongClickListener {
                onChangeOverlay(item)
                true
            }
        }

        fun updateStatusOnly(item: AppEntry) = with(b) {
            statusChip.bindStatusChip(item.overlayStatus)
            statusChip.setOnClickListener { onChangeOverlay(item) }
            rowRoot.setOnLongClickListener {
                onChangeOverlay(item)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
        const val PAYLOAD_STATUS = "payload_status"

        private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(old: ListItem, new: ListItem) = old.key == new.key
            override fun areContentsTheSame(old: ListItem, new: ListItem): Boolean {
                if (old is ListItem.App && new is ListItem.App) {
                    return old.entry.overlayStatus == new.entry.overlayStatus &&
                        old.entry.label == new.entry.label &&
                        old.entry.declaresOverlay == new.entry.declaresOverlay &&
                        old.entry.isSystem == new.entry.isSystem
                }
                return old == new
            }

            override fun getChangePayload(old: ListItem, new: ListItem): Any? {
                if (old is ListItem.App && new is ListItem.App) {
                    if (old.entry.packageName == new.entry.packageName &&
                        old.entry.overlayStatus != new.entry.overlayStatus
                    ) {
                        return PAYLOAD_STATUS
                    }
                }
                return null
            }
        }
    }
}
