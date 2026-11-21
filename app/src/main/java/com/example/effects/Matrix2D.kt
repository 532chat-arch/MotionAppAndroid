package com.example.effects

import android.graphics.Matrix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Lightweight, mutable, zero-allocation 2D affine transformation matrix.
 *
 * Represents the 3x3 affine matrix:
 * ```
 * | m00  m01  m02 |   | a   c   tx |
 * | m10  m11  m12 | = | b   d   ty |
 * |  0    0    1  |   | 0   0    1 |
 * ```
 * Pure Kotlin math allows deterministic JVM unit testing without Android framework stubs
 * while converting directly to [android.graphics.Matrix] for GPU-accelerated Canvas drawing.
 */
class Matrix2D(
    var m00: Double = 1.0,
    var m01: Double = 0.0,
    var m02: Double = 0.0,
    var m10: Double = 0.0,
    var m11: Double = 1.0,
    var m12: Double = 0.0,
) {
    fun reset() {
        m00 = 1.0
        m01 = 0.0
        m02 = 0.0
        m10 = 0.0
        m11 = 1.0
        m12 = 0.0
    }

    fun set(other: Matrix2D) {
        m00 = other.m00
        m01 = other.m01
        m02 = other.m02
        m10 = other.m10
        m11 = other.m11
        m12 = other.m12
    }

    fun isIdentity(epsilon: Double = 1e-6): Boolean =
        abs(m00 - 1.0) <= epsilon &&
            abs(m01) <= epsilon &&
            abs(m02) <= epsilon &&
            abs(m10) <= epsilon &&
            abs(m11 - 1.0) <= epsilon &&
            abs(m12) <= epsilon

    /** Post-multiplies this matrix by a translation: M' = T(tx, ty) * M. */
    fun postTranslate(tx: Double, ty: Double) {
        m02 += tx
        m12 += ty
    }

    /** Pre-multiplies this matrix by a translation: M' = M * T(tx, ty). */
    fun preTranslate(tx: Double, ty: Double) {
        m02 += m00 * tx + m01 * ty
        m12 += m10 * tx + m11 * ty
    }

    /** Post-multiplies this matrix by a scale around optional pivot (px, py). */
    fun postScale(sx: Double, sy: Double, px: Double = 0.0, py: Double = 0.0) {
        if (px != 0.0 || py != 0.0) {
            postTranslate(-px, -py)
        }
        m00 *= sx
        m01 *= sx
        m02 *= sx
        m10 *= sy
        m11 *= sy
        m12 *= sy
        if (px != 0.0 || py != 0.0) {
            postTranslate(px, py)
        }
    }

    /** Pre-multiplies this matrix by a scale: M' = M * S(sx, sy). */
    fun preScale(sx: Double, sy: Double) {
        m00 *= sx
        m10 *= sx
        m01 *= sy
        m11 *= sy
    }

    /** Post-multiplies this matrix by a rotation in degrees around optional pivot (px, py). */
    fun postRotate(degrees: Double, px: Double = 0.0, py: Double = 0.0) {
        if (abs(degrees) <= 1e-9) return
        if (px != 0.0 || py != 0.0) {
            postTranslate(-px, -py)
        }
        val rad = degrees * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val n00 = c * m00 - s * m10
        val n01 = c * m01 - s * m11
        val n02 = c * m02 - s * m12
        val n10 = s * m00 + c * m10
        val n11 = s * m01 + c * m11
        val n12 = s * m02 + c * m12
        m00 = n00; m01 = n01; m02 = n02
        m10 = n10; m11 = n11; m12 = n12
        if (px != 0.0 || py != 0.0) {
            postTranslate(px, py)
        }
    }

    /** Pre-multiplies this matrix by a rotation in degrees: M' = M * R(degrees). */
    fun preRotate(degrees: Double) {
        if (abs(degrees) <= 1e-9) return
        val rad = degrees * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val n00 = m00 * c + m01 * s
        val n01 = -m00 * s + m01 * c
        val n10 = m10 * c + m11 * s
        val n11 = -m10 * s + m11 * c
        m00 = n00; m01 = n01
        m10 = n10; m11 = n11
    }

    /** Pre-multiplies this matrix by a 2D shear in degrees: M' = M * Skew(kx, ky). */
    fun preSkew(skewXDeg: Double, skewYDeg: Double) {
        if (abs(skewXDeg) <= 1e-5 && abs(skewYDeg) <= 1e-5) return
        val kx = tan(skewXDeg * PI / 180.0).coerceIn(-2.0, 2.0)
        val ky = tan(skewYDeg * PI / 180.0).coerceIn(-2.0, 2.0)
        val n00 = m00 + m01 * ky
        val n01 = m00 * kx + m01
        val n10 = m10 + m11 * ky
        val n11 = m10 * kx + m11
        m00 = n00; m01 = n01
        m10 = n10; m11 = n11
    }

    /** Post-multiplies this matrix by [other]: this = other * this. */
    fun postConcat(other: Matrix2D) {
        val n00 = other.m00 * m00 + other.m01 * m10
        val n01 = other.m00 * m01 + other.m01 * m11
        val n02 = other.m00 * m02 + other.m01 * m12 + other.m02
        val n10 = other.m10 * m00 + other.m11 * m10
        val n11 = other.m10 * m01 + other.m11 * m11
        val n12 = other.m10 * m02 + other.m11 * m12 + other.m12
        m00 = n00; m01 = n01; m02 = n02
        m10 = n10; m11 = n11; m12 = n12
    }

    /** Pre-multiplies this matrix by [other]: this = this * other. */
    fun preConcat(other: Matrix2D) {
        val n00 = m00 * other.m00 + m01 * other.m10
        val n01 = m00 * other.m01 + m01 * other.m11
        val n02 = m00 * other.m02 + m01 * other.m12 + m02
        val n10 = m10 * other.m00 + m11 * other.m10
        val n11 = m10 * other.m01 + m11 * other.m11
        val n12 = m10 * other.m02 + m11 * other.m12 + m12
        m00 = n00; m01 = n01; m02 = n02
        m10 = n10; m11 = n11; m12 = n12
    }

    /**
     * Composes a standard 2D layer transform in Canvas pre-concatenation order:
     * `T(x + anchorX, y + anchorY) * R(rotationDeg) * Skew(skewXDeg, skewYDeg) * S(sx, sy) * T(-anchorX, -anchorY)`
     */
    fun composeFromTransform(
        tx: Double,
        ty: Double,
        scaleX: Double = 1.0,
        scaleY: Double = 1.0,
        rotationDeg: Double = 0.0,
        skewXDeg: Double = 0.0,
        skewYDeg: Double = 0.0,
        flipX: Boolean = false,
        flipY: Boolean = false,
        anchorOffsetX: Double = 0.0,
        anchorOffsetY: Double = 0.0,
    ) {
        reset()
        preTranslate(tx + anchorOffsetX, ty + anchorOffsetY)
        if (abs(rotationDeg) > 1e-5) preRotate(rotationDeg)
        if (abs(skewXDeg) > 1e-5 || abs(skewYDeg) > 1e-5) preSkew(skewXDeg, skewYDeg)
        val sx = scaleX * if (flipX) -1.0 else 1.0
        val sy = scaleY * if (flipY) -1.0 else 1.0
        if (abs(sx - 1.0) > 1e-6 || abs(sy - 1.0) > 1e-6) preScale(sx, sy)
        if (abs(anchorOffsetX) > 1e-5 || abs(anchorOffsetY) > 1e-5) {
            preTranslate(-anchorOffsetX, -anchorOffsetY)
        }
    }

    /** Maps a 2D point (x, y) into [outXY] (length >= 2) with zero allocations. */
    fun mapPoint(x: Double, y: Double, outXY: DoubleArray) {
        outXY[0] = m00 * x + m01 * y + m02
        outXY[1] = m10 * x + m11 * y + m12
    }

    /** Copies affine coefficients into a 9-element row-major float array for Android [Matrix]. */
    fun toFloatArray9(out: FloatArray) {
        out[0] = m00.toFloat()
        out[1] = m01.toFloat()
        out[2] = m02.toFloat()
        out[3] = m10.toFloat()
        out[4] = m11.toFloat()
        out[5] = m12.toFloat()
        out[6] = 0f
        out[7] = 0f
        out[8] = 1f
    }

    /** Writes this affine matrix into an Android [Matrix] without allocating a new Matrix object. */
    fun toAndroidMatrix(out: Matrix, scratch9: FloatArray = FloatArray(9)) {
        toFloatArray9(scratch9)
        out.setValues(scratch9)
    }
}
