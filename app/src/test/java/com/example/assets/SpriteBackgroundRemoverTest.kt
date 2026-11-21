package com.example.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteBackgroundRemoverTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun alphaOf(pixel: Int): Int = (pixel ushr 24) and 0xFF

    @Test
    fun `smart edge removes exterior white background while preserving interior white eye`() {
        val w = 24
        val h = 24
        val white = argb(255, 255, 255, 255)
        val blueBody = argb(255, 35, 95, 210)
        val pixels = IntArray(w * h) { white }

        // Draw solid blue character body in center [5..18, 5..18]
        for (y in 5..18) {
            for (x in 5..18) {
                pixels[y * w + x] = blueBody
            }
        }
        // Place a white eye strictly inside the blue character [9..11, 9..11]
        for (y in 9..11) {
            for (x in 9..11) {
                pixels[y * w + x] = white
            }
        }

        val res = SpriteBackgroundRemover.removeBackgroundPixels(
            sourcePixels = pixels,
            width = w,
            height = h,
            options = BackgroundRemovalOptions(
                mode = BackgroundRemovalMode.SMART_EDGE,
                targetColor = null, // Auto-detect border white
                tolerance = 25,
                feather = 8,
            ),
        )

        assertEquals(white, res.detectedKeyColor)
        assertEquals("Corner background pixel must be fully transparent", 0, alphaOf(res.pixels[0]))
        assertEquals("Character body pixel must remain opaque", 255, alphaOf(res.pixels[6 * w + 6]))
        assertEquals(
            "Interior white eye must be preserved by Smart Edge flood-fill",
            255,
            alphaOf(res.pixels[10 * w + 10]),
        )
        assertTrue("Removed pixel count should cover exterior backdrop", res.removedPixelCount > 300)
    }

    @Test
    fun `color key removes green screen everywhere including enclosed holes and despills edges`() {
        val w = 20
        val h = 20
        val greenScreen = argb(255, 0, 255, 0)
        val redSprite = argb(255, 220, 40, 40)
        val greenFringe = argb(255, 95, 200, 30) // Semi-blended edge pixel
        val pixels = IntArray(w * h) { greenScreen }

        // Draw a red ring with an enclosed green hole in the middle
        for (y in 4..15) {
            for (x in 4..15) {
                pixels[y * w + x] = redSprite
            }
        }
        for (y in 8..11) {
            for (x in 8..11) {
                pixels[y * w + x] = greenScreen
            }
        }
        // Add a green-fringed pixel on the outer edge
        pixels[4 * w + 10] = greenFringe

        val res = SpriteBackgroundRemover.removeBackgroundPixels(
            sourcePixels = pixels,
            width = w,
            height = h,
            options = BackgroundRemovalOptions(
                mode = BackgroundRemovalMode.COLOR_KEY,
                targetColor = greenScreen,
                tolerance = 28,
                feather = 30,
                despill = 0.8f,
            ),
        )

        assertEquals("Outer green screen must be removed", 0, alphaOf(res.pixels[0]))
        assertEquals("Enclosed green hole must also be removed in Color Key mode", 0, alphaOf(res.pixels[9 * w + 9]))
        assertEquals("Red sprite body must stay opaque", 255, alphaOf(res.pixels[6 * w + 6]))

        val fringeOut = res.pixels[4 * w + 10]
        val fringeAlpha = alphaOf(fringeOut)
        if (fringeAlpha in 1..250) {
            val outGreen = (fringeOut ushr 8) and 0xFF
            assertTrue("Despill should reduce green channel bleed on fringe pixel", outGreen < 200)
        }
    }

    @Test
    fun `white bg clears bright neutral paper while keeping dark line art and saturated colors`() {
        val w = 16
        val h = 16
        val paper = argb(255, 248, 247, 245)
        val ink = argb(255, 22, 24, 30)
        val yellowAccent = argb(255, 250, 220, 30)
        val pixels = IntArray(w * h) { paper }
        pixels[8 * w + 6] = ink
        pixels[8 * w + 10] = yellowAccent

        val res = SpriteBackgroundRemover.removeBackgroundPixels(
            sourcePixels = pixels,
            width = w,
            height = h,
            options = BackgroundRemovalOptions(
                mode = BackgroundRemovalMode.WHITE_BG,
                tolerance = 25,
                feather = 10,
            ),
        )

        assertTrue("Paper background should become transparent", alphaOf(res.pixels[0]) < 20)
        assertEquals("Dark ink linework must remain fully opaque", 255, alphaOf(res.pixels[8 * w + 6]))
        assertEquals("Saturated bright yellow must remain fully opaque", 255, alphaOf(res.pixels[8 * w + 10]))
    }

    @Test
    fun `black bg converts dark vfx backdrop to smooth alpha while preserving bright glow`() {
        val w = 12
        val h = 12
        val black = argb(255, 4, 4, 6)
        val midGlow = argb(255, 110, 50, 10)
        val coreFire = argb(255, 255, 210, 90)
        val pixels = IntArray(w * h) { black }
        pixels[5 * w + 5] = midGlow
        pixels[6 * w + 6] = coreFire

        val res = SpriteBackgroundRemover.removeBackgroundPixels(
            sourcePixels = pixels,
            width = w,
            height = h,
            options = BackgroundRemovalOptions(
                mode = BackgroundRemovalMode.BLACK_BG,
                tolerance = 18,
                feather = 24,
                despill = 0.6f,
            ),
        )

        assertEquals("Near-black backdrop must become fully transparent", 0, alphaOf(res.pixels[0]))
        val midAlpha = alphaOf(res.pixels[5 * w + 5])
        assertTrue("Mid-luminance glow should have soft intermediate alpha (got $midAlpha)", midAlpha in 20..240)
        assertEquals("Bright VFX core must stay fully opaque", 255, alphaOf(res.pixels[6 * w + 6]))
    }

    @Test
    fun `edge shrink erodes outer foreground rim by requested pixel radius`() {
        val w = 16
        val h = 16
        val white = argb(255, 255, 255, 255)
        val solid = argb(255, 30, 30, 30)
        val pixels = IntArray(w * h) { white }
        for (y in 4..11) {
            for (x in 4..11) {
                pixels[y * w + x] = solid
            }
        }

        val noShrink = SpriteBackgroundRemover.removeBackgroundPixels(
            pixels, w, h,
            BackgroundRemovalOptions(mode = BackgroundRemovalMode.SMART_EDGE, tolerance = 20, feather = 0, edgeShrinkPx = 0),
        )
        val shrunk = SpriteBackgroundRemover.removeBackgroundPixels(
            pixels, w, h,
            BackgroundRemovalOptions(mode = BackgroundRemovalMode.SMART_EDGE, tolerance = 20, feather = 0, edgeShrinkPx = 1),
        )

        assertEquals(255, alphaOf(noShrink.pixels[4 * w + 8]))
        assertEquals("1px edge shrink should erode outermost foreground pixel", 0, alphaOf(shrunk.pixels[4 * w + 8]))
        assertEquals("Interior pixel should stay intact after 1px shrink", 255, alphaOf(shrunk.pixels[6 * w + 8]))
    }
}
