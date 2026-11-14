// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 R. Kravcov

package com.avdesign.mfd24.geo

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Which time zone a position lies in, from the packed `tz_v1.tzg` asset that
 * `tools/tz/build_tz.py` cuts out of the IANA zone boundaries.
 *
 * The grid answers with a zone *name* and nothing else. Offsets, summer time and their history are
 * the platform's business — its tzdata is kept current by system updates, where anything copied
 * into this APK would be frozen at the day it was built — so the asset is a lookup table and the
 * arithmetic stays in `java.time`.
 *
 * ### Layout, little-endian
 * ```
 *   u32 magic "TZG1"   u16 version   u16 cells per degree   u16 cols   u16 rows   u16 zones
 *   u16 reserved
 *   u32 table bytes, then per zone:
 *       u8 name count, then per name: u8 length + ASCII      (newest name first)
 *       i32 standard offset in seconds                          (the last resort)
 *   u32 × (rows + 1)   index of each row's first run
 *   per run: u16 first column, u16 zone
 * ```
 * Row 0 is the strip just south of 90° N; column 0 starts at 180° W. Each row is a sequence of
 * runs sorted by column, so a lookup is one binary search over a few dozen entries.
 *
 * Pure and Android-free, so it is tested against the shipped asset itself.
 */
class TzGrid private constructor(
    private val buffer: ByteBuffer,
    private val cellsPerDegree: Int,
    private val cols: Int,
    private val rows: Int,
    private val zoneNames: Array<Array<String>>,
    private val standardOffsets: IntArray,
    private val indexOffset: Int,
    private val runsOffset: Int,
) {

    val zoneCount: Int
        get() = zoneNames.size

    /** The zone index at a position. Any latitude and longitude is accepted; both are clamped or wrapped. */
    fun zoneAt(latDeg: Double, lonDeg: Double): Int {
        var row = Math.floor((90.0 - latDeg) * cellsPerDegree).toInt()
        if (row < 0) row = 0
        if (row >= rows) row = rows - 1
        var col = Math.floor((lonDeg + 180.0) * cellsPerDegree).toInt() % cols
        if (col < 0) col += cols

        var low = buffer.getInt(indexOffset + row * 4)
        var high = buffer.getInt(indexOffset + (row + 1) * 4) - 1
        // The last run starting at or before the column. Every row's first run starts at column 0.
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (startColumn(mid) <= col) low = mid else high = mid - 1
        }
        return zoneOfRun(low)
    }

    /** The zone's names, newest first, then older or equivalent names a dated device may know instead. */
    fun names(zone: Int): Array<String> = zoneNames[zone]

    /** The zone whose newest name is [name], or -1. A scan of a few hundred entries, off the render thread. */
    fun indexOf(name: String): Int {
        for (z in zoneNames.indices) if (zoneNames[z][0] == name) return z
        return -1
    }

    /** Standard (non-summer) offset at build time, for a device that knows none of [names]. */
    fun standardOffsetSeconds(zone: Int): Int = standardOffsets[zone]

    private fun startColumn(run: Int): Int =
        buffer.getShort(runsOffset + run * RUN_BYTES).toInt() and 0xFFFF

    private fun zoneOfRun(run: Int): Int =
        buffer.getShort(runsOffset + run * RUN_BYTES + 2).toInt() and 0xFFFF

    companion object {
        const val ASSET_NAME: String = "tz_v1.tzg"

        private const val MAGIC = 0x31475A54
        private const val VERSION = 1
        private const val HEADER_BYTES = 16
        private const val RUN_BYTES = 4

        /** Validates and wraps the asset's bytes, or returns null if they are not a grid this reader knows. */
        fun fromBytes(bytes: ByteArray): TzGrid? {
            if (bytes.size < HEADER_BYTES + 4) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.getInt(0) != MAGIC) return null
            if (buffer.getShort(4).toInt() and 0xFFFF != VERSION) return null
            val cellsPerDegree = buffer.getShort(6).toInt() and 0xFFFF
            val cols = buffer.getShort(8).toInt() and 0xFFFF
            val rows = buffer.getShort(10).toInt() and 0xFFFF
            val zones = buffer.getShort(12).toInt() and 0xFFFF
            if (cellsPerDegree == 0 || cols != 360 * cellsPerDegree || rows != 180 * cellsPerDegree) {
                return null
            }

            val tableBytes = buffer.getInt(HEADER_BYTES)
            var p = HEADER_BYTES + 4
            val tableEnd = p + tableBytes
            if (tableBytes < 0 || tableEnd > bytes.size) return null
            val names = Array(zones) { emptyArray<String>() }
            val offsets = IntArray(zones)
            for (z in 0 until zones) {
                if (p >= tableEnd) return null
                val count = bytes[p++].toInt() and 0xFF
                names[z] = Array(count) {
                    val length = bytes[p].toInt() and 0xFF
                    val name = String(bytes, p + 1, length, Charsets.US_ASCII)
                    p += 1 + length
                    name
                }
                offsets[z] = buffer.getInt(p)
                p += 4
            }
            if (p != tableEnd) return null

            val indexOffset = tableEnd
            val runsOffset = indexOffset + (rows + 1) * 4
            if (runsOffset > bytes.size) return null
            val runCount = buffer.getInt(indexOffset + rows * 4)
            if (runCount < rows || runsOffset + runCount * RUN_BYTES != bytes.size) return null
            return TzGrid(buffer, cellsPerDegree, cols, rows, names, offsets, indexOffset, runsOffset)
        }
    }
}
