package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

private val SAMPLE_LOCK_CONTENT =
    LockContent(Instant.EPOCH, exclusive = true, hostname = "host", username = "user", pid = 42, uid = 1000, gid = 1000)

class RepositoryObjectsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixture() {
        fixture = RepositoryFixture(temporaryDirectory)
    }

    @Test
    fun `reading any property fills all of them with a single read`() {
        val reads = AtomicInteger()
        val lock = Lock("abc", lazy { reads.incrementAndGet().let { SAMPLE_LOCK_CONTENT } })

        assertEquals(0, reads.get(), "listing alone reads nothing")
        val description = "${lock.id} ${lock.pid} ${lock.hostname} ${lock.username} ${lock.time} ${lock.exclusive} ${lock.uid} ${lock.gid}"

        assertEquals("abc 42 host user 1970-01-01T00:00:00Z true 1000 1000", description)
        assertEquals(1, reads.get())
    }

    @Test
    fun `a failed read is retried on the next access`() {
        val reads = AtomicInteger()
        val lock =
            Lock(
                "abc",
                lazy {
                    check(reads.incrementAndGet() > 1) { "lock vanished" }
                    SAMPLE_LOCK_CONTENT
                },
            )

        assertThrows<IllegalStateException> { lock.pid }
        assertEquals(42, lock.pid)
        assertEquals(2, reads.get())
    }

    @Test
    fun `objects are equal by type and id`() {
        assertEquals(Pack("a"), Pack("a"))
        assertEquals(Pack("a").hashCode(), Pack("a").hashCode())
        assertNotEquals(Pack("a"), Pack("b"))
        assertFalse(Pack("a").equals(Blob("a", "data")), "same id, different type")
        assertEquals("Pack(a)", Pack("a").toString())
    }

    @Test
    fun `listed locks read their content on first access`() {
        HeldLock(restic).use {
            val lock = restic.list(ListObjectType.LOCKS).single()

            assertEquals(64, lock.id.length)
            assertNotEquals(ProcessHandle.current().pid(), lock.pid, "the lock belongs to the restic process")
            assertTrue(lock.pid > 0 && lock.hostname.isNotEmpty() && lock.username.isNotEmpty())
            assertFalse(lock.exclusive, "backup takes a shared lock")
            assertNotNull(lock.time)
            assertEquals(restic.cat().lock(lock.id).pid, lock.pid)
            assertEquals(restic.cat().lock(lock.id), lock)
        }
        assertTrue(restic.list(ListObjectType.LOCKS).isEmpty(), "the finished backup released its lock")
    }

    @Test
    fun `listed keys read their content on first access`() {
        val key = restic.list(ListObjectType.KEYS).single()

        assertEquals(64, key.id.length)
        assertTrue(
            key.id.startsWith(
                restic
                    .keys()
                    .list()
                    .single()
                    .id,
            ),
        )
        assertEquals("scrypt", key.kdf)
        assertTrue(key.n > 0 && key.r > 0 && key.p > 0)
        assertTrue(key.salt.isNotEmpty() && key.data.isNotEmpty() && key.username.isNotEmpty() && key.hostname.isNotEmpty())
        assertNotNull(key.created)
    }

    @Test
    fun `listed index files read their content on first access`() {
        fixture.backup()

        val index = restic.list(ListObjectType.INDEX).first()

        assertTrue(index.packs.isNotEmpty())
        assertTrue(index.supersedes.isEmpty())
        assertEquals(restic.list(ListObjectType.PACKS).map { it.id }.toSet(), index.packs.map { it.id }.toSet())
    }

    @Test
    fun `snapshots, packs and blobs are listed`() {
        val snapshotId = fixture.backup().snapshotId

        assertEquals(listOf(snapshotId), restic.list(ListObjectType.SNAPSHOTS).map { it.id })
        assertEquals(restic.snapshots(), restic.list(ListObjectType.SNAPSHOTS))
        assertTrue(restic.list(ListObjectType.PACKS).all { it.id.length == 64 })
        val blobs = restic.list(ListObjectType.BLOBS)
        assertTrue(blobs.any { it.type == "data" && it.id.length == 64 })
        assertTrue(blobs.any { it.type == "tree" })
    }

    @Test
    fun `find results read their snapshot on first access`() {
        fixture.backup()
        val snapshot = restic.snapshots().single()

        val match = restic.find(listOf("b.txt")).single()
        val found = restic.findObjects(FindObjectKind.TREE, listOf(snapshot.tree)).single()

        assertEquals(snapshot, match.snapshot)
        assertEquals(snapshot, found.snapshot)
    }

    @Test
    fun `list types name themselves`() {
        assertEquals("LOCKS", ListObjectType.LOCKS.toString())
    }
}
