package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.data.FrpProfile
import com.mineserve.mobile.data.FrpProfileKind
import com.mineserve.mobile.data.FrpProfiles
import com.mineserve.mobile.server.tunnel.MeFrpApi
import com.mineserve.mobile.server.tunnel.SakuraFrpApi
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.DebouncedTextField
import com.mineserve.mobile.ui.HintNote
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.WarnNote
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrpProfileEditorScreen(
    vm: McViewModel,
    profileId: String?,
    onBack: () -> Unit
) {
    val glass = HorizonGlass
    val config by vm.config.collectAsState()
    val all = config.frpProfiles
    val original = all.firstOrNull { it.id == profileId }
    val isNew = original == null

    val draftId = remember(profileId) { original?.id ?: UUID.randomUUID().toString() }

    var name by remember(profileId) {
        mutableStateOf(original?.name ?: FrpProfiles.defaultName(FrpProfileKind.CustomToml, all))
    }
    var kind by remember(profileId) { mutableStateOf(original?.kind ?: FrpProfileKind.CustomToml) }
    var configText by remember(profileId) { mutableStateOf(original?.configText.orEmpty()) }
    var token by remember(profileId) { mutableStateOf(original?.token.orEmpty()) }
    var tunnelId by remember(profileId) { mutableStateOf(original?.tunnelId.orEmpty()) }
    var tunnelLabel by remember(profileId) { mutableStateOf(original?.tunnelLabel.orEmpty()) }
    var error by remember(profileId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(profileId) { mutableStateOf(false) }

    fun changeKind(next: FrpProfileKind) {
        val wasAuto = name.isBlank() || name == FrpProfiles.defaultName(kind, all)
        kind = next
        if (wasAuto) name = FrpProfiles.defaultName(next, all)
        tunnelId = ""
        tunnelLabel = ""
        error = null
    }

    fun currentDraft(): FrpProfile = FrpProfile(
        id = draftId,
        name = name.trim().ifBlank { FrpProfiles.defaultName(kind, all) },
        kind = kind,
        configText = configText,
        token = token.trim(),
        tunnelId = tunnelId,
        tunnelLabel = tunnelLabel
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            BackBar(
                title = stringResource(
                    if (isNew) R.string.net_frp_edit_title_new else R.string.net_frp_edit_title_edit
                ),
                onBack = onBack
            )

            McCard(title = stringResource(R.string.net_frp_edit_section_basic)) {
                DebouncedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.net_frp_edit_name)) },
                    placeholder = { Text(stringResource(R.string.net_frp_edit_name_hint)) },
                    singleLine = true,
                    shape = McShape.Control,
                    modifier = Modifier.fillMaxWidth()
                )
                if (FrpProfiles.isNameTaken(name, all, draftId)) {
                    Spacer(Modifier.height(6.dp))
                    WarnNote(stringResource(R.string.net_frp_edit_name_dup))
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.net_frp_edit_kind),
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegPill(
                        text = stringResource(R.string.net_frp_edit_kind_toml),
                        selected = kind == FrpProfileKind.CustomToml,
                        compact = true,
                        onClick = { changeKind(FrpProfileKind.CustomToml) }
                    )
                    SegPill(
                        text = stringResource(R.string.net_frp_edit_kind_sakura),
                        selected = kind == FrpProfileKind.SakuraFrp,
                        compact = true,
                        onClick = { changeKind(FrpProfileKind.SakuraFrp) }
                    )
                    SegPill(
                        text = stringResource(R.string.net_frp_edit_kind_me),
                        selected = kind == FrpProfileKind.MeFrp,
                        compact = true,
                        onClick = { changeKind(FrpProfileKind.MeFrp) }
                    )
                }
            }

            when (kind) {
                FrpProfileKind.CustomToml -> McCard(title = stringResource(R.string.s599)) {
                    DebouncedTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        placeholder = { Text(stringResource(R.string.s600)) },
                        textStyle = HorizonTabularStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        minLines = 6,
                        maxLines = 14,
                        shape = McShape.Control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    HintNote(stringResource(R.string.s601))
                    Spacer(Modifier.height(4.dp))
                    HintNote(stringResource(R.string.net_frp_edit_toml_note, serverPortOf(vm)))
                }

                FrpProfileKind.SakuraFrp -> McCard(title = stringResource(R.string.net_frp_edit_kind_sakura)) {
                    TokenField(
                        token = token,
                        onTokenChange = { token = it },
                        hint = stringResource(R.string.net_frp_edit_token_hint_sakura)
                    )
                    Spacer(Modifier.height(12.dp))
                    SakuraTunnelSection(
                        token = token.trim(),
                        selectedTunnelId = tunnelId,
                        defaultPort = serverPortOf(vm),
                        onSelect = { t ->
                            tunnelId = t.id
                            tunnelLabel = FrpProfiles.tunnelLabel(
                                nodeName = t.nodeId,
                                type = t.type,
                                localIp = t.localIp,
                                localPort = t.localPort,
                                remote = t.remoteAddress.ifBlank { t.remotePort?.toString().orEmpty() }
                            )
                            error = null
                        }
                    )
                }

                FrpProfileKind.MeFrp -> McCard(title = stringResource(R.string.net_frp_edit_kind_me)) {
                    TokenField(
                        token = token,
                        onTokenChange = { token = it },
                        hint = stringResource(R.string.net_frp_edit_token_hint_me)
                    )
                    Spacer(Modifier.height(12.dp))
                    MeFrpTunnelSection(
                        token = token.trim(),
                        selectedTunnelId = tunnelId,
                        onSelect = { p ->
                            tunnelId = p.id
                            tunnelLabel = FrpProfiles.tunnelLabel(
                                nodeName = p.nodeName,
                                type = p.type,
                                localIp = p.localIp,
                                localPort = p.localPort,
                                remote = p.remoteAddress.ifBlank { p.remotePort?.toString().orEmpty() }
                            )
                            error = null
                        }
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.padding(horizontal = McSpacing.Page)) { WarnNote(it) }
            }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val draft = currentDraft()
                    val problem = FrpProfiles.validationError(draft)
                    if (problem != null) {
                        error = problem
                    } else {
                        vm.saveFrpProfile(draft)
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = glass.accent,
                    contentColor = Color.White
                ),
                shape = McShape.Control,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = McSpacing.Page)
            ) {
                Text(stringResource(R.string.net_frp_edit_save), color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    shape = McShape.Control,
                    border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.dangerCoralSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = McSpacing.Page)
                ) {
                    Text(stringResource(R.string.net_frp_edit_delete), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.net_frp_delete_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.net_frp_delete_message, name), fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        vm.deleteFrpProfile(draftId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = glass.dangerCoral, contentColor = Color.White),
                    shape = McShape.Control
                ) { Text(stringResource(R.string.net_frp_dialog_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.s402)) }
            }
        )
    }
}

@Composable
private fun serverPortOf(vm: McViewModel): Int = vm.serverPort.collectAsState().value

@Composable
private fun TokenField(token: String, onTokenChange: (String) -> Unit, hint: String) {
    DebouncedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.net_frp_edit_token)) },
        placeholder = { Text(hint) },
        singleLine = true,
        shape = McShape.Control,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SakuraTunnelSection(
    token: String,
    selectedTunnelId: String,
    defaultPort: Int,
    onSelect: (SakuraFrpApi.Tunnel) -> Unit
) {
    val glass = HorizonGlass
    val scope = rememberCoroutineScope()
    var account by remember(token) { mutableStateOf<SakuraFrpApi.Account?>(null) }
    var tunnels by remember(token) { mutableStateOf<List<SakuraFrpApi.Tunnel>>(emptyList()) }
    var nodes by remember(token) { mutableStateOf<List<SakuraFrpApi.Node>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember(token) { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    fun refresh(withNodes: Boolean) {
        if (token.isBlank() || loading) return
        scope.launch {
            loading = true
            message = ""
            try {
                val info = withContext(Dispatchers.IO) { SakuraFrpApi.userInfo(token) }
                val list = withContext(Dispatchers.IO) { SakuraFrpApi.tunnelList(token) }
                account = info
                tunnels = list
                if (withNodes) {
                    nodes = withContext(Dispatchers.IO) { SakuraFrpApi.nodeList(token, info.level) }
                }
            } catch (e: Exception) {
                message = e.message ?: "Tải danh sách thất bại"
            } finally {
                loading = false
            }
        }
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { refresh(withNodes = false) },
                enabled = token.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = glass.accent, contentColor = Color.White),
                shape = McShape.ControlCompact,
                modifier = Modifier.weight(1f)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.size(6.dp))
                }
                Text(stringResource(R.string.net_frp_edit_fetch), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = {
                    if (nodes.isEmpty()) refresh(withNodes = true)
                    showCreate = true
                },
                enabled = token.isNotBlank() && !loading,
                border = BorderStroke(1.dp, glass.hairline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                shape = McShape.ControlCompact
            ) {
                Text(stringResource(R.string.net_frp_edit_create_tunnel), fontSize = 12.sp, color = glass.accent, fontWeight = FontWeight.SemiBold)
            }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = glass.dangerCoral, fontSize = 11.sp)
        }

        account?.let { acc ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.net_frp_edit_account,
                    acc.username,
                    acc.group,
                    "${tunnels.size}" + (acc.maxTunnels?.let { "/$it" } ?: "")
                ),
                style = HorizonTabularStyle,
                color = Muted,
                fontSize = 11.sp
            )
        }

        if (tunnels.isEmpty() && !loading) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.net_frp_edit_empty_sakura), color = Muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tunnels.forEach { t ->
                TunnelRow(
                    selected = t.id == selectedTunnelId,
                    online = t.online,
                    title = t.name + if (t.id == selectedTunnelId) stringResource(R.string.net_frp_edit_selected) else "",
                    subtitle = FrpProfiles.tunnelLabel(
                        nodeName = t.nodeId,
                        type = t.type,
                        localIp = t.localIp,
                        localPort = t.localPort,
                        remote = t.remoteAddress.ifBlank { t.remotePort?.toString().orEmpty() }
                    ),
                    onClick = { onSelect(t) }
                )
            }
        }
    }

    if (showCreate) {
        SakuraCreateTunnelDialog(
            nodes = nodes,
            defaultPort = defaultPort,
            onDismiss = { showCreate = false },
            onConfirm = { node, name, type, port ->
                showCreate = false
                scope.launch {
                    loading = true
                    message = ""
                    try {
                        withContext(Dispatchers.IO) {
                            SakuraFrpApi.createTunnel(token, node.id, name, type, "127.0.0.1", port)
                        }
                        refresh(withNodes = false)
                    } catch (e: Exception) {
                        message = e.message ?: "Tạo đường hầm thất bại"
                    } finally {
                        loading = false
                    }
                }
            }
        )
    }
}

@Composable
private fun MeFrpTunnelSection(
    token: String,
    selectedTunnelId: String,
    onSelect: (MeFrpApi.Proxy) -> Unit
) {
    val glass = HorizonGlass
    val scope = rememberCoroutineScope()
    var account by remember(token) { mutableStateOf<MeFrpApi.Account?>(null) }
    var proxies by remember(token) { mutableStateOf<List<MeFrpApi.Proxy>>(emptyList()) }
    var message by remember(token) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        if (token.isBlank() || loading) return
        scope.launch {
            loading = true
            message = ""
            try {
                val acc = withContext(Dispatchers.IO) { MeFrpApi.userInfo(token) }
                val list = withContext(Dispatchers.IO) { MeFrpApi.proxyList(token) }
                account = acc
                proxies = list
            } catch (e: Exception) {
                message = e.message ?: "Tải danh sách thất bại"
            } finally {
                loading = false
            }
        }
    }

    Column {
        Button(
            onClick = { refresh() },
            enabled = token.isNotBlank() && !loading,
            colors = ButtonDefaults.buttonColors(containerColor = glass.accent, contentColor = Color.White),
            shape = McShape.ControlCompact,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.size(6.dp))
            }
            Text(stringResource(R.string.net_frp_edit_fetch), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = glass.dangerCoral, fontSize = 11.sp)
        }

        account?.let { acc ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.net_frp_edit_account,
                    acc.username,
                    acc.group,
                    "${proxies.size}" + (acc.maxTunnels?.let { "/$it" } ?: "")
                ),
                style = HorizonTabularStyle,
                color = Muted,
                fontSize = 11.sp
            )
        }

        if (proxies.isEmpty() && !loading) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.net_frp_edit_empty_me), color = Muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            proxies.forEach { p ->
                TunnelRow(
                    selected = p.id == selectedTunnelId,
                    online = p.online,
                    title = p.name + if (p.id == selectedTunnelId) stringResource(R.string.net_frp_edit_selected) else "",
                    subtitle = FrpProfiles.tunnelLabel(
                        nodeName = p.nodeName,
                        type = p.type,
                        localIp = p.localIp,
                        localPort = p.localPort,
                        remote = p.remoteAddress.ifBlank { p.remotePort?.toString().orEmpty() }
                    ),
                    onClick = { onSelect(p) }
                )
            }
        }
    }
}

@Composable
private fun TunnelRow(
    selected: Boolean,
    online: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = if (selected) glass.accentSoft else glass.glassRow,
                borderColor = if (selected) glass.accent else glass.hairline,
                highlighted = selected
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (online) glass.liveGreenSoft else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (online) glass.liveGreen else Muted, CircleShape)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) glass.accent else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = HorizonTabularStyle,
                color = Muted,
                fontSize = 10.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SakuraCreateTunnelDialog(
    nodes: List<SakuraFrpApi.Node>,
    defaultPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (node: SakuraFrpApi.Node, name: String, type: String, port: Int) -> Unit
) {
    val glass = HorizonGlass
    var selectedNode by remember { mutableStateOf(nodes.firstOrNull { it.online }) }
    var nodeMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Minexa") }
    var type by remember { mutableStateOf("tcp") }
    var portText by remember { mutableStateOf(defaultPort.toString()) }
    val port = portText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.glassChrome,
        shape = McShape.Dialog,
        title = { Text(stringResource(R.string.net_frp_edit_sakura_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nodes.isEmpty()) {
                    Text(stringResource(R.string.net_frp_edit_sakura_node_empty), color = glass.dangerCoral, fontSize = 11.sp)
                }
                Box {
                    OutlinedTextField(
                        value = selectedNode?.let { "${it.name}（${it.id}）" }
                            ?: stringResource(R.string.net_frp_edit_sakura_node_pick),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.net_frp_edit_sakura_node)) },
                        singleLine = true,
                        shape = McShape.Control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { if (nodes.isNotEmpty()) nodeMenu = true }
                    )
                    DropdownMenu(
                        expanded = nodeMenu,
                        onDismissRequest = { nodeMenu = false }
                    ) {
                        nodes.forEach { node ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${node.name}（${node.id}）" +
                                            if (node.online) "" else stringResource(R.string.net_frp_edit_sakura_offline)
                                    )
                                },
                                onClick = { selectedNode = node; nodeMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.net_frp_edit_sakura_name)) },
                    singleLine = true,
                    shape = McShape.Control,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tcp", "udp").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.uppercase(), fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.net_frp_edit_sakura_port)) },
                    textStyle = HorizonTabularStyle.copy(fontSize = 14.sp),
                    singleLine = true,
                    shape = McShape.Control,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val node = selectedNode ?: return@Button
                    onConfirm(node, name.ifBlank { "Minexa" }, type, port ?: defaultPort)
                },
                enabled = selectedNode != null && port != null && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = glass.accent, contentColor = Color.White),
                shape = McShape.Control
            ) { Text(stringResource(R.string.net_frp_edit_sakura_create), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s402)) }
        }
    )
}
