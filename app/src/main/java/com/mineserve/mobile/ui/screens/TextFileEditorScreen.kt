package com.mineserve.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.LocalMineHostChrome
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import java.io.File

@Composable
fun TextFileEditorScreen(vm: McViewModel, file: McViewModel.TextEditorFile, onBack: () -> Unit) {
    var text by remember(file.path, file.content) { mutableStateOf(file.content) }
    var confirmRunningSave by remember { mutableStateOf(false) }
    val state by vm.serverState.collectAsState()
    val context = LocalContext.current
    val glass = HorizonGlass
    val inChrome = LocalMineHostChrome.current
    val hasChanges = text != file.content
    val lineCount = remember(text) { if (text.isEmpty()) 1 else text.count { it == '\n' } + 1 }

    val rootModifier = if (inChrome) {
        Modifier.fillMaxSize().background(Color.Transparent)
    } else {
        Modifier.fillMaxSize().background(Color.Transparent).statusBarsPadding()
    }

    Column(rootModifier) {
        // Frosted Glass Editor Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = McSpacing.Page, vertical = 8.dp)
                .horizonGlassPlate(
                    shape = McShape.Card,
                    containerColor = glass.glassCard,
                    borderColor = if (hasChanges) glass.accent.copy(alpha = 0.55f) else glass.hairline,
                    highlighted = hasChanges
                )
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(McIcon.Touch)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.s404),
                    tint = glass.accent,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        file.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (hasChanges) glass.warnAmberSoft else glass.liveGreenSoft)
                            .border(
                                1.dp,
                                (if (hasChanges) glass.warnAmber else glass.liveGreen).copy(alpha = 0.45f),
                                CircleShape
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (hasChanges) "● Chưa lưu · $lineCount dòng" else "✓ $lineCount dòng",
                            style = HorizonTabularStyle,
                            color = if (hasChanges) glass.warnAmber else glass.liveGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    file.path,
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { vm.openTextFile(File(file.path)) },
                modifier = Modifier.size(McIcon.Touch)
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.tfe_refresh),
                    tint = glass.accent,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
            IconButton(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText(file.name, text))
                },
                modifier = Modifier.size(McIcon.Touch)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.crash_copy),
                    tint = glass.accent,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = {
                    if (state.isRunning) confirmRunningSave = true
                    else vm.saveTextFile(file.path, text)
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (hasChanges) glass.accent else glass.accentSoft)
            ) {
                Icon(
                    Icons.Outlined.Save,
                    contentDescription = stringResource(R.string.tfe_save),
                    tint = if (hasChanges) Color.White else glass.accent,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
        }

        // Deep Frosted Monospace Code Plate
        val codePlateBg = if (glass.isLight) {
            glass.glassCardStrong
        } else {
            Color(0xFF0D0C1D).copy(alpha = 0.78f)
        }
        val codeTextColor = if (glass.isLight) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color(0xFFE8ECF8)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = McSpacing.Page, vertical = 6.dp)
                .horizonGlassPlate(
                    shape = RoundedCornerShape(18.dp),
                    containerColor = codePlateBg,
                    borderColor = glass.hairline
                )
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = codeTextColor
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = glass.accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = glass.accent
                ),
                shape = RoundedCornerShape(18.dp),
                minLines = 20
            )
        }
    }

    if (confirmRunningSave) {
        AlertDialog(
            onDismissRequest = { confirmRunningSave = false },
            containerColor = glass.glassChrome,
            title = {
                Text(
                    stringResource(R.string.sm_running),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    stringResource(R.string.tfe_save_running),
                    color = Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRunningSave = false
                        vm.saveTextFile(file.path, text)
                    }
                ) {
                    Text(
                        stringResource(R.string.tfe_save),
                        color = glass.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRunningSave = false }) {
                    Text(stringResource(R.string.props_cancel), color = Muted)
                }
            }
        )
    }
}
