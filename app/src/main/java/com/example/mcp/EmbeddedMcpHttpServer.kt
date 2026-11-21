package com.example.mcp

import com.example.commands.uid
import com.example.schema.StudioJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Metadata for an exported file served over LAN at `/exports/{fileName}`.
 */
data class McpExportedFileEntry(
    val fileName: String,
    val file: File,
    val mimeType: String,
    val format: String,
    val sizeBytes: Long,
    val createdAt: String,
)

/**
 * Bridge invoked by [EmbeddedMcpHttpServer] to query live studio state, execute
 * synchronous/asynchronous MCP tools, and stream exported artifacts.
 */
interface McpServerBridge {
    suspend fun listTools(): List<McpToolInfo>
    suspend fun listResources(): List<McpResourceInfo>
    suspend fun readResource(uri: String): Pair<String, String> // (content, mimeType)
    suspend fun listPrompts(): List<McpPromptInfo>
    suspend fun getPrompt(name: String, arguments: JsonObject): JsonArray?
    suspend fun callTool(name: String, arguments: JsonObject, baseUrl: String): Pair<String, Boolean>
    fun resolveExportFile(fileName: String): McpExportedFileEntry?
    fun onIncomingRpc(method: String, detail: String, isError: Boolean, durationMs: Long, totalRequests: Int, activeSessions: Int)
}

/**
 * Lightweight, zero-dependency Streamable HTTP MCP Server (protocol `2025-03-26`)
 * designed for low-end Android tablets.
 *
 * Runs over local Wi-Fi (open access, no token required) or USB (`adb forward`),
 * enabling Google Antigravity, Cursor, Claude, and other MCP hosts to inspect
 * projects, run tools live on the studio canvas, and download rendered MP4/GIF videos.
 */
class EmbeddedMcpHttpServer(
    private val bridge: McpServerBridge,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null

    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val requestCounter = AtomicInteger(0)

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var boundPort: Int = DEFAULT_PORT
        private set

    /**
     * Starts listening on `0.0.0.0:[port]` (pass `0` in unit tests for an ephemeral port).
     */
    fun start(port: Int = DEFAULT_PORT): Result<Int> = runCatching {
        stop()
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("0.0.0.0", port))
        }
        serverSocket = socket
        boundPort = socket.localPort
        isRunning = true
        sessions.clear()
        requestCounter.set(0)

        val srvScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = srvScope
        acceptJob = srvScope.launch {
            while (isRunning && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                srvScope.launch {
                    handleClient(client)
                }
            }
        }
        boundPort
    }

    fun stop() {
        isRunning = false
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
        sessions.clear()
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()

            val request = readHttpRequest(input) ?: return
            val hostHeader = request.headers["host"] ?: "${detectLocalIpv4()}:$boundPort"
            val baseUrl = "http://$hostHeader"
            val path = request.path.substringBefore('?')

            when {
                request.method == "OPTIONS" -> {
                    writeHttpResponse(
                        output = output,
                        statusCode = 204,
                        statusText = "No Content",
                        contentType = "text/plain",
                        body = ByteArray(0),
                    )
                }

                request.method == "GET" && (path == "/" || path == "/health") -> {
                    val tools = bridge.listTools()
                    val body = buildJsonObject {
                        put("status", "ok")
                        put("server", SERVER_NAME)
                        put("version", SERVER_VERSION)
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("endpoint", "$baseUrl/mcp")
                        put("toolsCount", tools.size)
                        put("activeSessions", sessions.size)
                        put("askFirstPolicy", true)
                    }.toString().toByteArray(Charsets.UTF_8)
                    writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", body)
                }

                request.method == "GET" && path == "/mcp" -> {
                    val body = buildJsonObject {
                        put("name", SERVER_NAME)
                        put("version", SERVER_VERSION)
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("transport", "streamable-http")
                        put("message", "Send MCP JSON-RPC 2.0 POST requests to /mcp")
                    }.toString().toByteArray(Charsets.UTF_8)
                    writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", body)
                }

                request.method == "DELETE" && path == "/mcp" -> {
                    request.headers["mcp-session-id"]?.let { sessions.remove(it) }
                    writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", """{"closed":true}""".toByteArray(Charsets.UTF_8))
                }

                request.method == "GET" && path.startsWith("/exports/") -> {
                    val rawName = path.removePrefix("/exports/")
                    val safe = rawName.isNotEmpty() &&
                        !rawName.contains("..") &&
                        !rawName.contains('/') &&
                        !rawName.contains('\\')
                    val entry = if (safe) bridge.resolveExportFile(rawName) else null
                    if (entry != null && entry.file.exists() && entry.file.isFile) {
                        val bytes = entry.file.readBytes()
                        writeHttpResponse(
                            output = output,
                            statusCode = 200,
                            statusText = "OK",
                            contentType = entry.mimeType,
                            body = bytes,
                            extraHeaders = mapOf(
                                "Content-Disposition" to "inline; filename=\"${entry.fileName}\"",
                            ),
                        )
                    } else {
                        val err = """{"error":"Export file not found"}""".toByteArray(Charsets.UTF_8)
                        writeHttpResponse(output, 404, "Not Found", "application/json; charset=utf-8", err)
                    }
                }

                request.method == "POST" && (path == "/mcp" || path == "/") -> {
                    handleMcpJsonRpcPost(request, baseUrl, output)
                }

                else -> {
                    val err = """{"error":"Not found: ${request.method} $path"}""".toByteArray(Charsets.UTF_8)
                    writeHttpResponse(output, 404, "Not Found", "application/json; charset=utf-8", err)
                }
            }
        }
    }

    private suspend fun handleMcpJsonRpcPost(
        request: RawHttpRequest,
        baseUrl: String,
        output: OutputStream,
    ) {
        val rawBody = request.body.toString(Charsets.UTF_8).trim()
        if (rawBody.isEmpty()) {
            val err = rpcError(JsonNull, -32700, "Empty JSON-RPC body").toString().toByteArray(Charsets.UTF_8)
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", err)
            return
        }

        val parsed = runCatching { StudioJson.parseToJsonElement(rawBody) }.getOrElse { ex ->
            val err = rpcError(JsonNull, -32700, "Parse error: ${ex.message}").toString().toByteArray(Charsets.UTF_8)
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", err)
            return
        }

        val incomingSessionId = request.headers["mcp-session-id"]
        var responseSessionId = incomingSessionId

        if (parsed is JsonArray) {
            val responses = mutableListOf<JsonObject>()
            for (el in parsed) {
                if (el is JsonObject) {
                    val (resp, sid) = dispatchSingleRpc(el, baseUrl, responseSessionId)
                    if (sid != null) responseSessionId = sid
                    if (resp != null) responses.add(resp)
                }
            }
            if (responses.isEmpty()) {
                writeHttpResponse(output, 202, "Accepted", "application/json; charset=utf-8", ByteArray(0), sessionHeaders(responseSessionId))
            } else {
                val arrBytes = JsonArray(responses).toString().toByteArray(Charsets.UTF_8)
                writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", arrBytes, sessionHeaders(responseSessionId))
            }
            return
        }

        if (parsed !is JsonObject) {
            val err = rpcError(JsonNull, -32600, "Invalid JSON-RPC request").toString().toByteArray(Charsets.UTF_8)
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", err)
            return
        }

        val (resp, sid) = dispatchSingleRpc(parsed, baseUrl, responseSessionId)
        if (sid != null) responseSessionId = sid
        if (resp == null) {
            writeHttpResponse(output, 202, "Accepted", "application/json; charset=utf-8", ByteArray(0), sessionHeaders(responseSessionId))
        } else {
            val body = resp.toString().toByteArray(Charsets.UTF_8)
            writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", body, sessionHeaders(responseSessionId))
        }
    }

    private suspend fun dispatchSingleRpc(
        rpc: JsonObject,
        baseUrl: String,
        currentSessionId: String?,
    ): Pair<JsonObject?, String?> {
        val t0 = System.currentTimeMillis()
        val idEl: JsonElement? = rpc["id"]
        val method = rpc["method"]?.jsonPrimitive?.contentOrNull
            ?: return rpcError(idEl ?: JsonNull, -32600, "Missing method") to currentSessionId
        val params = (rpc["params"] as? JsonObject) ?: JsonObject(emptyMap())
        val isNotification = idEl == null || method.startsWith("notifications/")

        val reqCount = requestCounter.incrementAndGet()

        if (isNotification) {
            bridge.onIncomingRpc(method, "Notification received", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
            return null to currentSessionId
        }

        return try {
            when (method) {
                "initialize" -> {
                    val sid = currentSessionId ?: uid("mcpsess")
                    sessions.add(sid)
                    val result = buildJsonObject {
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("capabilities", buildJsonObject {
                            put("tools", buildJsonObject { put("listChanged", false) })
                            put("resources", buildJsonObject { put("subscribe", false); put("listChanged", false) })
                            put("prompts", buildJsonObject { put("listChanged", false) })
                        })
                        put("serverInfo", buildJsonObject {
                            put("name", SERVER_NAME)
                            put("title", "Motion Studio Pro MCP Server")
                            put("version", SERVER_VERSION)
                        })
                        put("instructions", McpCatalog.SERVER_INSTRUCTIONS)
                    }
                    bridge.onIncomingRpc("initialize", "Client connected (session ${sid.takeLast(6)})", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to sid
                }

                "ping" -> {
                    rpcSuccess(idEl, buildJsonObject {}) to currentSessionId
                }

                "tools/list" -> {
                    val tools = bridge.listTools()
                    val result = buildJsonObject {
                        put("tools", buildJsonArray {
                            for (t in tools) {
                                add(buildJsonObject {
                                    put("name", t.name)
                                    if (t.title.isNotBlank()) put("title", t.title)
                                    put("description", t.description)
                                    put("inputSchema", t.inputSchema)
                                })
                            }
                        })
                    }
                    bridge.onIncomingRpc("tools/list", "Listed ${tools.size} tools", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "tools/call" -> {
                    val toolName = params["name"]?.jsonPrimitive?.contentOrNull
                        ?: return rpcError(idEl, -32602, "Missing tool name") to currentSessionId
                    val toolArgs = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
                    val (outputText, isErr) = bridge.callTool(toolName, toolArgs, baseUrl)
                    val result = buildJsonObject {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", outputText)
                            })
                        })
                        put("isError", isErr)
                    }
                    bridge.onIncomingRpc(
                        "tools/call:$toolName",
                        if (isErr) outputText.take(140) else "OK",
                        isErr,
                        System.currentTimeMillis() - t0,
                        reqCount,
                        sessions.size,
                    )
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "resources/list" -> {
                    val list = bridge.listResources()
                    val result = buildJsonObject {
                        put("resources", buildJsonArray {
                            for (r in list) {
                                add(buildJsonObject {
                                    put("uri", r.uri)
                                    put("name", r.name)
                                    put("description", r.description)
                                    put("mimeType", r.mimeType)
                                })
                            }
                        })
                    }
                    bridge.onIncomingRpc("resources/list", "Listed ${list.size} resources", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "resources/read" -> {
                    val uri = params["uri"]?.jsonPrimitive?.contentOrNull
                        ?: return rpcError(idEl, -32602, "Missing resource uri") to currentSessionId
                    val (content, mimeType) = bridge.readResource(uri)
                    val result = buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("uri", uri)
                                put("mimeType", mimeType)
                                put("text", content)
                            })
                        })
                    }
                    bridge.onIncomingRpc("resources/read", uri, false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "prompts/list" -> {
                    val list = bridge.listPrompts()
                    val result = buildJsonObject {
                        put("prompts", buildJsonArray {
                            for (p in list) {
                                add(buildJsonObject {
                                    put("name", p.name)
                                    put("description", p.description)
                                })
                            }
                        })
                    }
                    bridge.onIncomingRpc("prompts/list", "Listed ${list.size} prompts", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "prompts/get" -> {
                    val name = params["name"]?.jsonPrimitive?.contentOrNull
                        ?: return rpcError(idEl, -32602, "Missing prompt name") to currentSessionId
                    val promptArgs = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
                    val messages = bridge.getPrompt(name, promptArgs)
                        ?: return rpcError(idEl, -32602, "Unknown prompt: $name") to currentSessionId
                    val desc = bridge.listPrompts().find { it.name == name }?.description ?: ""
                    val result = buildJsonObject {
                        put("description", desc)
                        put("messages", messages)
                    }
                    bridge.onIncomingRpc("prompts/get", name, false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                else -> {
                    bridge.onIncomingRpc(method, "Method not found", true, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcError(idEl, -32601, "Method not found: $method") to currentSessionId
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            bridge.onIncomingRpc(method, e.message ?: "Internal server error", true, System.currentTimeMillis() - t0, reqCount, sessions.size)
            rpcError(idEl, -32603, "Internal error: ${e.message}") to currentSessionId
        }
    }

    private fun rpcSuccess(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun rpcError(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }

    private fun sessionHeaders(sessionId: String?): Map<String, String> =
        if (!sessionId.isNullOrBlank()) mapOf("Mcp-Session-Id" to sessionId) else emptyMap()

    private data class RawHttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private fun readHttpRequest(input: InputStream): RawHttpRequest? {
        val headerBuf = ByteArrayOutputStream(1024)
        var prev1 = -1
        var prev2 = -1
        var prev3 = -1
        while (true) {
            val b = input.read()
            if (b == -1) return null
            headerBuf.write(b)
            if (headerBuf.size() > 64 * 1024) return null
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                break
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }

        val headerText = headerBuf.toString(Charsets.ISO_8859_1.name())
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim() ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val path = parts[1]

        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
        val body = if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(buf, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            if (read == contentLength) buf else buf.copyOf(read)
        } else {
            ByteArray(0)
        }

        return RawHttpRequest(method, path, headers, body)
    }

    private fun writeHttpResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n")
        sb.append("Content-Type: ").append(contentType).append("\r\n")
        sb.append("Content-Length: ").append(body.size).append("\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n")
        sb.append("Access-Control-Allow-Headers: Content-Type, Accept, Mcp-Session-Id, Authorization\r\n")
        sb.append("Access-Control-Expose-Headers: Mcp-Session-Id\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in extraHeaders) {
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val PROTOCOL_VERSION = "2025-03-26"
        const val SERVER_NAME = "motion-studio-pro"
        const val SERVER_VERSION = "1.0.0"
        private const val MAX_BODY_BYTES = 16 * 1024 * 1024 // 16 MB for base64 sprite uploads

        /**
         * Discovers the device's local Wi-Fi IPv4 address (prefers `wlan*` / private LAN ranges).
         */
        fun detectLocalIpv4(): String {
            return runCatching {
                val candidates = mutableListOf<Pair<Int, String>>()
                val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
                while (ifaces.hasMoreElements()) {
                    val iface = ifaces.nextElement()
                    if (!iface.isUp || iface.isLoopback) continue
                    val name = iface.name.lowercase()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            val priority = when {
                                name.startsWith("wlan") || name.startsWith("wifi") -> 0
                                ip.startsWith("192.168.") -> 1
                                ip.startsWith("10.") -> 2
                                ip.startsWith("172.") -> 3
                                else -> 4
                            }
                            candidates.add(priority to ip)
                        }
                    }
                }
                candidates.minByOrNull { it.first }?.second ?: "127.0.0.1"
            }.getOrDefault("127.0.0.1")
        }

        /**
         * Generates ready-to-paste Google Antigravity `mcp_config.json` using `serverUrl`.
         */
        fun buildAntigravityConfigJson(endpointUrl: String): String =
            """{
  "mcpServers": {
    "motion-studio": {
      "serverUrl": "$endpointUrl"
    }
  }
}"""

        /**
         * Generates standard Streamable HTTP MCP client config (`url`) for Cursor / Claude / CLI.
         */
        fun buildStandardMcpConfigJson(endpointUrl: String): String =
            """{
  "mcpServers": {
    "motion-studio": {
      "url": "$endpointUrl"
    }
  }
}"""

        /**
         * Generates USB ADB forwarding command + localhost Antigravity config snippet.
         */
        fun buildUsbAdbInstructions(port: Int): String =
            """adb forward tcp:$port tcp:$port

Then set serverUrl in mcp_config.json to:
http://127.0.0.1:$port/mcp"""
    }
}
