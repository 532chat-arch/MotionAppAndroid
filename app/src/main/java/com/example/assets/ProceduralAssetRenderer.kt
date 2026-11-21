package com.example.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ProceduralAssetRenderer {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun clearCache() {
        cache.evictAll()
    }

    fun getBitmap(context: Context?, dataUrl: String): Bitmap? {
        if (dataUrl.isEmpty()) return null
        cache.get(dataUrl)?.let { if (!it.isRecycled) return it }

        val bmp = when {
            dataUrl.startsWith("procedural://") -> renderProcedural(dataUrl.removePrefix("procedural://"))
            dataUrl.startsWith("file://") -> {
                val path = Uri.parse(dataUrl).path
                if (path != null && File(path).exists()) decodeScaledFile(path) else null
            }
            dataUrl.startsWith("/") && File(dataUrl).exists() -> decodeScaledFile(dataUrl)
            dataUrl.startsWith("content://") && context != null -> decodeContentUri(context, Uri.parse(dataUrl))
            dataUrl.startsWith("data:image/") && dataUrl.contains("base64,") -> {
                val b64 = dataUrl.substringAfter("base64,")
                runCatching {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            else -> null
        }

        if (bmp != null) {
            cache.put(dataUrl, bmp)
        }
        return bmp
    }

    private fun decodeScaledFile(path: String): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val maxDim = 2048
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, decodeOpts)
    }.getOrNull()

    private fun decodeContentUri(context: Context, uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()

    private fun renderProcedural(key: String): Bitmap = when (key) {
        "mountains_far" -> drawMountainsFar()
        "forest_mid" -> drawForestMid()
        "archway_fg" -> drawArchwayFg()
        "traveler_sheet" -> drawTravelerSpriteSheet()
        "sakura_portrait" -> drawSakuraPortrait()
        "sakura_hair" -> drawSakuraHair()
        "sakura_scarf" -> drawSakuraScarf()
        "stage_horizon" -> drawStageHorizon()
        else -> drawFallbackTile()
    }

    private fun drawMountainsFar(): Bitmap {
        val w = 1600
        val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Moon glow
        p.shader = RadialGradient(1180f, 120f, 220f, Color.argb(140, 224, 242, 254), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(1180f, 120f, 220f, p)
        p.shader = null
        p.color = Color.parseColor("#E0F2FE")
        c.drawCircle(1180f, 120f, 52f, p)

        // Distant mountain ridge
        p.shader = LinearGradient(0f, 160f, 0f, h.toFloat(), Color.parseColor("#1E3A5F"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP)
        val backPath = Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(0f, 380f)
            lineTo(200f, 220f)
            lineTo(420f, 340f)
            lineTo(680f, 165f)
            lineTo(940f, 330f)
            lineTo(1200f, 195f)
            lineTo(1430f, 310f)
            lineTo(1600f, 240f)
            lineTo(1600f, h.toFloat())
            close()
        }
        c.drawPath(backPath, p)

        // Secondary darker peaks
        p.shader = LinearGradient(0f, 260f, 0f, h.toFloat(), Color.parseColor("#162A45"), Color.parseColor("#090F1E"), Shader.TileMode.CLAMP)
        val frontPath = Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(0f, 450f)
            lineTo(290f, 300f)
            lineTo(540f, 415f)
            lineTo(830f, 275f)
            lineTo(1090f, 405f)
            lineTo(1360f, 290f)
            lineTo(1600f, 390f)
            lineTo(1600f, h.toFloat())
            close()
        }
        c.drawPath(frontPath, p)

        // Snow ridge highlights
        p.shader = null
        p.color = Color.argb(65, 147, 197, 253)
        val highlight = Path().apply {
            moveTo(680f, 165f); lineTo(620f, 225f); lineTo(688f, 210f); lineTo(735f, 232f); close()
            moveTo(1200f, 195f); lineTo(1150f, 246f); lineTo(1208f, 236f); lineTo(1252f, 252f); close()
        }
        c.drawPath(highlight, p)
        return bmp
    }

    private fun drawForestMid(): Bitmap {
        val w = 1600
        val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Rolling dark hill base
        p.shader = LinearGradient(0f, 180f, 0f, h.toFloat(), Color.parseColor("#132A3A"), Color.parseColor("#08111B"), Shader.TileMode.CLAMP)
        val hill = Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(0f, 300f)
            cubicTo(420f, 220f, 1120f, 360f, 1600f, 270f)
            lineTo(1600f, h.toFloat())
            close()
        }
        c.drawPath(hill, p)
        p.shader = null

        // Pine trees silhouettes
        for (i in 0 until 26) {
            val tx = i * 64f + ((i * 37) % 28)
            val treeH = 120f + ((i * 53) % 95)
            val baseY = 320f + sin(i * 0.45).toFloat() * 35f
            p.color = if (i % 2 == 0) Color.parseColor("#0D2230") else Color.parseColor("#0A1925")
            for (tier in 0..2) {
                val topY = baseY - treeH + tier * (treeH * 0.24f)
                val botY = topY + treeH * 0.48f
                val halfW = (18f + tier * 11f) * (treeH / 160f)
                val tri = Path().apply {
                    moveTo(tx, topY)
                    lineTo(tx - halfW, botY)
                    lineTo(tx + halfW, botY)
                    close()
                }
                c.drawPath(tri, p)
            }
        }

        // Ground footpath shimmer
        p.color = Color.argb(40, 96, 165, 250)
        c.drawRoundRect(RectF(0f, 410f, w.toFloat(), 438f), 12f, 12f, p)
        return bmp
    }

    private fun drawArchwayFg(): Bitmap {
        val w = 1400
        val h = 760
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#1E293B"), Color.parseColor("#090D16"), Shader.TileMode.CLAMP)
        // Left stone pillar
        c.drawRoundRect(RectF(40f, 110f, 185f, h.toFloat()), 14f, 14f, p)
        c.drawRoundRect(RectF(20f, 80f, 210f, 130f), 8f, 8f, p)
        // Right stone pillar
        c.drawRoundRect(RectF(1215f, 110f, 1360f, h.toFloat()), 14f, 14f, p)
        c.drawRoundRect(RectF(1190f, 80f, 1380f, 130f), 8f, 8f, p)
        // Top lintel arch
        val arch = Path().apply {
            moveTo(20f, 110f)
            lineTo(1380f, 110f)
            lineTo(1380f, 168f)
            quadTo(700f, 40f, 20f, 168f)
            close()
        }
        c.drawPath(arch, p)
        p.shader = null

        // Glowing cyan runes on pillars
        p.color = Color.parseColor("#38BDF8")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        for (y in listOf(220f, 320f, 420f, 520f)) {
            c.drawCircle(112f, y, 14f, p)
            c.drawLine(112f, y - 22f, 112f, y + 22f, p)
            c.drawCircle(1288f, y, 14f, p)
            c.drawLine(1288f, y - 22f, 1288f, y + 22f, p)
        }
        return bmp
    }

    private fun drawTravelerSpriteSheet(): Bitmap {
        // 4 cols x 2 rows, each cell 128x128 -> 512x256
        val bmp = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        for (frame in 0 until 8) {
            val col = frame % 4
            val row = frame / 4
            val ox = col * 128f
            val oy = row * 128f
            val cycle = (frame / 8.0) * 2.0 * PI
            val bob = -kotlin.math.abs(sin(cycle)).toFloat() * 6f
            val stride = sin(cycle).toFloat() * 14f
            val cloakSway = cos(cycle).toFloat() * 7f
            val lanternSwing = sin(cycle + 0.6).toFloat() * 5f

            c.save()
            c.translate(ox + 64f, oy + 72f + bob)

            // Shadow
            p.color = Color.argb(90, 0, 0, 0)
            c.drawOval(RectF(-24f, 40f - bob, 24f, 50f - bob), p)

            // Back leg
            p.color = Color.parseColor("#1E293B")
            p.strokeWidth = 7f
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(-4f, 16f, -4f - stride, 42f, p)
            // Front leg
            p.color = Color.parseColor("#334155")
            c.drawLine(4f, 16f, 4f + stride, 42f, p)

            // Flowing traveler cloak
            val cloak = Path().apply {
                moveTo(-6f, -18f)
                lineTo(14f, -16f)
                lineTo(20f + cloakSway * 0.4f, 24f)
                lineTo(-26f - cloakSway, 26f)
                close()
            }
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#2563EB")
            c.drawPath(cloak, p)

            // Cloak trim
            p.color = Color.parseColor("#60A5FA")
            p.strokeWidth = 2.5f
            p.style = Paint.Style.STROKE
            c.drawLine(-24f - cloakSway, 24f, 19f + cloakSway * 0.4f, 22f, p)

            // Scarf tail
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#F59E0B")
            val scarf = Path().apply {
                moveTo(-4f, -18f)
                quadTo(-22f - cloakSway, -12f, -34f - cloakSway * 1.3f, -4f + sin(cycle).toFloat() * 4f)
                lineTo(-28f - cloakSway * 1.1f, 3f)
                quadTo(-16f, -6f, 8f, -14f)
                close()
            }
            c.drawPath(scarf, p)

            // Hood / head
            p.color = Color.parseColor("#1D4ED8")
            c.drawCircle(2f, -28f, 15f, p)
            p.color = Color.parseColor("#0F172A")
            c.drawCircle(7f, -27f, 9f, p)
            // Glowing eye
            p.color = Color.parseColor("#FDE68A")
            c.drawCircle(11f, -28f, 2.5f, p)

            // Lantern staff
            p.color = Color.parseColor("#94A3B8")
            p.strokeWidth = 3f
            p.style = Paint.Style.STROKE
            val lx = 32f + lanternSwing
            val ly = -4f
            c.drawLine(12f, -4f, lx, ly - 10f, p)
            c.drawLine(lx, ly - 10f, lx, ly + 2f, p)

            // Lantern aura
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(lx, ly + 8f, 28f, Color.argb(180, 251, 191, 36), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(lx, ly + 8f, 28f, p)
            p.shader = null

            // Lantern core
            p.color = Color.parseColor("#FEF08A")
            c.drawRoundRect(RectF(lx - 5f, ly + 2f, lx + 5f, ly + 14f), 3f, 3f, p)
            c.restore()
        }
        return bmp
    }

    private fun drawSakuraPortrait(): Bitmap {
        val w = 420
        val h = 520
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shoulders & coat
        p.shader = LinearGradient(0f, 280f, 0f, h.toFloat(), Color.parseColor("#312E81"), Color.parseColor("#1E1B4B"), Shader.TileMode.CLAMP)
        val body = Path().apply {
            moveTo(95f, 520f)
            cubicTo(100f, 340f, 155f, 300f, 210f, 300f)
            cubicTo(265f, 300f, 320f, 340f, 325f, 520f)
            close()
        }
        c.drawPath(body, p)
        p.shader = null

        // Collar highlight
        p.color = Color.parseColor("#E0E7FF")
        val collar = Path().apply {
            moveTo(175f, 302f); lineTo(210f, 368f); lineTo(245f, 302f); close()
        }
        c.drawPath(collar, p)

        // Neck & face
        p.color = Color.parseColor("#FDE2D2")
        c.drawRoundRect(RectF(188f, 240f, 232f, 315f), 14f, 14f, p)
        c.drawOval(RectF(146f, 112f, 274f, 266f), p)

        // Blush & calm anime eyes
        p.color = Color.argb(80, 244, 114, 182)
        c.drawOval(RectF(158f, 198f, 186f, 212f), p)
        c.drawOval(RectF(234f, 198f, 262f, 212f), p)

        p.color = Color.parseColor("#1E1B4B")
        c.drawOval(RectF(168f, 174f, 192f, 194f), p)
        c.drawOval(RectF(228f, 174f, 252f, 194f), p)
        p.color = Color.WHITE
        c.drawCircle(176f, 181f, 3.5f, p)
        c.drawCircle(236f, 181f, 3.5f, p)

        // Feathered curved bangs crown
        p.color = Color.parseColor("#1E1B4B")
        val bangsCrown = Path().apply {
            moveTo(136f, 182f)
            cubicTo(136f, 96f, 284f, 96f, 284f, 182f)
            cubicTo(272f, 166f, 260f, 152f, 248f, 148f)
            cubicTo(244f, 166f, 236f, 178f, 228f, 184f)
            cubicTo(226f, 166f, 220f, 154f, 212f, 147f)
            cubicTo(206f, 166f, 196f, 178f, 186f, 186f)
            cubicTo(186f, 167f, 180f, 154f, 172f, 148f)
            cubicTo(162f, 164f, 152f, 176f, 136f, 182f)
            close()
        }
        c.drawPath(bangsCrown, p)

        // Subtle violet sheen on bangs
        val bangSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 3f
            color = Color.argb(110, 167, 139, 250)
        }
        c.drawArc(RectF(152f, 114f, 268f, 166f), 200f, 140f, false, bangSheen)
        return bmp
    }

    private fun drawTaperedHairStrand(
        canvas: Canvas,
        rootX: Float,
        rootY: Float,
        ctrl1X: Float,
        ctrl1Y: Float,
        ctrl2X: Float,
        ctrl2Y: Float,
        tipX: Float,
        tipY: Float,
        rootHalfWidth: Float,
        midHalfWidth: Float,
        paint: Paint,
    ) {
        val strand = Path().apply {
            moveTo(rootX - rootHalfWidth, rootY)
            cubicTo(
                ctrl1X - midHalfWidth, ctrl1Y,
                ctrl2X - midHalfWidth * 0.55f, ctrl2Y,
                tipX, tipY,
            )
            cubicTo(
                ctrl2X + midHalfWidth * 0.55f, ctrl2Y - 4f,
                ctrl1X + midHalfWidth, ctrl1Y - 3f,
                rootX + rootHalfWidth, rootY,
            )
            close()
        }
        canvas.drawPath(strand, paint)
    }

    private fun drawSakuraHair(): Bitmap {
        val w = 360
        val h = 420
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Deep shadow back hair locks (anchored at scalp crown, fanning into feathered separated tips)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            60f, 20f, 300f, 405f,
            intArrayOf(Color.parseColor("#1B1742"), Color.parseColor("#312E81"), Color.parseColor("#4C1D95")),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        data class StrandSpec(
            val rx: Float, val ry: Float,
            val c1x: Float, val c1y: Float,
            val c2x: Float, val c2y: Float,
            val tx: Float, val ty: Float,
            val rw: Float, val mw: Float,
        )
        val backLocks = listOf(
            StrandSpec(118f, 36f, 78f, 128f, 52f, 232f, 44f, 342f, 16f, 15f),
            StrandSpec(138f, 32f, 108f, 142f, 88f, 260f, 82f, 378f, 18f, 17f),
            StrandSpec(164f, 30f, 146f, 148f, 134f, 272f, 126f, 398f, 20f, 19f),
            StrandSpec(192f, 30f, 188f, 150f, 186f, 276f, 176f, 404f, 20f, 19f),
            StrandSpec(216f, 34f, 226f, 146f, 238f, 264f, 232f, 390f, 19f, 18f),
            StrandSpec(236f, 40f, 262f, 138f, 286f, 248f, 286f, 366f, 18f, 16f),
            StrandSpec(248f, 48f, 288f, 136f, 322f, 224f, 336f, 322f, 14f, 12f),
        )
        for (s in backLocks) {
            drawTaperedHairStrand(c, s.rx, s.ry, s.c1x, s.c1y, s.c2x, s.c2y, s.tx, s.ty, s.rw, s.mw, p)
        }

        // 2. Mid-plane distinct flowing hair strands with individual S-curves and fine tapered tips
        p.shader = LinearGradient(
            70f, 24f, 315f, 390f,
            intArrayOf(Color.parseColor("#2E2A78"), Color.parseColor("#5B21B6"), Color.parseColor("#7C3AED")),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        val midStrands = listOf(
            StrandSpec(110f, 44f, 68f, 134f, 48f, 214f, 32f, 306f, 10f, 9f),
            StrandSpec(126f, 38f, 94f, 138f, 74f, 244f, 64f, 356f, 12f, 11f),
            StrandSpec(146f, 34f, 122f, 144f, 110f, 258f, 104f, 382f, 13f, 12f),
            StrandSpec(168f, 32f, 152f, 146f, 148f, 266f, 144f, 394f, 13f, 12f),
            StrandSpec(188f, 32f, 182f, 148f, 192f, 264f, 194f, 388f, 13f, 12f),
            StrandSpec(208f, 34f, 214f, 144f, 228f, 256f, 242f, 376f, 12f, 11f),
            StrandSpec(226f, 38f, 246f, 138f, 268f, 244f, 274f, 352f, 12f, 10f),
            StrandSpec(240f, 44f, 274f, 134f, 304f, 228f, 316f, 334f, 11f, 9f),
            StrandSpec(252f, 52f, 294f, 128f, 330f, 206f, 346f, 292f, 9f, 7.5f),
        )
        val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2.2f
            color = Color.argb(125, 22, 18, 56)
        }
        for (s in midStrands) {
            drawTaperedHairStrand(c, s.rx, s.ry, s.c1x, s.c1y, s.c2x, s.c2y, s.tx, s.ty, s.rw, s.mw, p)
            val crease = Path().apply {
                moveTo(s.rx + s.rw * 0.45f, s.ry + 6f)
                cubicTo(s.c1x + s.mw * 0.5f, s.c1y, s.c2x + s.mw * 0.3f, s.c2y, s.tx, s.ty)
            }
            c.drawPath(crease, creasePaint)
        }

        // 3. Soft silk highlights & individual fine wispy flyaway strands
        p.shader = null
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val highlightStrands = listOf(
            StrandSpec(120f, 54f, 88f, 140f, 70f, 232f, 56f, 334f, 2.5f, 0f),
            StrandSpec(150f, 48f, 128f, 146f, 120f, 250f, 112f, 366f, 3.0f, 0f),
            StrandSpec(184f, 44f, 180f, 148f, 186f, 252f, 182f, 372f, 3.2f, 0f),
            StrandSpec(216f, 48f, 230f, 142f, 248f, 242f, 258f, 348f, 3.0f, 0f),
            StrandSpec(238f, 56f, 270f, 138f, 298f, 222f, 314f, 316f, 2.6f, 0f),
        )
        for ((i, s) in highlightStrands.withIndex()) {
            highlightPaint.strokeWidth = s.rw
            highlightPaint.color = if (i % 2 == 0) Color.argb(115, 196, 181, 253) else Color.argb(95, 233, 213, 255)
            val curve = Path().apply {
                moveTo(s.rx, s.ry)
                cubicTo(s.c1x, s.c1y, s.c2x, s.c2y, s.tx, s.ty)
            }
            c.drawPath(curve, highlightPaint)
        }

        // Fine edge flyaways that flutter freely in wind
        val wispPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(150, 139, 92, 246)
        }
        val flyaways = listOf(
            StrandSpec(102f, 58f, 54f, 136f, 34f, 210f, 18f, 282f, 2.4f, 0f),
            StrandSpec(132f, 70f, 84f, 174f, 60f, 286f, 46f, 386f, 2.0f, 0f),
            StrandSpec(172f, 64f, 158f, 186f, 154f, 304f, 142f, 408f, 2.2f, 0f),
            StrandSpec(228f, 64f, 260f, 166f, 292f, 272f, 304f, 378f, 2.2f, 0f),
            StrandSpec(252f, 62f, 302f, 132f, 340f, 196f, 354f, 268f, 2.0f, 0f),
            StrandSpec(244f, 78f, 296f, 164f, 332f, 242f, 348f, 318f, 1.8f, 0f),
        )
        for (s in flyaways) {
            wispPaint.strokeWidth = s.rw
            val wisp = Path().apply {
                moveTo(s.rx, s.ry)
                cubicTo(s.c1x, s.c1y, s.c2x, s.c2y, s.tx, s.ty)
            }
            c.drawPath(wisp, wispPaint)
        }

        // Soft upper-crown specular highlight halo broken into individual locks
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 4.5f
            color = Color.argb(105, 221, 214, 254)
        }
        c.drawArc(RectF(108f, 64f, 260f, 166f), 206f, 34f, false, haloPaint)
        c.drawArc(RectF(108f, 64f, 260f, 166f), 252f, 32f, false, haloPaint)
        c.drawArc(RectF(108f, 64f, 260f, 166f), 296f, 36f, false, haloPaint)

        return bmp
    }

    private fun drawSakuraScarf(): Bitmap {
        val w = 440
        val h = 180
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.parseColor("#F43F5E"), Color.parseColor("#FB7185"), Shader.TileMode.CLAMP)
        val ribbon = Path().apply {
            moveTo(18f, 52f)
            cubicTo(130f, 20f, 260f, 110f, 424f, 58f)
            lineTo(412f, 128f)
            cubicTo(255f, 168f, 130f, 88f, 18f, 112f)
            close()
        }
        c.drawPath(ribbon, p)
        p.shader = null
        p.color = Color.argb(120, 255, 228, 230)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        val stripe = Path().apply {
            moveTo(22f, 84f)
            cubicTo(132f, 54f, 258f, 138f, 418f, 94f)
        }
        c.drawPath(stripe, p)
        return bmp
    }

    private fun drawStageHorizon(): Bitmap {
        val w = 1400
        val h = 520
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Stage floor gradient
        p.shader = LinearGradient(0f, 280f, 0f, h.toFloat(), Color.parseColor("#1E1B4B"), Color.parseColor("#090D16"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 280f, w.toFloat(), h.toFloat(), p)

        // Horizon neon glow
        p.shader = RadialGradient(w / 2f, 285f, 520f, Color.argb(135, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawOval(RectF(140f, 140f, 1260f, 430f), p)
        p.shader = null

        // Perspective grid lines
        p.color = Color.argb(80, 56, 189, 248)
        p.strokeWidth = 2.5f
        for (i in 0..8) {
            val y = 285f + i * i * 3.6f
            c.drawLine(0f, y, w.toFloat(), y, p)
        }
        for (i in -8..8) {
            c.drawLine(w / 2f + i * 38f, 285f, w / 2f + i * 165f, h.toFloat(), p)
        }

        // Horizon accent line
        p.color = Color.parseColor("#38BDF8")
        p.strokeWidth = 4f
        c.drawLine(0f, 285f, w.toFloat(), 285f, p)
        return bmp
    }

    private fun drawFallbackTile(): Bitmap {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
        c.drawRoundRect(RectF(16f, 16f, 240f, 240f), 28f, 28f, p)
        return bmp
    }
}
