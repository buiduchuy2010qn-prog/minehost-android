package com.mineserve.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.mineserve.mobile.R

/** Bottom navigation tabs with paired Outlined (inactive) / Rounded (active) Material icons. */
enum class McTab(
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Dashboard(R.string.tab_dashboard, Icons.Outlined.Dns, Icons.Rounded.Dns),
    Worlds(R.string.more_backup, Icons.Outlined.Public, Icons.Rounded.Public),
    Download(R.string.tab_download, Icons.Outlined.Extension, Icons.Rounded.Extension),
    ServerManagement(R.string.tab_server_management, Icons.Outlined.Dns, Icons.Rounded.Dns),
    Players(R.string.tab_players, Icons.Outlined.People, Icons.Rounded.People),
    Files(R.string.tab_files, Icons.Outlined.Folder, Icons.Rounded.Folder),
    Terminal(R.string.tab_terminal, Icons.Outlined.Terminal, Icons.Rounded.Terminal),
    Settings(R.string.tab_settings, Icons.Outlined.Build, Icons.Rounded.Build)
}
