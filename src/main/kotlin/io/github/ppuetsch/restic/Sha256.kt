package io.github.ppuetsch.restic

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest

private const val HEX_RADIX: Int = 16
private const val BYTE_MASK: Int = 0xff

/** SHA-256 checksums as lowercase hex, the form restic's `SHA256SUMS` and the binary artifacts use. */
internal object Sha256 {
    fun of(file: Path): String = Files.newInputStream(file).use { input -> copyHashing(input, OutputStream.nullOutputStream()) }

    /** Copies [source] to [target] and returns the checksum of what was copied. */
    fun copyHashing(
        source: InputStream,
        target: OutputStream,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(source, digest).use { hashing -> hashing.transferTo(target) }
        return digest.digest().joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
    }
}
