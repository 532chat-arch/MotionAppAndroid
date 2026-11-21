package com.example.mcp

import com.example.commands.uid
import com.example.schema.MotionProject
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
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core on-device MCP + REST HTTP server exposing scene creation, scene manipulation
 * (transforms, keyframes, camera, environment, timeline sequencing, layer operations),
 * effect modification, and MP4/GIF/PNG export.
 *
 * Every mutating or export request—whether delivered over MCP JSON-RPC 2.0 (`POST /mcp`)
 * or direct REST endpoints (`/api/scenes`, `/api/scenes/manipulate`, `/api/effects`,
 * `/api/export`)—MUST pass through the mandatory pre-flight [verify] gate first.
 */
class McpServer(
    private val bridge: McpServerBridge,
    private val projectProvider: () -> MotionProject,
    private val activeCompIdProvider: () -> String,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null

    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val requestCounter = AtomicInteger(0)
    private val verifyCounter = AtomicInteger(0)

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var boundPort: Int = DEFAULT_PORT
        private set

    val verifyChecksRun: Int
        get() = verifyCounter.get()

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
        verifyCounter.set(0)

        val srvScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = srvScope
        acceptJob = srvScope.launch {
            while (isRunning && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                srvScope.launch { handleClient(client) }
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

    // ── Mandatory Pre-Flight Verification Step ───────────────────────────────────

    fun verify(operation: VerificationOperation, params: JsonObject): VerificationResult {
        verifyCounter.incrementAndGet()
        return McpRequestVerifier.verify(
            project = projectProvider(),
            activeCompId = activeCompIdProvider(),
            operation = operation,
            params = params,
        )
    }

    private fun verifyTool(toolName: String, arguments: JsonObject): VerificationResult {
        verifyCounter.incrementAndGet()
        return McpRequestVerifier.verifyToolCall(
            project = projectProvider(),
            activeCompId = activeCompIdProvider(),
            toolName = toolName,
            args = arguments,
        )
    }

    // ── Core REST Request Handlers (all gated by verify) ─────────────────────────

    suspend fun handleVerifyEndpoint(params: JsonObject): EndpointResponse {
        val opStr = params["operation"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val operation = when (opStr) {
            "scene", "scene_creation" -> VerificationOperation.SCENE_CREATION
            "manipulate", "scene_manipulation" -> VerificationOperation.SCENE_MANIPULATION
            "effect", "effect_modification" -> VerificationOperation.EFFECT_MODIFICATION
            "export" -> VerificationOperation.EXPORT
            else -> VerificationOperation.GENERAL
        }
        val check = verify(operation, params)
        val payload = check.toJson(projectProvider())
        return if (check.valid) EndpointResponse(200, "OK", payload)
        else EndpointResponse(422, "Unprocessable Entity", payload)
    }

    suspend fun handleSceneCreation(params: JsonObject, baseUrl: String = ""): EndpointResponse {
        val action = params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "create_composition"
        if (action in McpRequestVerifier.SCENE_MANIPULATION_ACTIONS) {
            return handleSceneManipulation(params, baseUrl)
        }
        return executeVerifiedAction(
            endpointLabel = "POST /api/scenes",
            operation = VerificationOperation.SCENE_CREATION,
            toolName = action,
            params = params,
            baseUrl = baseUrl,
        )
    }

    suspend fun handleSceneManipulation(params: JsonObject, baseUrl: String = ""): EndpointResponse {
        val action = params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: ""
        val toolArgs = if (action == "undo_redo") {
            val dir = params["historyAction"]?.jsonPrimitive?.contentOrNull
                ?: params["mode"]?.jsonPrimitive?.contentOrNull
                ?: ""
            JsonObject(params + ("action" to JsonPrimitive(dir)))
        } else {
            params
        }
        return executeVerifiedAction(
            endpointLabel = "POST /api/scenes/manipulate",
            operation = VerificationOperation.SCENE_MANIPULATION,
            toolName = action,
            params = params,
            toolArgs = toolArgs,
            baseUrl = baseUrl,
        )
    }

    suspend fun handleEffectModification(params: JsonObject, baseUrl: String = ""): EndpointResponse {
        val action = params["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "apply_effect_module"
        return executeVerifiedAction(
            endpointLabel = "POST /api/effects",
            operation = VerificationOperation.EFFECT_MODIFICATION,
            toolName = action,
            params = params,
            baseUrl = baseUrl,
        )
    }

    suspend fun handleExport(params: JsonObject, baseUrl: String = ""): EndpointResponse {
        val format = params["format"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        val toolName = if (format == "png") "render_frame_png" else "render_video"
        return executeVerifiedAction(
            endpointLabel = "POST /api/export",
            operation = VerificationOperation.EXPORT,
            toolName = toolName,
            params = params,
            baseUrl = baseUrl,
        )
    }

    /**
     * Single verified execution choke-point for REST handlers: runs [verify] first,
     * short-circuits with HTTP 422 on any missing/invalid asset or parameter, and only
     * dispatches to [McpServerBridge.callTool] once verified.
     */
    private suspend fun executeVerifiedAction(
        endpointLabel: String,
        operation: VerificationOperation,
        toolName: String,
        params: JsonObject,
        toolArgs: JsonObject = params,
        baseUrl: String,
    ): EndpointResponse {
        val t0 = System.currentTimeMillis()
        val reqCount = requestCounter.incrementAndGet()
        val check = verify(operation, params)
        val project = projectProvider()
        if (!check.valid) {
            bridge.onIncomingRpc(endpointLabel, "Blocked by verify step", true, System.currentTimeMillis() - t0, reqCount, sessions.size)
            return EndpointResponse(422, "Unprocessable Entity", check.toJson(project))
        }

        val (outText, isErr) = bridge.callTool(toolName, toolArgs, baseUrl)
        bridge.onIncomingRpc(
            "$endpointLabel:$toolName",
            if (isErr) outText.take(140) else "Verified & applied",
            isErr,
            System.currentTimeMillis() - t0,
            reqCount,
            sessions.size,
        )
        val parsedResult = runCatching { StudioJson.parseToJsonElement(outText) }
            .getOrElse { JsonPrimitive(outText) }
        val body = buildJsonObject {
            put("verified", true)
            put("success", !isErr)
            put("verification", check.toJson(project))
            put("result", parsedResult)
        }
        return if (isErr) EndpointResponse(400, "Bad Request", body) else EndpointResponse(200, "OK", body)
    }

    // ── Core MCP JSON-RPC 2.0 Request Handlers ───────────────────────────────────

    fun handleInitialize(currentSessionId: String?): Pair<JsonObject, String> {
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
        return result to sid
    }

    suspend fun handleToolsList(): JsonObject {
        val tools = bridge.listTools()
        return buildJsonObject {
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
    }

    /**
     * Core MCP `tools/call` handler. Runs [verifyTool] before executing any scene,
     * manipulation, effect, or export tool so unverified or invented inputs are rejected.
     */
    suspend fun handleToolsCall(
        toolName: String,
        arguments: JsonObject,
        baseUrl: String,
    ): Triple<JsonObject, Boolean, String> {
        val check = verifyTool(toolName, arguments)
        val project = projectProvider()
        val (outputText, isErr, logDetail) = if (!check.valid) {
            Triple(check.toJson(project).toString(), true, "Blocked by verify step")
        } else {
            val (out, err) = bridge.callTool(toolName, arguments, baseUrl)
            Triple(out, err, if (err) out.take(140) else "Verified & OK")
        }
        val result = buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", outputText)
                })
            })
            put("isError", isErr)
        }
        return Triple(result, isErr, logDetail)
    }

    suspend fun handleResourcesList(): JsonObject {
        val list = bridge.listResources()
        return buildJsonObject {
            put("resources", buildJsonArray {
                list.forEach { r ->
                    add(buildJsonObject {
                        put("uri", r.uri)
                        put("name", r.name)
                        put("description", r.description)
                        put("mimeType", r.mimeType)
                    })
                }
            })
        }
    }

    suspend fun handleResourcesRead(uri: String): Result<JsonObject> {
        verifyCounter.incrementAndGet()
        val knownUris = bridge.listResources().map { it.uri }.toSet()
        if (uri !in knownUris) {
            return Result.failure(IllegalArgumentException("Resource URI not found: $uri"))
        }
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
        return Result.success(result)
    }

    suspend fun handlePromptsList(): JsonObject {
        val list = bridge.listPrompts()
        return buildJsonObject {
            put("prompts", buildJsonArray {
                list.forEach { p ->
                    add(buildJsonObject {
                        put("name", p.name)
                        put("description", p.description)
                    })
                }
            })
        }
    }

    suspend fun handlePromptsGet(name: String, arguments: JsonObject): Result<JsonObject> {
        verifyCounter.incrementAndGet()
        val messages = bridge.getPrompt(name, arguments)
            ?: return Result.failure(IllegalArgumentException("Unknown prompt: $name"))
        val desc = bridge.listPrompts().find { it.name == name }?.description ?: ""
        return Result.success(
            buildJsonObject {
                put("description", desc)
                put("messages", messages)
            }
        )
    }

    // ── HTTP Transport & Routing ─────────────────────────────────────────────────

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()

            val request = readHttpRequest(input) ?: return
            val hostHeader = request.headers["host"] ?: "${EmbeddedMcpHttpServer.detectLocalIpv4()}:$boundPort"
            val baseUrl = "http://$hostHeader"
            val path = request.path.substringBefore('?')

            when {
                request.method == "OPTIONS" -> {
                    writeHttpResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                }

                request.method == "GET" && (path == "/" || path == "/health") -> {
                    val tools = bridge.listTools()
                    val body = buildJsonObject {
                        put("status", "ok")
                        put("server", SERVER_NAME)
                        put("version", SERVER_VERSION)
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("endpoint", "$baseUrl/mcp")
                        put("verifyGateEnabled", true)
                        put("verifyChecksRun", verifyCounter.get())
                        put("toolsCount", tools.size)
                        put("activeSessions", sessions.size)
                        put("askFirstPolicy", true)
                        put("endpoints", buildJsonObject {
                            put("mcp", "$baseUrl/mcp")
                            put("verify", "$baseUrl/api/verify")
                            put("scenes", "$baseUrl/api/scenes")
                            put("sceneManipulation", "$baseUrl/api/scenes/manipulate")
                            put("effects", "$baseUrl/api/effects")
                            put("export", "$baseUrl/api/export")
                            put("exports", "$baseUrl/exports/{fileName}")
                        })
                    }.toString().toByteArray(Charsets.UTF_8)
                    writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", body)
                }

                request.method == "GET" && path == "/mcp" -> {
                    val body = buildJsonObject {
                        put("name", SERVER_NAME)
                        put("version", SERVER_VERSION)
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("transport", "streamable-http")
                        put("verifyGateEnabled", true)
                        put("message", "Send MCP JSON-RPC 2.0 POST requests to /mcp or REST calls to /api/verify, /api/scenes, /api/scenes/manipulate, /api/effects, /api/export")
                    }.toString().toByteArray(Charsets.UTF_8)
                    writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", body)
                }

                request.method == "DELETE" && path == "/mcp" -> {
                    request.headers["mcp-session-id"]?.let { sessions.remove(it) }
                    writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", """{"closed":true}""".toByteArray(Charsets.UTF_8))
                }

                request.method == "POST" && path == "/api/verify" -> {
                    dispatchRestEndpoint(request, output) { json -> handleVerifyEndpoint(json) }
                }

                request.method == "POST" && (path == "/api/scenes" || path == "/scenes") -> {
                    dispatchRestEndpoint(request, output) { json -> handleSceneCreation(json, baseUrl) }
                }

                request.method == "POST" && (path == "/api/scenes/manipulate" || path == "/scenes/manipulate") -> {
                    dispatchRestEndpoint(request, output) { json -> handleSceneManipulation(json, baseUrl) }
                }

                request.method == "POST" && (path == "/api/effects" || path == "/effects") -> {
                    dispatchRestEndpoint(request, output) { json -> handleEffectModification(json, baseUrl) }
                }

                request.method == "POST" && (path == "/api/export" || path == "/export") -> {
                    dispatchRestEndpoint(request, output) { json -> handleExport(json, baseUrl) }
                }

                request.method == "GET" && path.startsWith("/exports/") -> {
                    val rawName = path.removePrefix("/exports/")
                    val safe = rawName.isNotEmpty() && !rawName.contains("..") && !rawName.contains('/') && !rawName.contains('\\')
                    val entry = if (safe) bridge.resolveExportFile(rawName) else null
                    if (entry != null && entry.file.exists() && entry.file.isFile) {
                        writeHttpResponse(
                            output = output,
                            statusCode = 200,
                            statusText = "OK",
                            contentType = entry.mimeType,
                            body = entry.file.readBytes(),
                            extraHeaders = mapOf("Content-Disposition" to "inline; filename=\"${entry.fileName}\""),
                        )
                    } else {
                        writeHttpResponse(output, 404, "Not Found", "application/json; charset=utf-8", """{"error":"Export file not found"}""".toByteArray(Charsets.UTF_8))
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

    private suspend fun dispatchRestEndpoint(
        request: RawHttpRequest,
        output: OutputStream,
        handler: suspend (JsonObject) -> EndpointResponse,
    ) {
        val rawBody = request.body.toString(Charsets.UTF_8).trim()
        val json = runCatching {
            if (rawBody.isEmpty()) JsonObject(emptyMap()) else StudioJson.parseToJsonElement(rawBody).jsonObject
        }.getOrElse { ex ->
            val err = buildJsonObject {
                put("verified", false)
                put("error", "Invalid JSON payload: ${ex.message}")
            }.toString().toByteArray(Charsets.UTF_8)
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", err)
            return
        }
        val resp = handler(json)
        writeHttpResponse(
            output = output,
            statusCode = resp.statusCode,
            statusText = resp.statusText,
            contentType = "application/json; charset=utf-8",
            body = resp.body.toString().toByteArray(Charsets.UTF_8),
        )
    }

    private suspend fun handleMcpJsonRpcPost(
        request: RawHttpRequest,
        baseUrl: String,
        output: OutputStream,
    ) {
        val rawBody = request.body.toString(Charsets.UTF_8).trim()
        if (rawBody.isEmpty()) {
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", rpcError(JsonNull, -32700, "Empty JSON-RPC body").toString().toByteArray(Charsets.UTF_8))
            return
        }
        val parsed = runCatching { StudioJson.parseToJsonElement(rawBody) }.getOrElse { ex ->
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", rpcError(JsonNull, -32700, "Parse error: ${ex.message}").toString().toByteArray(Charsets.UTF_8))
            return
        }
        var sessionId = request.headers["mcp-session-id"]
        if (parsed is JsonArray) {
            val responses = mutableListOf<JsonObject>()
            for (el in parsed) {
                if (el is JsonObject) {
                    val (resp, sid) = dispatchSingleRpc(el, baseUrl, sessionId)
                    if (sid != null) sessionId = sid
                    if (resp != null) responses.add(resp)
                }
            }
            if (responses.isEmpty()) {
                writeHttpResponse(output, 202, "Accepted", "application/json; charset=utf-8", ByteArray(0), sessionHeaders(sessionId))
            } else {
                writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", JsonArray(responses).toString().toByteArray(Charsets.UTF_8), sessionHeaders(sessionId))
            }
            return
        }
        if (parsed !is JsonObject) {
            writeHttpResponse(output, 400, "Bad Request", "application/json; charset=utf-8", rpcError(JsonNull, -32600, "Invalid JSON-RPC request").toString().toByteArray(Charsets.UTF_8))
            return
        }
        val (resp, sid) = dispatchSingleRpc(parsed, baseUrl, sessionId)
        if (sid != null) sessionId = sid
        if (resp == null) {
            writeHttpResponse(output, 202, "Accepted", "application/json; charset=utf-8", ByteArray(0), sessionHeaders(sessionId))
        } else {
            writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", resp.toString().toByteArray(Charsets.UTF_8), sessionHeaders(sessionId))
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
                    val (result, sid) = handleInitialize(currentSessionId)
                    bridge.onIncomingRpc("initialize", "Client connected (session ${sid.takeLast(6)})", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to sid
                }

                "ping" -> rpcSuccess(idEl, buildJsonObject {}) to currentSessionId

                "tools/list" -> {
                    val result = handleToolsList()
                    bridge.onIncomingRpc("tools/list", "Listed tools", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "tools/call" -> {
                    val toolName = params["name"]?.jsonPrimitive?.contentOrNull
                        ?: return rpcError(idEl, -32602, "Missing tool name") to currentSessionId
                    val toolArgs = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
                    val (result, isErr, detail) = handleToolsCall(toolName, toolArgs, baseUrl)
                    bridge.onIncomingRpc("tools/call:$toolName", detail, isErr, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "resources/list" -> {
                    val result = handleResourcesList()
                    bridge.onIncomingRpc("resources/list", "Listed resources", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "resources/read" -> {
                    val uri = params["uri"]?.jsonPrimitive?.contentOrNull
                        ?: return rpcError(idEl, -32602, "Missing resource uri") to currentSessionId
                    handleResourcesRead(uri).fold(
                        onSuccess = { res ->
                            bridge.onIncomingRpc("resources/read", uri, false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                            rpcSuccess(idEl, res) to currentSessionId
                        },
                        onFailure = { err ->
                            bridge.onIncomingRpc("resources/read", err.message ?: "Not found", true, System.currentTimeMillis() - t0, reqCount, sessions.size)
                            rpcError(idEl, -32602, err.message ?: "Resource not found") to currentSessionId
                        },
                    )
                }

                "prompts/list" -> {
                    val result = handlePromptsList()
                    bridge.onIncomingRpc("prompts/list", "Listed prompts", false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                    rpcSuccess(idEl, result) to currentSessionId
                }

                "prompts/get" -> {
                    val name = params["name"]?.jsonPrimitive?.contentOrNull
                        ?: return rpcError(idEl, -32602, "Missing prompt name") to currentSessionId
                    val promptArgs = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
                    handlePromptsGet(name, promptArgs).fold(
                        onSuccess = { res ->
                            bridge.onIncomingRpc("prompts/get", name, false, System.currentTimeMillis() - t0, reqCount, sessions.size)
                            rpcSuccess(idEl, res) to currentSessionId
                        },
                        onFailure = { err ->
                            bridge.onIncomingRpc("prompts/get", err.message ?: "Unknown prompt", true, System.currentTimeMillis() - t0, reqCount, sessions.size)
                            rpcError(idEl, -32602, err.message ?: "Unknown prompt") to currentSessionId
                        },
                    )
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
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        val lines = headerBuf.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val parts = lines.firstOrNull()?.trim()?.split(' ') ?: return null
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) headers[lines[i].substring(0, idx).trim().lowercase()] = lines[i].substring(idx + 1).trim()
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
        } else ByteArray(0)
        return RawHttpRequest(parts[0].uppercase(), parts[1], headers, body)
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
        for ((k, v) in extraHeaders) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    companion object {
        const val DEFAULT_PORT = EmbeddedMcpHttpServer.DEFAULT_PORT
        const val PROTOCOL_VERSION = EmbeddedMcpHttpServer.PROTOCOL_VERSION
        const val SERVER_NAME = EmbeddedMcpHttpServer.SERVER_NAME
        const val SERVER_VERSION = EmbeddedMcpHttpServer.SERVER_VERSION
        private const val MAX_BODY_BYTES = 16 * 1024 * 1024
    }
}
