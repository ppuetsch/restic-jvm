package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TypedCatTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val repository by lazy { RepositoryFixture(temporaryDirectory).also { it.backup() } }

    @Test
    fun `config is typed`() {
        val config = repository.restic.catConfig()

        assertEquals(2, config.version)
        assertEquals(64, config.id.length)
        assertTrue(config.chunkerPolynomial.isNotEmpty())
    }

    @Test
    fun `master key is typed`() {
        val key = repository.restic.catMasterKey()

        assertTrue(key.encryptionKey.isNotEmpty() && key.macKey.isNotEmpty() && key.macRandom.isNotEmpty())
    }

    @Test
    fun `snapshot is typed and carries its id`() {
        val listed = repository.restic.snapshots().single()

        val snapshot = repository.restic.catSnapshot(listed.id)

        assertEquals(listed.id, snapshot.id)
        assertEquals(listed.tree, snapshot.tree)
        assertEquals(listed.paths, snapshot.paths)
    }

    @Test
    fun `key is typed`() {
        val keyInfo = repository.restic.listKeys().single()

        val key = repository.restic.catKey(keyInfo.id)

        assertEquals("scrypt", key.kdf)
        assertTrue(key.n > 0 && key.r > 0 && key.p > 0)
        assertTrue(key.salt.isNotEmpty())
    }

    @Test
    fun `tree is typed`() {
        val snapshot = repository.restic.snapshots().single()

        val tree = repository.restic.catTree(snapshot.id)

        assertTrue(tree.nodes.isNotEmpty())
        assertTrue(tree.nodes.all { it.name.isNotEmpty() && it.type.isNotEmpty() })
    }

    @Test
    fun `index is typed`() {
        val indexId = repository.restic.list(ListObjectType.INDEX).first()

        val index = repository.restic.catIndex(indexId)

        assertTrue(index.packs.isNotEmpty())
        assertTrue(index.packs.all { pack -> pack.id.isNotEmpty() && pack.blobs.isNotEmpty() })
        assertFalse(index.packs.flatMap { it.blobs }.any { it.length <= 0 })
    }
}
