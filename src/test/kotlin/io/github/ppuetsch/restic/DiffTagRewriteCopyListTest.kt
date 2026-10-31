package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

class DiffTagRewriteCopyListTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixture() {
        fixture = RepositoryFixture(temporaryDirectory)
    }

    private fun twoSnapshotsWithOneChangedAndOneAddedFile(): Pair<String, String> {
        val first = fixture.backup().snapshotId.orEmpty()
        fixture.source.resolve("a.txt").writeText("changed")
        fixture.source.resolve("new.txt").writeText("new file")
        fixture.source.resolve("debug.log").deleteExisting()
        return first to fixture.backup().snapshotId.orEmpty()
    }

    private fun namesIn(snapshotId: String): Set<String> =
        restic.listNodes(snapshotId, LsOptions.builder().recursive().build()).nodes.filter { it.isFile() }.map { it.name }.toSet()

    @Test
    fun `diff reports modified added and removed entries`() {
        val (first, second) = twoSnapshotsWithOneChangedAndOneAddedFile()
        val result = restic.diffAll(first, second)
        val byName = result.changes.associate { it.path.substringAfterLast('/') to it.modifier }
        assertEquals("M", byName["a.txt"])
        assertEquals("+", byName["new.txt"])
        assertEquals("-", byName["debug.log"])
        assertEquals(first, result.statistics.sourceSnapshotId)
        assertEquals(second, result.statistics.targetSnapshotId)
        assertEquals(1, result.statistics.changedFiles)
        assertEquals(1, result.statistics.added.files)
        assertEquals(1, result.statistics.removed.files)
        assertTrue(result.statistics.added.bytes > 0)
    }

    @Test
    fun `diff streams changes to the listener and supports metadata`() {
        val (first, second) = twoSnapshotsWithOneChangedAndOneAddedFile()
        val seen = mutableListOf<DiffChange>()
        restic.diff(first, second, DiffOptions.builder().metadata().build(), seen::add)
        assertTrue(seen.isNotEmpty())
    }

    @Test
    fun `tag add remove and set`() {
        val id = fixture.backup { tag("one") }.snapshotId.orEmpty()
        val added =
            restic.tag {
                snapshotId(id)
                add("two", "three")
            }
        assertEquals(1, added.changedSnapshots)
        assertEquals(id, added.changes.single().oldSnapshotId)
        assertEquals(listOf("one", "three", "two"), restic.snapshots().single().tags.sorted())

        restic.tag {
            remove("one")
            snapshotId(restic.snapshots().single().id)
        }
        assertEquals(listOf("three", "two"), restic.snapshots().single().tags.sorted())

        restic.tag {
            set("only")
            snapshotId(restic.snapshots().single().id)
        }
        assertEquals(listOf("only"), restic.snapshots().single().tags)
    }

    @Test
    fun `tag filters select snapshots`() {
        fixture.backup {
            host("alpha")
            tag("a")
        }
        fixture.backup {
            host("beta")
            tag("b")
        }
        val result =
            restic.tag(
                TagOptions.builder().host("beta").tag("b").path(fixture.source.toString()).add("marked").build(),
            )
        assertEquals(1, result.changedSnapshots)
        assertEquals(listOf("marked"), restic.snapshots { host("beta") }.single().tags.filter { it == "marked" })
        assertFalse(restic.snapshots { host("alpha") }.single().tags.contains("marked"))
    }

    @Test
    fun `rewrite excludes files and forgets the original`() {
        val id = fixture.backup().snapshotId.orEmpty()
        val result =
            restic.rewrite {
                exclude("*.txt")
                snapshotId(id)
                forget()
            }
        assertEquals(1, result.newSnapshotIds.size)
        val snapshot = restic.snapshots().single()
        assertTrue(snapshot.id.startsWith(result.newSnapshotIds.single()))
        assertEquals(setOf("debug.log"), namesIn(snapshot.id))
    }

    @Test
    fun `rewrite dry run changes nothing`() {
        val id = fixture.backup().snapshotId.orEmpty()
        val lines = mutableListOf<String>()
        restic.rewrite(RewriteOptions.builder().dryRun().exclude("*.txt").snapshotId(id).build(), lines::add)
        assertTrue(lines.any { it.contains("would") }, lines.toString())
        assertEquals(listOf(id), restic.snapshots().map { it.id })
    }

    @Test
    fun `rewrite replaces host and time and accepts case insensitive excludes`() {
        val id = fixture.backup().snapshotId.orEmpty()
        val newTime = LocalDateTime.of(2019, 5, 6, 7, 8, 9)
        restic.rewrite {
            snapshotId(id)
            newHost("renamed")
            newTime(newTime)
            forget()
        }
        val snapshot = restic.snapshots().single()
        assertEquals("renamed", snapshot.hostname)
        assertTrue(snapshot.time.toString().startsWith("2019-05-0"), snapshot.time.toString())

        val patterns = temporaryDirectory.resolve("patterns.txt").also { it.writeText("*.log\n") }
        restic.rewrite {
            snapshotId(snapshot.id)
            caseInsensitiveExclude("*.TXT")
            caseInsensitiveExcludeFile(patterns)
            excludeFile(patterns)
            forget()
        }
        assertEquals(emptySet<String>(), namesIn("latest"))
    }

    @Test
    fun `rewrite filters and snapshot summary flag`() {
        fixture.backup {
            host("alpha")
            tag("a")
        }
        val result =
            restic.rewrite {
                host("alpha")
                tag("a")
                path(fixture.source.toString())
                snapshotSummary()
                exclude("*.log")
                dryRun()
            }
        assertTrue(result.outputLines.isNotEmpty())
    }

    private fun secondRepository(configure: ResticConfiguration.Builder.() -> Unit = {}): Restic {
        val restic = resticFor(temporaryDirectory.resolve("target-repo"), configure)
        restic.init()
        return restic
    }

    @Test
    fun `copy transfers all snapshots from another repository`() {
        fixture.backup { tag("x") }
        fixture.backup { tag("y") }
        val target = secondRepository()
        val result =
            target.copy {
                fromRepository(fixture.repository.toString())
                fromPassword(TEST_PASSWORD)
            }
        assertEquals(2, result.newSnapshotIds.size)
        assertEquals(2, target.snapshots().size)
    }

    @Test
    fun `copy with a password file and snapshot selection`() {
        val first = fixture.backup().snapshotId.orEmpty()
        fixture.backup()
        val passwordFile = temporaryDirectory.resolve("source-pw.txt").also { it.writeText(TEST_PASSWORD) }
        val target = secondRepository()
        target.copy {
            fromRepository(fixture.repository.toString())
            fromPasswordFile(passwordFile)
            snapshotId(first)
        }
        assertEquals(1, target.snapshots().size)
    }

    @Test
    fun `copy with password command repository file and filters`() {
        fixture.backup {
            host("alpha")
            tag("keep")
        }
        fixture.backup {
            host("beta")
            tag("skip")
        }
        val repositoryFile = temporaryDirectory.resolve("source-repo.txt").also { it.writeText(fixture.repository.toString()) }
        val target = secondRepository()
        val lines = mutableListOf<String>()
        target.copy(
            CopyOptions.builder()
                .fromRepositoryFile(repositoryFile)
                .fromPasswordCommand("echo $TEST_PASSWORD")
                .host("alpha")
                .tag("keep")
                .path(fixture.source.toString())
                .build(),
            lines::add,
        )
        assertEquals(listOf("alpha"), target.snapshots().map { it.hostname })
        assertTrue(lines.isNotEmpty())
    }

    @Test
    fun `copy from a repository without password and with key hint`() {
        val insecureSource = temporaryDirectory.resolve("insecure-source")
        val insecure =
            Restic(
                ResticConfiguration.builder().binary(
                    resticBinaryForTests,
                ).repository(insecureSource.toString()).insecureNoPassword().build(),
            )
        insecure.init()
        insecure.backup(listOf(fixture.source))
        val target = secondRepository()
        val keyId = insecure.list(ListObjectType.KEYS).first()
        target.copy {
            fromRepository(insecureSource.toString())
            fromInsecureNoPassword()
            fromKeyHint(keyId)
        }
        assertEquals(1, target.snapshots().size)
    }

    @Test
    fun `list enumerates repository objects`() {
        val id = fixture.backup().snapshotId.orEmpty()
        assertEquals(listOf(id), restic.list(ListObjectType.SNAPSHOTS))
        assertEquals(1, restic.list(ListObjectType.KEYS).size)
        assertTrue(restic.list(ListObjectType.PACKS).isNotEmpty())
        assertTrue(restic.list(ListObjectType.INDEX).isNotEmpty())
        assertEquals(emptyList<String>(), restic.list(ListObjectType.LOCKS))
        val blobs = restic.listBlobs()
        assertTrue(blobs.any { it.type == "data" && it.id.length == 64 })
        assertTrue(blobs.any { it.type == "tree" })
    }
}
