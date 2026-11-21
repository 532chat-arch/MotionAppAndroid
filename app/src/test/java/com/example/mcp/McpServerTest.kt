package com.example.mcp

import com.example.data.createLostKingdomProject
import com.example.schema.AssetKind
import com.example.schema.MotionProject
import com.example.schema.StudioJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class McpServerTest {
    private lateinit var server: McpServer
    private lateinit var project: MotionProject
    private lateinit var activeCompId: String
    private lateinit var tempExportFile: File
    private val toolExecutions = AtomicInteger(0)
    private var port: Int = 0

    @Before
    fun setUp() {
        project = createLostKingdomProject()
        activeCompId = project.compositions.first().id
        toolExecutions.set(0)
        tempExportFile = File.createTempFile("mcpserver_export_", ".mp4").apply {
            writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
            deleteOnExit()
        }

        val exportEntry = McpExportedFileEntry(
            fileName = tempExportFile.name,
            file = tempExportFile,
            mimeType = "video/mp4",
            format = "mp4",
            sizeBytes = tempExportFile.length(),
            createdAt = "2026-01-01T00:00:00Z",
        )

        val bridge = object : McpServerBridge {
            override suspend fun listTools(): List<McpToolInfo> = LocalMcpEngine.tools
            override suspend fun listResources(): List<McpResourceInfo> = LocalMcpEngine.resourcesFor(project)
            override suspend fun readResource(uri: String): Pair<String, String> =
                LocalMcpEngine.readResource(project, uri, listOf(exportEntry), "http://127.0.0.1:$port") to "application/json"
            override suspend fun listPrompts(): List<McpPromptInfo> = LocalMcpEngine.prompts
            override suspend fun getPrompt(name: String, arguments: JsonObject): JsonArray? =
                McpCatalog.getPromptMessages(name, arguments)

            override suspend fun callTool(name: String, arguments: JsonObject, baseUrl: String): Pair<String, Boolean> {
                toolExecutions.incrementAndGet()
                if (name == "render_video" || name == "render_frame_png") {
                    return """{"exported":true,"fileName":"${exportEntry.fileName}","downloadUrl":"$baseUrl/exports/${exportEntry.fileName}"}""" to false
                }
                val outcome = LocalMcpEngine.executeTool(project, activeCompId, name, arguments)
                if (!outcome.isError) {
                    outcome.command?.let { cmd -> project = cmd.execute(project) }
                    outcome.switchCompId?.let { activeCompId = it }
                }
                return outcome.outputJson to outcome.isError
            }

            override fun resolveExportFile(fileName: String): McpExportedFileEntry? =
                if (fileName == exportEntry.fileName) exportEntry else null

            override fun onIncomingRpc(
                method: String,
                detail: String,
                isError: Boolean,
                durationMs: Long,
                totalRequests: Int,
                activeSessions: Int,
            ) = Unit
        }

        server = McpServer(
            bridge = bridge,
            projectProvider = { project },
            activeCompIdProvider = { activeCompId },
        )
        port = server.start(0).getOrThrow()
    }

    @After
    fun tearDown() {
        server.stop()
        tempExportFile.delete()
    }

    @Test
    fun healthExposesVerifyGateAndAllEndpoints() {
        val (code, body) = httpGet("/health")
        assertEquals(200, code)
        val json = StudioJson.parseToJsonElement(body).jsonObject
        assertEquals(true, json["verifyGateEnabled"]?.jsonPrimitive?.boolean)
        val endpoints = json["endpoints"]!!.jsonObject
        assertTrue(endpoints["verify"]!!.jsonPrimitive.content.endsWith("/api/verify"))
        assertTrue(endpoints["scenes"]!!.jsonPrimitive.content.endsWith("/api/scenes"))
        assertTrue(endpoints["effects"]!!.jsonPrimitive.content.endsWith("/api/effects"))
        assertTrue(endpoints["export"]!!.jsonPrimitive.content.endsWith("/api/export"))
    }

    @Test
    fun verifyEndpointValidatesExistingAndMissingAssets() {
        val (badCode, badBody) = httpPost(
            "/api/verify",
            """{"operation":"scene_creation","action":"add_sprite_layer","assetId":"missing_sprite_404"}""",
        )
        assertEquals(422, badCode)
        val badJson = StudioJson.parseToJsonElement(badBody).jsonObject
        assertEquals(false, badJson["verified"]?.jsonPrimitive?.boolean)
        assertTrue(badJson["missingAssets"]!!.jsonArray.isNotEmpty())
        assertTrue(badJson["questionsToAskUser"]!!.jsonArray.isNotEmpty())
        assertEquals(0, toolExecutions.get())

        val realImageId = project.assets.first { it.kind == AssetKind.IMAGE }.id
        val (okCode, okBody) = httpPost(
            "/api/verify",
            """{"operation":"scene_creation","action":"add_sprite_layer","assetId":"$realImageId"}""",
        )
        assertEquals(200, okCode)
        assertEquals(true, StudioJson.parseToJsonElement(okBody).jsonObject["verified"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun sceneCreationEndpointTriggersVerifyBeforeMutating() {
        // Missing width/height/duration -> blocked at verify step (422), tool never executed
        val (blockedCode, blockedBody) = httpPost(
            "/api/scenes",
            """{"action":"create_composition","name":"Incomplete Scene"}""",
        )
        assertEquals(422, blockedCode)
        val blockedJson = StudioJson.parseToJsonElement(blockedBody).jsonObject
        assertEquals(false, blockedJson["verified"]?.jsonPrimitive?.boolean)
        assertTrue(blockedJson["missingParameters"]!!.jsonArray.size >= 3)
        assertEquals(0, toolExecutions.get())
        assertTrue(server.verifyChecksRun >= 1)

        // Valid scene creation -> passes verify then mutates project
        val (okCode, okBody) = httpPost(
            "/api/scenes",
            """{"action":"create_composition","name":"Verified Scene","width":1280,"height":720,"durationFrames":150}""",
        )
        assertEquals(200, okCode)
        val okJson = StudioJson.parseToJsonElement(okBody).jsonObject
        assertEquals(true, okJson["verified"]?.jsonPrimitive?.boolean)
        assertEquals(true, okJson["success"]?.jsonPrimitive?.boolean)
        assertEquals(1, toolExecutions.get())
        assertTrue(project.compositions.any { it.name == "Verified Scene" })
    }

    @Test
    fun effectModificationEndpointTriggersVerifyBeforeApplying() {
        val layerId = project.compositions.first().layers.first().id

        // Invalid effect ID -> blocked before execution
        val (badCode, badBody) = httpPost(
            "/api/effects",
            """{"layerId":"$layerId","effectId":"fx.invented.nonexistent"}""",
        )
        assertEquals(422, badCode)
        assertEquals(0, toolExecutions.get())
        val badJson = StudioJson.parseToJsonElement(badBody).jsonObject
        assertTrue(badJson["invalidParameters"]!!.jsonArray.isNotEmpty())

        // Out-of-bounds intensity -> blocked before execution
        val (badRangeCode, _) = httpPost(
            "/api/effects",
            """{"layerId":"$layerId","effectId":"fx.motion.float","intensity":99.0}""",
        )
        assertEquals(422, badRangeCode)
        assertEquals(0, toolExecutions.get())

        // Valid effect module -> passes verify & applies
        val (okCode, okBody) = httpPost(
            "/api/effects",
            """{"layerId":"$layerId","effectId":"fx.motion.float","intensity":1.2,"profile":"low_end"}""",
        )
        assertEquals(200, okCode)
        val okJson = StudioJson.parseToJsonElement(okBody).jsonObject
        assertEquals(true, okJson["verified"]?.jsonPrimitive?.boolean)
        assertEquals(1, toolExecutions.get())
    }

    @Test
    fun exportEndpointTriggersVerifyBeforeRendering() {
        // Unsupported format -> blocked before render
        val (badCode, _) = httpPost("/api/export", """{"format":"avi"}""")
        assertEquals(422, badCode)
        assertEquals(0, toolExecutions.get())

        // Out-of-range GIF FPS -> blocked before render
        val (badGifCode, _) = httpPost("/api/export", """{"format":"gif","gifFps":120}""")
        assertEquals(422, badGifCode)
        assertEquals(0, toolExecutions.get())

        // Valid MP4 export -> passes verify & returns download URL
        val (okCode, okBody) = httpPost("/api/export", """{"format":"mp4","scope":"active_comp"}""")
        assertEquals(200, okCode)
        val okJson = StudioJson.parseToJsonElement(okBody).jsonObject
        assertEquals(true, okJson["verified"]?.jsonPrimitive?.boolean)
        assertEquals(1, toolExecutions.get())
        assertTrue(okJson["result"]!!.jsonObject["downloadUrl"]!!.jsonPrimitive.content.contains("/exports/"))
    }

    @Test
    fun mcpJsonRpcToolsCallRunsVerifyGateBeforeDispatching() {
        val beforeChecks = server.verifyChecksRun
        val req = """{
          "jsonrpc":"2.0",
          "id":7,
          "method":"tools/call",
          "params":{
            "name":"add_sprite_layer",
            "arguments":{"assetId":"invented_asset_id"}
          }
        }"""
        val (code, body) = httpPost("/mcp", req)
        assertEquals(200, code)
        val result = StudioJson.parseToJsonElement(body).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]?.jsonPrimitive?.boolean)
        assertEquals("Tool should never execute when verify fails", 0, toolExecutions.get())
        assertEquals(beforeChecks + 1, server.verifyChecksRun)
        val payloadText = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val payload = StudioJson.parseToJsonElement(payloadText).jsonObject
        assertEquals(false, payload["verified"]?.jsonPrimitive?.boolean)
        assertFalse(payload["missingAssets"]!!.jsonArray.isEmpty())
    }

    @Test
    fun sceneManipulationHandlerVerifiesTransformsKeyframesCameraAndEnvironment() {
        val layerId = project.compositions.first().layers.first().id

        // 1. Out-of-bounds opacity -> blocked by verify before execution
        val (badOpacityCode, _) = httpPost(
            "/api/scenes/manipulate",
            """{"action":"update_layer_transform","layerId":"$layerId","opacity":2.5}""",
        )
        assertEquals(422, badOpacityCode)
        assertEquals(0, toolExecutions.get())

        // 2. Invalid keyframe property -> blocked before execution
        val (badPropCode, _) = httpPost(
            "/api/scenes/manipulate",
            """{"action":"set_keyframe","layerId":"$layerId","property":"skewZ","frame":12,"value":10}""",
        )
        assertEquals(422, badPropCode)
        assertEquals(0, toolExecutions.get())

        // 3. Invalid camera zoom (<= 0) -> blocked before execution
        val (badZoomCode, _) = httpPost(
            "/api/scenes/manipulate",
            """{"action":"set_camera_keyframe","property":"zoom","frame":10,"value":-1.0}""",
        )
        assertEquals(422, badZoomCode)
        assertEquals(0, toolExecutions.get())

        // 4. Valid keyframe manipulation -> verified & applied
        val (okKfCode, okKfBody) = httpPost(
            "/api/scenes/manipulate",
            """{"action":"set_keyframe","layerId":"$layerId","property":"y","frame":24,"value":320.0,"interpolation":"easeInOut"}""",
        )
        assertEquals(200, okKfCode)
        assertEquals(true, StudioJson.parseToJsonElement(okKfBody).jsonObject["verified"]?.jsonPrimitive?.boolean)
        assertEquals(1, toolExecutions.get())

        // 5. Valid camera keyframe + environment preset via MCP JSON-RPC tools/call
        val camReq = """{
          "jsonrpc":"2.0",
          "id":30,
          "method":"tools/call",
          "params":{"name":"set_camera_keyframe","arguments":{"property":"zoom","frame":36,"value":1.25,"interpolation":"easeOut"}}
        }"""
        val (camCode, camBody) = httpPost("/mcp", camReq)
        assertEquals(200, camCode)
        assertFalse(StudioJson.parseToJsonElement(camBody).jsonObject["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean)
        assertEquals(2, toolExecutions.get())
    }

    @Test
    fun mcpResourcesReadRejectsUnknownUriAndPngExportRejectsOutOfBoundsFrame() {
        val badResReq = """{"jsonrpc":"2.0","id":40,"method":"resources/read","params":{"uri":"motion://composition/does_not_exist"}}"""
        val (resCode, resBody) = httpPost("/mcp", badResReq)
        assertEquals(200, resCode)
        assertTrue(StudioJson.parseToJsonElement(resBody).jsonObject.containsKey("error"))

        val (badFrameCode, _) = httpPost("/api/export", """{"format":"png","scope":"active_comp","frame":999999}""")
        assertEquals(422, badFrameCode)
        assertEquals(0, toolExecutions.get())
    }

    private fun httpGet(path: String): Pair<Int, String> {
        val conn = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        return readConn(conn)
    }

    private fun httpPost(path: String, jsonBody: String): Pair<Int, String> {
        val conn = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        return readConn(conn)
    }

    private fun readConn(conn: HttpURLConnection): Pair<Int, String> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return code to text
    }
}
