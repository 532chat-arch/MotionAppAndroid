package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mcp.LocalMcpEngine
import com.example.ui.components.McpServerCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioChipRow
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
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.launch

@Composable
fun McpWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var selectedTargetId by remember { mutableStateOf("LOCAL") }
    var activeSubTab by remember { mutableStateOf("TOOLS") }
    var selectedToolName by remember { mutableStateOf(LocalMcpEngine.tools.first().name) }
    var argsJson by remember { mutableStateOf(LocalMcpEngine.tools.first().sampleArgsJson) }
    var lastOutput by remember { mutableStateOf("") }
    var lastOutputIsError by remember { mutableStateOf(false) }
    var isRunningTool by remember { mutableStateOf(false) }

    var newSrvName by remember { mutableStateOf("") }
    var newSrvUrl by remember { mutableStateOf("") }
    var newSrvToken by remember { mutableStateOf("") }

    val selectedExternal = state.externalMcpServers.find { it.id == selectedTargetId }
    val isLocal = selectedExternal == null

    val toolsList = if (isLocal) LocalMcpEngine.tools else selectedExternal.tools
    val resourcesList = if (isLocal) LocalMcpEngine.resourcesFor(state.project) else selectedExternal.resources
    val promptsList = if (isLocal) LocalMcpEngine.prompts else selectedExternal.prompts

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBg),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Live On-Device MCP HTTP Server (Google Antigravity / LAN / USB)
            item {
                McpServerCard(state = state, viewModel = viewModel)
            }

            // Target Server Switcher
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.mcp_title),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(StudioEmerald))
                                Text("Local Ready", style = MaterialTheme.typography.labelSmall, color = StudioEmerald)
                            }
                        },
                    )

                    val targets = buildList {
                        add("LOCAL" to "Local Studio Engine (${LocalMcpEngine.tools.size})")
                        state.externalMcpServers.forEach { s ->
                            add(s.id to "${s.name} (${s.status})")
                        }
                    }
                    StudioChipRow(
                        options = targets,
                        selectedKey = if (isLocal) "LOCAL" else selectedTargetId,
                        onSelect = { id ->
                            selectedTargetId = id
                            val list = if (id == "LOCAL") LocalMcpEngine.tools else state.externalMcpServers.find { it.id == id }?.tools.orEmpty()
                            list.firstOrNull()?.let {
                                selectedToolName = it.name
                                argsJson = it.sampleArgsJson
                            }
                        },
                    )

                    if (selectedExternal != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedExternal.url, style = StudioMonoStyle, color = StudioCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                selectedExternal.errorMessage?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = StudioRose)
                                }
                            }
                            IconButton(onClick = {
                                viewModel.removeExternalMcpServer(selectedExternal.id)
                                selectedTargetId = "LOCAL"
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Disconnect server", tint = StudioRose)
                            }
                        }
                    }
                }
            }

            // Connect External Streamable HTTP MCP Server
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionHeader(title = stringResource(R.string.mcp_external_servers))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newSrvName,
                            onValueChange = { newSrvName = it },
                            label = { Text("Label") },
                            singleLine = true,
                            modifier = Modifier.weight(0.4f),
                        )
                        OutlinedTextField(
                            value = newSrvUrl,
                            onValueChange = { newSrvUrl = it },
                            label = { Text("Streamable HTTP MCP URL") },
                            placeholder = { Text("https://.../mcp") },
                            singleLine = true,
                            modifier = Modifier.weight(0.6f).testTag("mcp_url_input"),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = newSrvToken,
                            onValueChange = { newSrvToken = it },
                            label = { Text("Bearer Token (optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                if (newSrvUrl.isNotBlank()) {
                                    viewModel.connectExternalMcpServer(newSrvName, newSrvUrl, newSrvToken)
                                    newSrvName = ""
                                    newSrvUrl = ""
                                    newSrvToken = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                            modifier = Modifier.testTag("mcp_connect_btn"),
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Connect")
                        }
                    }
                }
            }

            // Tools / Resources / Prompts Explorer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StudioChipRow(
                        options = listOf(
                            "TOOLS" to "Tools (${toolsList.size})",
                            "RESOURCES" to "Resources (${resourcesList.size})",
                            "PROMPTS" to "Prompts (${promptsList.size})",
                        ),
                        selectedKey = activeSubTab,
                        onSelect = { activeSubTab = it },
                        accent = StudioCyan,
                    )

                    when (activeSubTab) {
                        "TOOLS" -> {
                            if (toolsList.isEmpty()) {
                                Text("No tools reported by this server.", color = StudioTextSecondary)
                            } else {
                                StudioChipRow(
                                    options = toolsList.map { it.name to it.name },
                                    selectedKey = selectedToolName,
                                    onSelect = { name ->
                                        selectedToolName = name
                                        toolsList.find { it.name == name }?.let { argsJson = it.sampleArgsJson }
                                    },
                                )
                                val toolMeta = toolsList.find { it.name == selectedToolName }
                                if (toolMeta != null) {
                                    Text(toolMeta.description, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                }
                                OutlinedTextField(
                                    value = argsJson,
                                    onValueChange = { argsJson = it },
                                    label = { Text("JSON Arguments") },
                                    minLines = 3,
                                    maxLines = 6,
                                    textStyle = StudioMonoStyle.copy(color = StudioTextPrimary),
                                    modifier = Modifier.fillMaxWidth().testTag("mcp_args_input"),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !isRunningTool,
                                        onClick = {
                                            scope.launch {
                                                isRunningTool = true
                                                try {
                                                    val (out, isErr) = if (isLocal) {
                                                        viewModel.executeLocalMcpToolAsync(selectedToolName, argsJson)
                                                    } else {
                                                        viewModel.invokeExternalMcpTool(selectedExternal, selectedToolName, argsJson)
                                                    }
                                                    lastOutput = out
                                                    lastOutputIsError = isErr
                                                } finally {
                                                    isRunningTool = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                                        modifier = Modifier.testTag("mcp_execute_tool_btn"),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isRunningTool) "Running…" else stringResource(R.string.mcp_execute_tool))
                                    }
                                    if (lastOutput.startsWith("{")) {
                                        OutlinedButton(
                                            onClick = { viewModel.applyMcpJsonAsProject(lastOutput) },
                                            modifier = Modifier.testTag("mcp_apply_json_btn"),
                                        ) {
                                            Text("Apply JSON to Stage")
                                        }
                                    }
                                }
                            }
                        }

                        "RESOURCES" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                resourcesList.forEach { res ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(StudioBg)
                                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (isLocal) {
                                                    lastOutput = LocalMcpEngine.readResource(
                                                        project = state.project,
                                                        uri = res.uri,
                                                        recentExports = state.mcpRecentExports,
                                                        baseUrl = viewModel.getLiveMcpBaseUrl(),
                                                    )
                                                    lastOutputIsError = false
                                                } else {
                                                    scope.launch {
                                                        val (txt, err) = viewModel.readExternalMcpResource(selectedExternal, res.uri)
                                                        lastOutput = txt
                                                        lastOutputIsError = err
                                                    }
                                                }
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(res.name, style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                                            Text(res.uri, style = StudioMonoStyle, color = StudioCyan)
                                            if (res.description.isNotBlank()) {
                                                Text(res.description, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                            }
                                        }
                                        Text(stringResource(R.string.mcp_read_resource), style = MaterialTheme.typography.labelMedium, color = StudioPrimaryBright)
                                    }
                                }
                            }
                        }

                        "PROMPTS" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                promptsList.forEach { pr ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(StudioBg)
                                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(pr.name, style = StudioMonoStyle, color = StudioAmber, fontWeight = FontWeight.SemiBold)
                                        Text(pr.description, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    if (lastOutput.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A0D12))
                                .border(
                                    1.dp,
                                    if (lastOutputIsError) StudioRose.copy(alpha = 0.6f) else StudioEmerald.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = if (lastOutputIsError) StudioRose else StudioEmerald, modifier = Modifier.size(14.dp))
                                Text(
                                    if (lastOutputIsError) "Response Error" else "Response Payload",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (lastOutputIsError) StudioRose else StudioEmerald,
                                )
                            }
                            Text(
                                text = lastOutput.take(2000),
                                style = StudioMonoStyle,
                                color = StudioTextPrimary,
                            )
                        }
                    }
                }
            }

            // Protocol Activity Log
            item {
                SectionHeader(
                    title = stringResource(R.string.mcp_activity_log),
                    trailing = {
                        if (state.mcpLogs.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearMcpLogs() }, modifier = Modifier.testTag("mcp_clear_logs_btn")) {
                                Icon(Icons.Default.ClearAll, contentDescription = "Clear activity log", tint = StudioTextSecondary)
                            }
                        }
                    },
                )
            }

            if (state.mcpLogs.isEmpty()) {
                item {
                    Text(
                        "Execute any local studio tool or connect an external MCP server to inspect live JSON-RPC activity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioTextSecondary,
                    )
                }
            } else {
                items(state.mcpLogs, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioPanel)
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = if (entry.isError) StudioRose else StudioCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${entry.method} · ${entry.serverName}",
                                style = StudioMonoStyle,
                                color = StudioTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isError) StudioRose else StudioTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("${entry.durationMs}ms", style = StudioMonoStyle, color = StudioTextSecondary)
                    }
                }
            }
        }
    }
}
