package com.mineserve.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.EmptyHint
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileManagerScreen(vm: McViewModel, onOpenMtGuide: () -> Unit = {}) {
    val glass = HorizonGlass
    val context = LocalContext.current
    val files by vm.fileList.collectAsState()
    val currentPath by vm.currentPath.collectAsState()
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val editorFile by vm.textEditorFile.collectAsState()
    if (editorFile != null) {
        TextFileEditorScreen(vm, editorFile!!, onBack = vm::closeTextFile)
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }
    var showNewDirDialog by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }

    // SAF file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val targetDir = File(currentPath)
            vm.uploadFile(uri, targetDir)
        }
    }

    // Export target
    var exportTarget by remember { mutableStateOf<File?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { target -> exportTarget?.let { vm.exportPathToUri(it, target) } }
        exportTarget = null
    }

    // Whole server export picker
    val exportServerFileName = stringResource(R.string.s490)
    val exportServerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> uri?.let { vm.exportServerToUri(it) } }

    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) {
        vm.messageFlow.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(isBootstrapped) {
        if (isBootstrapped && currentPath.isEmpty()) {
            vm.loadFilesRoot()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(paddingValues)
        ) {
            HeaderBlock(eyebrow = stringResource(R.string.eyebrow_files), title = stringResource(R.string.s488))

            var topActionsExpanded by rememberSaveable { mutableStateOf(true) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = McSpacing.Page),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!topActionsExpanded) {
                    Text(
                        stringResource(R.string.files_actions_collapsed_hint),
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                AnimatedVisibility(
                    visible = topActionsExpanded,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onOpenMtGuide,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = glass.accent,
                                contentColor = Color.White
                            ),
                            shape = McShape.ControlCompact,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.s489), fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { exportServerLauncher.launch(exportServerFileName) },
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = glass.accent, modifier = Modifier.size(McIcon.Inline))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.s491), fontSize = 12.sp, color = glass.accent, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                IconButton(
                    onClick = { topActionsExpanded = !topActionsExpanded },
                    modifier = Modifier.size(McIcon.TouchCompact)
                ) {
                    Icon(
                        if (topActionsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(McIcon.Item)
                    )
                }
            }

            // Thanh điều hướng Breadcrumb Pills mặt kính mờ
            McCard(title = stringResource(R.string.s493)) {
                val rootFile = vm.fileManagerRoot
                val rootPath = rootFile.absolutePath
                val relPath = if (currentPath.startsWith(rootPath)) {
                    currentPath.removePrefix(rootPath).removePrefix(File.separator)
                } else {
                    ""
                }
                val segments = remember(relPath) {
                    relPath.split('/', '\\').filter { it.isNotBlank() }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val atRoot = segments.isEmpty()
                        BreadcrumbGlassPill(
                            label = "🏠 servers",
                            active = atRoot,
                            onClick = {
                                if (!atRoot) vm.loadFiles(rootFile)
                            }
                        )
                        segments.forEachIndexed { idx, seg ->
                            Text("›", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val isLast = idx == segments.lastIndex
                            BreadcrumbGlassPill(
                                label = seg,
                                active = isLast,
                                onClick = {
                                    if (!isLast) {
                                        val target = File(rootFile, segments.take(idx + 1).joinToString(File.separator))
                                        vm.loadFiles(target)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { vm.navigateUp() }, modifier = Modifier.size(McIcon.TouchCompact)) {
                        Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.s494), tint = glass.accent, modifier = Modifier.size(McIcon.Action))
                    }
                    IconButton(onClick = { vm.refreshFiles() }, modifier = Modifier.size(McIcon.TouchCompact)) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.s333), tint = glass.accent, modifier = Modifier.size(McIcon.Action))
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentDir = File(currentPath)
                    val parentDir = currentDir.parentFile
                    val canGoUp = parentDir != null &&
                        currentDir.absolutePath != vm.fileManagerRoot.absolutePath &&
                        currentDir.absolutePath.contains(vm.fileManagerRoot.absolutePath)

                    Button(
                        onClick = {
                            parentDir?.let { if (canGoUp) vm.loadFiles(it) }
                        },
                        enabled = canGoUp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = glass.accent,
                            contentColor = Color.White
                        ),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.s495),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.s496), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = glass.liveGreen,
                            contentColor = Color.White
                        ),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.Upload,
                            contentDescription = stringResource(R.string.s497),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.s497), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            newDirName = ""
                            showNewDirDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = glass.accent,
                            contentColor = Color.White
                        ),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = stringResource(R.string.s498),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.s499), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (files.isEmpty()) {
                McCard(title = stringResource(R.string.s500)) {
                    EmptyHint(
                        icon = Icons.Outlined.FolderOpen,
                        text = if (currentPath.isEmpty()) stringResource(R.string.s501) else stringResource(R.string.s502)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(files, key = { it.path }, contentType = { "file-entry" }) { entry ->
                        FileItemRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    vm.loadFiles(File(entry.path))
                                }
                            },
                            onExport = {
                                exportTarget = File(entry.path)
                                exportLauncher.launch(if (entry.isDirectory) "${entry.name}.zip" else entry.name)
                            },
                            onEdit = { vm.openTextFile(File(entry.path)) },
                            canEdit = entry.isEditable,
                            onDelete = {
                                showDeleteConfirm = File(entry.path)
                            }
                        )
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.s401), fontWeight = FontWeight.Bold) },
            text = {
                val deleteFolderPrefix = stringResource(R.string.s503)
                val deleteFilePrefix = stringResource(R.string.s1030)
                val folderContentMsg = stringResource(R.string.s504)
                val irreversibleMsg = stringResource(R.string.s505)
                Text(
                    buildString {
                        append(if (fileToDelete.isDirectory) deleteFolderPrefix else deleteFilePrefix)
                        append(fileToDelete.name)
                        if (fileToDelete.isDirectory) {
                            append(folderContentMsg)
                        }
                        append(irreversibleMsg)
                    },
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteFile(fileToDelete)
                        showDeleteConfirm = null
                    }
                ) {
                    Text(stringResource(R.string.s339), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = null }
                ) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }

    if (showNewDirDialog) {
        AlertDialog(
            onDismissRequest = { showNewDirDialog = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.s498), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newDirName,
                    onValueChange = { newDirName = it },
                    label = { Text(stringResource(R.string.s506), fontSize = 12.sp) },
                    singleLine = true,
                    shape = McShape.Control,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newDirName.trim()
                        val invalid = name.isBlank() || name != newDirName ||
                            name.contains('/') || name.contains('\\') || name.contains("..")
                        if (invalid) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.fm_new_dir_invalid_name))
                            }
                        } else {
                            vm.createDirectory(File(currentPath), name)
                            showNewDirDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.s507), color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewDirDialog = false }
                ) {
                    Text(stringResource(R.string.s402), color = Muted)
                }
            }
        )
    }
}

@Composable
private fun BreadcrumbGlassPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) glass.accentSoft else glass.glassInput)
            .border(
                1.dp,
                if (active) glass.accent.copy(alpha = 0.65f) else glass.hairline,
                CircleShape
            )
            .clickable(enabled = !active) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) glass.accent else Muted,
            maxLines = 1
        )
    }
}

@Composable
private fun FileItemRow(
    entry: McViewModel.FileEntry,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    canEdit: Boolean,
    onDelete: () -> Unit
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = McSpacing.Page)
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (entry.isDirectory) glass.liveGreenSoft else glass.accentSoft)
                .border(
                    1.dp,
                    if (entry.isDirectory) glass.liveGreen.copy(alpha = 0.4f) else glass.accent.copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) glass.liveGreen else glass.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                fileAnnotation(entry)?.let { resId ->
                    val ann = stringResource(resId)
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.glassInput)
                            .border(1.dp, glass.hairline, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            ann,
                            color = Muted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (entry.isDirectory) entry.modifiedText else "${entry.sizeText} · ${entry.modifiedText}",
                style = HorizonTabularStyle,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        IconButton(onClick = onExport, modifier = Modifier.padding(start = 2.dp).size(McIcon.Touch)) {
            Icon(
                Icons.Outlined.FileDownload,
                contentDescription = stringResource(R.string.s335),
                tint = glass.accent,
                modifier = Modifier.size(McIcon.Action)
            )
        }

        if (canEdit) {
            IconButton(onClick = onEdit, modifier = Modifier.size(McIcon.Touch)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.fm_edit_text),
                    tint = glass.accent,
                    modifier = Modifier.size(McIcon.Action)
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(McIcon.Touch)
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.s339),
                tint = glass.dangerCoral,
                modifier = Modifier.size(McIcon.Action)
            )
        }
    }
}

private val fileAnnotations = mapOf(
    "server.jar" to R.string.s508,
    "server.properties" to R.string.s509,
    "ops.json" to R.string.s510,
    "whitelist.json" to R.string.s511,
    "banned-players.json" to R.string.s512,
    "banned-ips.json" to R.string.s513,
    "eula.txt" to R.string.s514,
    "usercache.json" to R.string.s515,
    "usernamecache.json" to R.string.s516,
    "permissions.json" to R.string.s517,
    "help.yml" to R.string.s518,
    "bukkit.yml" to R.string.s519,
    "spigot.yml" to R.string.s520,
    "paper.yml" to R.string.s521,
    "commands.yml" to R.string.s522,
    "seedcache" to R.string.s523,
    "version_history.json" to R.string.s524,
    "latest.log" to R.string.s525,
    "debug.log" to R.string.s526
)

private val dirAnnotations = mapOf(
    "world" to R.string.s527,
    "world_nether" to R.string.s528,
    "world_the_end" to R.string.s529,
    "logs" to R.string.s530,
    "log" to R.string.s531,
    "plugins" to R.string.s532,
    "config" to R.string.s533,
    "versions" to R.string.s534,
    "libraries" to R.string.s535,
    "crash-reports" to R.string.s536,
    "cache" to R.string.s537,
    "generated" to R.string.s538,
    "datapacks" to R.string.s539,
    "world_backup" to R.string.s540
)

private fun fileAnnotation(entry: McViewModel.FileEntry): Int? {
    val key = entry.name.lowercase()
    return if (entry.isDirectory) dirAnnotations[key] else fileAnnotations[key]
}
