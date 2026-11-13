package com.cactuslauncher

import com.cactuslauncher.game.ProfileStore
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

/** One increment per launcher process; reopening the profile popup never changes it. */
internal fun recordLauncherStart(file: Path = ProfileStore.defaultFile().resolveSibling("launcher-starts")): Long {
    Files.createDirectories(file.parent)
    return FileChannel.open(file, CREATE, READ, WRITE).use { channel ->
        channel.lock().use {
            val bytes = ByteBuffer.allocate(32)
            channel.read(bytes)
            bytes.flip()
            val previous = Charsets.UTF_8.decode(bytes).toString().trim().toLongOrNull()?.coerceAtLeast(0) ?: 0L
            val next = if (previous == Long.MAX_VALUE) 1L else previous + 1L
            channel.truncate(0)
            channel.position(0)
            val output = ByteBuffer.wrap(next.toString().toByteArray(Charsets.UTF_8))
            while (output.hasRemaining()) channel.write(output)
            channel.force(true)
            next
        }
    }
}
