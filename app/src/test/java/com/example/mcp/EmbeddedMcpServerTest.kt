package com.example.mcp

import com.example.data.createLostKingdomProject
import com.example.schema.MotionProject
import com.example.schema.StudioJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class EmbeddedMcpServerTest {
    private lateinit var server: EmbeddedMcpHttpServer
    private lateinit var project: MotionProject
    private lateinit var activeCompId: String
    private lateinit var tempExportFile: File
    private var port: Int = 0

    @Before
    fun setUp() {
        project = createLostKingdomProject()
        activeCompId = project.compositions.first().id
        tempExportFile = File.createTempFile("mcp_test_export_", ".mp4").apply {
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
                LocalMcpEngine.readResource(project, uri, listOf(exportEntry), "http://127.0.0.1:$port") to
                    if (uri.endsWith(".md")) "text/markdown" else "application/json"
            override suspend fun listPrompts(): List<McpPromptInfo> = LocalMcpEngine.prompts
            override suspend fun getPrompt(name: String, arguments: JsonObject): JsonArray? =
                McpCatalog.getPromptMessages(name, arguments)

            override suspend fun callTool(name: String, arguments: JsonObject, baseUrl: String): Pair<String, Boolean> {
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

        server = EmbeddedMcpHttpServer(bridge)
        port = server.start(0).getOrThrow()
    }

    @After
    fun tearDown() {
        server.stop()
        tempExportFile.delete()
    }

    @Test
    fun healthEndpointReportsLiveServerAndAskFirstPolicy() {
        val (code, body, _) = httpGet("/health")
        assertEquals(200, code)
        val json = StudioJson.parseToJsonElement(body).jsonObject
        assertEquals("ok", json["status"]?.jsonPrimitive?.content)
        assertEquals(29, json["toolsCount"]?.jsonPrimitive?.int)
        assertEquals(true, json["askFirstPolicy"]?.jsonPrimitive?.boolean)
        assertEquals(EmbeddedMcpHttpServer.PROTOCOL_VERSION, json["protocolVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun initializeReturnsSessionIdAndAskFirstInstructions() {
        val req = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"antigravity","version":"1.0"}}}"""
        val (code, body, headers) = httpPost("/mcp", req)
        assertEquals(200, code)
        val sessionId = headers["Mcp-Session-Id"]
        assertNotNull("Expected Mcp-Session-Id response header", sessionId)

        val rpc = StudioJson.parseToJsonElement(body).jsonObject
        val result = rpc["result"]!!.jsonObject
        assertEquals("2025-03-26", result["protocolVersion"]?.jsonPrimitive?.content)
        val instructions = result["instructions"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(instructions.contains("NEVER INVENT", ignoreCase = true))
        assertTrue(instructions.contains("check_video_requirements"))
    }

    @Test
    fun toolsListReturnsAll29ToolsWithSchemas() {
        val req = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""
        val (code, body, _) = httpPost("/mcp", req)
        assertEquals(200, code)
        val tools = StudioJson.parseToJsonElement(body).jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(29, tools.size)
        for (el in tools) {
            val obj = el.jsonObject
            assertFalse(obj["name"]?.jsonPrimitive?.content.isNullOrBlank())
            val schema = obj["inputSchema"]?.jsonObject
            assertNotNull(schema)
            assertEquals("object", schema?.get("type")?.jsonPrimitive?.content)
        }
    }

    @Test
    fun checkVideoRequirementsFlagsMissingAssetsAndDetails() {
        val callMissing = """{
          "jsonrpc":"2.0",
          "id":3,
          "method":"tools/call",
          "params":{
            "name":"check_video_requirements",
            "arguments":{
              "requiredAssetIds":["nonexistent_hero_png"],
              "requiredCharacterIds":["missing_char"],
              "needsLyrics":true
            }
          }
        }"""
        val (code, body, _) = httpPost("/mcp", callMissing)
        assertEquals(200, code)
        val result = StudioJson.parseToJsonElement(body).jsonObject["result"]!!.jsonObject
        val payloadText = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val payload = StudioJson.parseToJsonElement(payloadText).jsonObject
        assertEquals(false, payload["ready"]?.jsonPrimitive?.boolean)
        assertTrue(payload["missingAssets"]!!.jsonArray.isNotEmpty())
        assertTrue(payload["missingDetails"]!!.jsonArray.isNotEmpty())
        assertTrue(payload["questionsToAskUser"]!!.jsonArray.isNotEmpty())

        // Now pass verified existing asset + explicit video settings
        val existingImageId = project.assets.first().id
        val callReady = """{
          "jsonrpc":"2.0",
          "id":4,
          "method":"tools/call",
          "params":{
            "name":"check_video_requirements",
            "arguments":{
              "requiredAssetIds":["$existingImageId"],
              "width":1280,
              "height":720,
              "durationFrames":180
            }
          }
        }"""
        val (code2, body2, _) = httpPost("/mcp", callReady)
        assertEquals(200, code2)
        val payloadText2 = StudioJson.parseToJsonElement(body2).jsonObject["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val payload2 = StudioJson.parseToJsonElement(payloadText2).jsonObject
        assertEquals(true, payload2["ready"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun mutatingToolsRejectInventedAssetsAndEmptyUserInputs() {
        // 1. Fake sprite asset ID
        val fakeSprite = callToolRpc(10, "add_sprite_layer", """{"assetId":"fake_sprite_123"}""")
        assertTrue(fakeSprite["isError"]!!.jsonPrimitive.boolean)
        val spriteText = fakeSprite["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(spriteText.contains("availableImageAssets"))

        // 2. Fake character ID
        val fakeChar = callToolRpc(11, "add_character_layer", """{"characterId":"fake_char_999"}""")
        assertTrue(fakeChar["isError"]!!.jsonPrimitive.boolean)

        // 3. Empty text layer copy
        val emptyText = callToolRpc(12, "add_layer", """{"name":"Title","kind":"text","text":""}""")
        assertTrue(emptyText["isError"]!!.jsonPrimitive.boolean)

        // 4. Missing resolution on create_composition
        val badComp = callToolRpc(13, "create_composition", """{"name":"Scene 2"}""")
        assertTrue(badComp["isError"]!!.jsonPrimitive.boolean)

        // 5. Empty lyrics text
        val textLayerId = project.compositions.first().layers.first { l ->
            l.objects.any { it is com.example.schema.SceneObjectDocument.TextObject }
        }.id
        val emptyLyrics = callToolRpc(14, "set_lyrics_cues", """{"layerId":"$textLayerId","text":""}""")
        assertTrue(emptyLyrics["isError"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun validCompositionCreationAndPromptGetWorkOverHttp() {
        val created = callToolRpc(
            20,
            "create_composition",
            """{"name":"Intro Shot","width":1280,"height":720,"durationFrames":120}""",
        )
        assertFalse(created["isError"]!!.jsonPrimitive.boolean)
        assertTrue(project.compositions.any { it.name == "Intro Shot" })

        val promptReq = """{"jsonrpc":"2.0","id":21,"method":"prompts/get","params":{"name":"create_animated_video","arguments":{"goal":"Animated product intro"}}}"""
        val (code, body, _) = httpPost("/mcp", promptReq)
        assertEquals(200, code)
        val messages = StudioJson.parseToJsonElement(body).jsonObject["result"]!!.jsonObject["messages"]!!.jsonArray
        assertTrue(messages.isNotEmpty())
        val promptText = messages[0].jsonObject["content"]!!.jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(promptText.contains("check_video_requirements"))
        assertTrue(promptText.contains("Never invent", ignoreCase = true))
    }

    @Test
    fun exportDownloadServesValidFileAndBlocksTraversal() {
        val (code, _, headers) = httpGet("/exports/${tempExportFile.name}")
        assertEquals(200, code)
        assertTrue(headers["Content-Type"].orEmpty().startsWith("video/mp4"))

        val (badCode, _, _) = httpGet("/exports/..%2Fsecret.txt")
        assertEquals(404, badCode)

        val (badCode2, _, _) = httpGet("/exports/../secret.txt")
        assertEquals(404, badCode2)
    }

    @Test
    fun antigravityConfigUsesServerUrlAndValidJson() {
        val jsonText = EmbeddedMcpHttpServer.buildAntigravityConfigJson("http://192.168.1.42:8765/mcp")
        val parsed = StudioJson.parseToJsonElement(jsonText).jsonObject
        val url = parsed["mcpServers"]!!.jsonObject["motion-studio"]!!.jsonObject["serverUrl"]!!.jsonPrimitive.contentOrNull
        assertEquals("http://192.168.1.42:8765/mcp", url)
    }

    private fun callToolRpc(id: Int, toolName: String, argsJson: String): JsonObject {
        val req = """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$toolName","arguments":$argsJson}}"""
        val (code, body, _) = httpPost("/mcp", req)
        assertEquals(200, code)
        return StudioJson.parseToJsonElement(body).jsonObject["result"]!!.jsonObject
    }

    private fun httpGet(path: String): Triple<Int, String, Map<String, String>> {
        val conn = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        return readConn(conn)
    }

    private fun httpPost(path: String, jsonBody: String): Triple<Int, String, Map<String, String>> {
        val conn = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        return readConn(conn)
    }

    private fun readConn(conn: HttpURLConnection): Triple<Int, String, Map<String, String>> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val headers = buildMap {
            conn.headerFields.forEach { (k, v) ->
                if (k != null && !v.isNullOrEmpty()) put(k, v.first())
            }
        }
        conn.disconnect()
        return Triple(code, text, headers)
    }
}
