package app.appsperms.ui

import app.appsperms.model.AppEntry

/** Isi RecyclerView: pemisah section (app terinstall / app sistem) + baris app. */
sealed interface ListItem {
    val key: String

    data class Header(val title: String, val count: Int) : ListItem {
        override val key: String get() = "header:$title"
    }

    data class App(val entry: AppEntry) : ListItem {
        override val key: String get() = "app:${entry.packageName}"
    }
}
