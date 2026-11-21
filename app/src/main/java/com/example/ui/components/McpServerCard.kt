package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mcp.EmbeddedMcpHttpServer
import com.example.mcp.LocalMcpEngine
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

/**
 * Interactive control card for the embedded Streamable HTTP MCP server.
 * Lets users start/stop the LAN server, switch between Google Antigravity,
 * Standard MCP, and USB ADB snippets, copy config JSON in one tap, and inspect
 * Ask-First policy status and recent exported video download URLs.
 */
@Composable
fun McpServerCard(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var configTab by remember { mutableStateOf("ANTIGRAVITY") }
    var portInput by remember(state.mcpServerPort) { mutableStateOf(state.mcpServerPort.toString()) }

    val hostIp = state.mcpServerHostIp.ifBlank { "127.0.0.1" }
    val endpointUrl = "http://$hostIp:${state.mcpServerPort}/mcp"
    val baseUrl = "http://$hostIp:${state.mcpServerPort}"

    val configSnippet = remember(configTab, endpointUrl, state.mcpServerPort) {
        when (configTab) {
            "STANDARD" -> EmbeddedMcpHttpServer.buildStandardMcpConfigJson(endpointUrl)
            "USB" -> EmbeddedMcpHttpServer.buildUsbAdbInstructions(state.mcpServerPort)
            else -> EmbeddedMcpHttpServer.buildAntigravityConfigJson(endpointUrl)
        }
    }

    val configHint = when (configTab) {
        "STANDARD" -> stringResource(R.string.mcp_hint_standard)
        "USB" -> stringResource(R.string.mcp_hint_usb)
        else -> stringResource(R.string.mcp_hint_antigravity)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioPanel)
            .border(
                width = 1.dp,
                color = if (state.mcpServerRunning) StudioEmerald.copy(alpha = 0.55f) else StudioBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header + Live Status Pill
        SectionHeader(
            title = stringResource(R.string.mcp_live_server_title),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (state.mcpServerRunning) StudioEmerald.copy(alpha = 0.14f)
                            else StudioBg,
                        )
                        .border(
                            1.dp,
                            if (state.mcpServerRunning) StudioEmerald.copy(alpha = 0.45f) else StudioBorder,
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.mcpServerRunning) StudioEmerald else StudioTextSecondary),
                    )
                    Text(
                        text = if (state.mcpServerRunning) {
                            stringResource(R.string.mcp_status_listening)
                        } else {
                            stringResource(R.string.mcp_status_stopped)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.mcpServerRunning) StudioEmerald else StudioTextSecondary,
                    )
                }
            },
        )

        Text(
            text = stringResource(R.string.mcp_live_server_subtitle, LocalMcpEngine.tools.size),
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
        )

        // Start / Stop + Port + Refresh IP Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.mcpServerRunning) {
                Button(
                    onClick = { viewModel.stopMcpServer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioRose.copy(alpha = 0.2f),
                        contentColor = StudioRose,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("mcp_server_stop_btn"),
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mcp_stop_server), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = {
                        val parsed = portInput.toIntOrNull() ?: state.mcpServerPort
                        viewModel.startMcpServer(parsed)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("mcp_server_start_btn"),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mcp_start_server), fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedTextField(
                value = portInput,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(5)
                    portInput = filtered
                    val parsed = filtered.toIntOrNull()
                    if (parsed != null && parsed in 1024..65535 && !state.mcpServerRunning) {
                        viewModel.setMcpServerPort(parsed)
                    }
                },
                label = { Text(stringResource(R.string.mcp_port_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = StudioMonoStyle.copy(color = StudioTextPrimary),
                modifier = Modifier
                    .width(104.dp)
                    .testTag("mcp_server_port_input"),
            )

            IconButton(
                onClick = {
                    viewModel.refreshMcpNetworkIp()
                    viewModel.showFeedback(false, "Refreshed Wi-Fi IP address")
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mcp_refresh_ip_btn"),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.mcp_refresh_ip),
                    tint = StudioCyan,
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.mcpServerRunning) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${state.mcpServerRequestCount} reqs",
                        style = StudioMonoStyle,
                        color = StudioCyan,
                    )
                    Text(
                        text = "${state.mcpServerSessionCount} active",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                    )
                }
            }
        }

        // Error banner if bind failed
        state.mcpServerLastError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = StudioRose,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioRose.copy(alpha = 0.12f))
                    .border(1.dp, StudioRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            )
        }

        // Live Endpoint URL Readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBg)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                tint = if (state.mcpServerRunning) StudioEmerald else StudioTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mcp_endpoint_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary,
                )
                Text(
                    text = endpointUrl,
                    style = StudioMonoStyle,
                    color = StudioCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    copyToClipboard(context, "Motion Studio MCP URL", endpointUrl)
                    viewModel.showFeedback(false, "Copied MCP endpoint URL")
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mcp_copy_url_btn"),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.mcp_copy_url),
                    tint = StudioPrimaryBright,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Ask-First & No-Invention Guardrail Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioAmber.copy(alpha = 0.1f))
                .border(1.dp, StudioAmber.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = StudioAmber,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mcp_ask_first_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StudioAmber,
                )
                Text(
                    text = stringResource(R.string.mcp_ask_first_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                )
            }
        }

        // Antigravity / Standard MCP / USB Config Switcher + One-Tap Copy
        StudioChipRow(
            options = listOf(
                "ANTIGRAVITY" to stringResource(R.string.mcp_tab_antigravity),
                "STANDARD" to stringResource(R.string.mcp_tab_standard),
                "USB" to stringResource(R.string.mcp_tab_usb),
            ),
            selectedKey = configTab,
            onSelect = { configTab = it },
            accent = StudioPrimaryBright,
        )

        Text(
            text = configHint,
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0D12))
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text(
                text = configSnippet,
                style = StudioMonoStyle,
                color = StudioTextPrimary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val label = when (configTab) {
                        "STANDARD" -> "Standard MCP Config"
                        "USB" -> "USB ADB MCP Instructions"
                        else -> "Antigravity mcp_config.json"
                    }
                    copyToClipboard(context, label, configSnippet)
                    viewModel.showFeedback(false, "Copied $label to clipboard")
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("mcp_copy_config_btn"),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (configTab) {
                        "STANDARD" -> stringResource(R.string.mcp_copy_standard_config)
                        "USB" -> stringResource(R.string.mcp_copy_usb_cmd)
                        else -> stringResource(R.string.mcp_copy_antigravity_config)
                    },
                )
            }

            if (!state.mcpServerRunning) {
                OutlinedButton(
                    onClick = {
                        val parsed = portInput.toIntOrNull() ?: state.mcpServerPort
                        viewModel.startMcpServer(parsed)
                        copyToClipboard(context, "Antigravity mcp_config.json", EmbeddedMcpHttpServer.buildAntigravityConfigJson(endpointUrl))
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("mcp_start_and_copy_btn"),
                ) {
                    Text(stringResource(R.string.mcp_start_and_copy))
                }
            }
        }

        // Recent MCP Exports (MP4 / GIF / PNG downloadable over LAN)
        if (state.mcpRecentExports.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBg)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.mcp_recent_exports),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StudioTextPrimary,
                )
                state.mcpRecentExports.take(4).forEach { exp ->
                    val dlUrl = "$baseUrl/exports/${exp.fileName}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = StudioEmerald,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exp.fileName,
                                style = StudioMonoStyle,
                                color = StudioTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${exp.format.uppercase()} · ${(exp.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary,
                            )
                        }
                        IconButton(
                            onClick = {
                                copyToClipboard(context, "Export Download URL", dlUrl)
                                viewModel.showFeedback(false, "Copied export download URL")
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy download URL for ${exp.fileName}",
                                tint = StudioCyan,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}
