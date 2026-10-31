package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SnapshotsLsFindTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixture() {
        fixture = RepositoryFixture(temporaryDirectory)
    }

    @Test
    fun `empty repository has no snapshots`() {
        assertEquals(emptyList<Snapshot>(), restic.snapshots())
        assertEquals(emptyList<SnapshotGroup>(), restic.snapshotGroups())
    }

    @Test
    fun `snapshots expose id tags host paths and summary`() {
        val summary = fixture.backup { tag("one", "two") }
        val snapshot = restic.snapshots().single()
        assertEquals(summary.snapshotId, snapshot.id)
        assertEquals(snapshot.id.take(8), snapshot.shortId)
        assertEquals(listOf("one", "two"), snapshot.tags)
        assertEquals(listOf(fixture.source.toString()), snapshot.paths)
        assertTrue(snapshot.hostname.isNotEmpty())
        assertNotNull(snapshot.time)
        assertEquals(3, snapshot.summary?.filesNew)
        assertTrue(snapshot.tree.isNotEmpty())
    }

    @Test
    fun `snapshots can be filtered by tag host and latest`() {
        fixture.backup {
            tag("daily")
            host("alpha")
        }
        fixture.backup {
            tag("weekly")
            host("beta")
        }
        fixture.backup {
            tag("weekly")
            host("beta")
        }
        assertEquals(3, restic.snapshots().size)
        assertEquals(1, restic.snapshots { tag("daily") }.size)
        assertEquals(2, restic.snapshots { host("beta") }.size)
        assertEquals(0, restic.snapshots { tag("weekly", "daily") }.size)
        assertEquals(
            3,
            restic.snapshots {
                tag("weekly")
                tag("daily")
            }.size,
        )
        assertEquals(
            1,
            restic.snapshots {
                host("beta")
                latest(1)
            }.size,
        )
        assertEquals(3, restic.snapshots { path(fixture.source.toString()) }.size)
        assertEquals(0, restic.snapshots { path("/gibt/es/nicht") }.size)
    }

    @Test
    fun `snapshots restricted to one id`() {
        val first = fixture.backup().snapshotId.orEmpty()
        fixture.backup()
        assertEquals(listOf(first), restic.snapshots { snapshotId(first) }.map { it.id })
    }

    @Test
    fun `groupBy flattens for snapshots and structures for snapshotGroups`() {
        fixture.backup { host("alpha") }
        fixture.backup { host("beta") }
        assertEquals(2, restic.snapshots { groupBy("host") }.size)
        val groups = restic.snapshotGroups(SnapshotsOptions.builder().groupBy("host").build())
        assertEquals(setOf("alpha", "beta"), groups.map { it.key.hostname }.toSet())
        assertTrue(groups.all { it.snapshots.size == 1 })
        assertEquals(2, restic.snapshotGroups().size)
    }

    @Test
    fun `compact flag is accepted`() {
        fixture.backup()
        assertEquals(1, restic.snapshots { compact() }.size)
    }

    @Test
    fun `ls recursive lists every entry`() {
        fixture.backup()
        val result = restic.listNodes(Restic.LATEST, LsOptions.builder().recursive().build())
        val files = result.nodes.filter { it.isFile() }.map { it.name }.toSet()
        assertEquals(setOf("a.txt", "debug.log", "b.txt"), files)
        assertTrue(result.nodes.any { it.isDirectory() && it.name == "sub" })
        assertEquals(5L, result.nodes.first { it.name == "a.txt" }.size)
        assertNotNull(result.nodes.first { it.name == "a.txt" }.modificationTime)
    }

    @Test
    fun `ls streams nodes to the listener and returns the snapshot`() {
        val id = fixture.backup().snapshotId.orEmpty()
        val seen = mutableListOf<String>()
        val snapshot = restic.ls(id, LsOptions.builder().recursive().build()) { seen += it.name }
        assertEquals(id, snapshot.id)
        assertTrue(seen.containsAll(listOf("a.txt", "b.txt")), seen.toString())
    }

    @Test
    fun `ls restricted to a directory sorted and reversed`() {
        fixture.backup()
        val directory = restic.listNodes("latest", LsOptions.builder().recursive().build()).nodes.first { it.name == "sub" }.path
        val inside = restic.listNodes("latest", LsOptions.builder().directory(directory).build()).nodes
        assertEquals(listOf("sub", "b.txt"), inside.map { it.name })

        val bySize = restic.listNodes("latest", LsOptions.builder().recursive().sort(LsSort.SIZE).build()).nodes.filter { it.isFile() }
        assertEquals(bySize.sortedBy { it.size }.map { it.name }, bySize.map { it.name })
        val reversed = restic.listNodes("latest", LsOptions.builder().recursive().reverse().long().humanReadable().build()).nodes
        assertTrue(reversed.isNotEmpty())
    }

    @Test
    fun `ls with latest honours host and tag filters`() {
        fixture.backup {
            host("alpha")
            tag("a")
        }
        fixture.backup {
            host("beta")
            tag("b")
        }
        assertEquals("beta", restic.ls("latest", LsOptions.builder().host("beta").build()).hostname)
        assertEquals(listOf("a"), restic.ls("latest", LsOptions.builder().tag("a").build()).tags)
        assertEquals(
            fixture.source.toString(),
            restic.ls("latest", LsOptions.builder().path(fixture.source.toString()).build()).paths.single(),
        )
    }

    @Test
    fun `ncdu export is returned as text`() {
        fixture.backup()
        val export = restic.lsNcdu()
        assertTrue(export.startsWith("[1, 2,"), export.take(40))
        assertTrue(export.contains("a.txt"))
    }

    @Test
    fun `find by name pattern`() {
        val id = fixture.backup().snapshotId.orEmpty()
        val result = restic.find(listOf("*.txt")).single()
        assertEquals(id, result.snapshotId)
        assertEquals(2, result.hits)
        assertEquals(setOf("a.txt", "b.txt"), result.matches.map { it.path.substringAfterLast('/') }.toSet())
    }

    @Test
    fun `find with ignore case snapshot restriction and dsl`() {
        val first = fixture.backup().snapshotId.orEmpty()
        fixture.backup()
        assertEquals(0, restic.find(listOf("*.TXT")).size)
        val matches =
            restic.find(listOf("*.TXT")) {
                ignoreCase()
                snapshotId(first)
                long()
                humanReadable()
            }
        assertEquals(listOf(first), matches.map { it.snapshotId })
    }

    @Test
    fun `find with host path tag and date filters`() {
        fixture.backup {
            host("alpha")
            tag("x")
        }
        val options =
            FindOptions.builder().host(
                "alpha",
            ).tag("x").path(fixture.source.toString()).newest("2999-01-01").oldest("2000-01-01").reverse().build()
        assertEquals(1, restic.find(listOf("a.txt"), options).size)
        assertEquals(0, restic.find(listOf("a.txt"), FindOptions.builder().host("nobody").build()).size)
    }

    @Test
    fun `find objects by blob and tree id`() {
        val id = fixture.backup().snapshotId.orEmpty()
        val directoryTreeId = listBlobIds("tree").first()
        val trees = restic.findObjects(FindObjectKind.TREE, listOf(directoryTreeId), FindOptions.builder().showPackId().build())
        assertTrue(trees.all { it.objectType == "tree" && it.snapshotId == id }, trees.toString())
        val blobId = listBlobIds("data").first()
        val blobs = restic.findObjects(FindObjectKind.BLOB, listOf(blobId))
        assertEquals(blobId, blobs.first().id)
        assertTrue(blobs.first().path.orEmpty().isNotEmpty())
    }

    private fun listBlobIds(type: String): List<String> {
        val lines = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("list", "blobs"), lines::add)
        return lines.filter { it.startsWith("$type ") }.map { it.substringAfter(' ').trim() }
    }
}
