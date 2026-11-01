package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class ForgetPruneCheckStatsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixtureWithThreeSnapshots() {
        fixture = RepositoryFixture(temporaryDirectory)
        fixture.backup { tag("old") }
        fixture.source.resolve("a.txt").writeText("changed once")
        fixture.backup { tag("middle") }
        fixture.source.resolve("a.txt").writeText("changed twice")
        fixture.backup { tag("new") }
    }

    @Test
    fun `forget dry run reports but does not remove`() {
        val result =
            restic.forget {
                keepLast(1)
                dryRun()
            }
        assertEquals(2, result.removedSnapshots().size)
        assertEquals(1, result.groups.single().keep.size)
        assertTrue(result.groups.single().reasons.isNotEmpty())
        assertEquals(3, restic.snapshots().size)
    }

    @Test
    fun `forget keep last removes older snapshots`() {
        val result = restic.forget { keepLast(1) }
        assertEquals(2, result.removedSnapshots().size)
        assertEquals(listOf("new"), restic.snapshots().single().tags)
    }

    @Test
    fun `forget by snapshot id`() {
        val oldest = restic.snapshots().first { it.tags == listOf("old") }
        restic.forget { snapshotId(oldest.id) }
        assertEquals(2, restic.snapshots().size)
        assertFalse(restic.snapshots().any { it.id == oldest.id })
    }

    @Test
    fun `forget keep tag filters and grouping`() {
        restic.forget {
            keepTag("old")
            keepTag("new")
            groupBy("host")
            compact()
        }
        assertEquals(setOf("old", "new"), restic.snapshots().flatMap { it.tags }.toSet())
    }

    @Test
    fun `forget with host path and tag filters only considers matching snapshots`() {
        val result =
            restic.forget {
                host("nobody")
                keepLast(1)
                dryRun()
            }
        assertEquals(0, result.removedSnapshots().size)
        val matching =
            restic.forget {
                tag("old")
                path(fixture.source.toString())
                keepLast(1)
                unsafeAllowRemoveAll()
                dryRun()
            }
        assertEquals(0, matching.removedSnapshots().size)
    }

    @Test
    fun `forget refuses to remove the last snapshot of a group without the unsafe flag`() {
        assertThrows<ResticFailedException> {
            restic.forget {
                keepLast(0)
                host(restic.snapshots().first().hostname)
            }
        }
        assertEquals(3, restic.snapshots().size)
        restic.forget {
            keepLast(0)
            host(restic.snapshots().first().hostname)
            unsafeAllowRemoveAll()
        }
        assertEquals(0, restic.snapshots().size)
    }

    @Test
    fun `forget accepts all calendar based keep rules`() {
        val result =
            restic.forget {
                keepHourly(1)
                keepDaily(1)
                keepWeekly(1)
                keepMonthly(1)
                keepYearly(1)
                keepWithin("1d")
                keepWithinHourly("1d")
                keepWithinDaily("1d")
                keepWithinWeekly("1d")
                keepWithinMonthly("1d")
                keepWithinYearly("1d")
                dryRun()
            }
        assertEquals(0, result.removedSnapshots().size)
    }

    @Test
    fun `forget accepts unlimited keep counts`() {
        val result =
            restic.forget {
                keepLastUnlimited()
                keepHourlyUnlimited()
                keepDailyUnlimited()
                keepWeeklyUnlimited()
                keepMonthlyUnlimited()
                keepYearlyUnlimited()
                dryRun()
            }
        assertEquals(0, result.removedSnapshots().size)
    }

    @Test
    fun `forget with prune returns the prune output`() {
        val lines = mutableListOf<String>()
        val result = restic.forget(ForgetOptions.builder().keepLast(1).prune().maxUnused("0").repackSmall().build(), lines::add)
        assertEquals(2, result.removedSnapshots().size)
        assertTrue(result.pruneOutputLines.isNotEmpty())
        assertEquals(result.pruneOutputLines, lines)
    }

    @Test
    fun `forget output that is not json is never parsed as the group document`() {
        val fake =
            FakeRestic(
                temporaryDirectory.resolve("fake").also(Files::createDirectories),
                "out []",
                "out [0:00] 100.00%  3 / 3 snapshots",
            )

        val result = fake.restic().forget(ForgetOptions.builder().keepLast(1).prune().build())

        assertEquals(listOf("[0:00] 100.00%  3 / 3 snapshots"), result.pruneOutputLines)
    }

    @Test
    fun `forget accepts all prune tuning flags`() {
        restic.forget {
            keepLast(2)
            prune()
            maxRepackSize("1M")
            repackCacheableOnly()
            repackSmallerThan("1M")
            repackUncompressed()
        }
        assertEquals(2, restic.snapshots().size)
    }

    @Test
    fun `prune frees space after forget`() {
        restic.forget { keepLast(1) }
        val result = restic.prune()
        assertTrue(result.outputLines.any { it.contains("done") }, result.outputLines.toString())
        assertTrue(restic.check().isHealthy())
    }

    @Test
    fun `prune dry run and tuning flags`() {
        restic.forget { keepLast(1) }
        val lines = mutableListOf<String>()
        restic.prune(PruneOptions.builder().dryRun().maxUnused("5%").maxRepackSize("10M").repackSmall().build(), lines::add)
        assertTrue(lines.isNotEmpty())
        restic.prune {
            repackCacheableOnly()
            repackSmallerThan("1M")
            repackUncompressed()
        }
    }

    @Test
    fun `check of a healthy repository`() {
        val result = restic.check()
        assertTrue(result.isHealthy())
        assertEquals(0, result.errorCount)
        assertEquals(emptyList<String>(), result.brokenPacks)
        assertFalse(result.suggestRepairIndex)
    }

    @Test
    fun `check with read data variants`() {
        assertTrue(restic.check { readData() }.isHealthy())
        assertTrue(restic.check { readDataSubset("1/2") }.isHealthy())
        assertTrue(restic.check { withCache() }.isHealthy())
    }

    @Test
    fun `check detects a missing pack`() {
        val dataDirectory = fixture.repository.resolve("data")
        val pack = Files.walk(dataDirectory).use { stream -> stream.filter { Files.isRegularFile(it) }.findFirst().orElseThrow() }
        Files.delete(pack)
        val result = restic.check { readData() }
        assertFalse(result.isHealthy())
        assertTrue(result.errorCount > 0 || result.errorMessages.isNotEmpty())
    }

    @Test
    fun `stats in the default mode`() {
        val stats = restic.stats()
        assertEquals(3, stats.snapshotsCount)
        assertTrue(stats.totalSize > 0)
        assertTrue(stats.totalFileCount!! > 0)
    }

    @Test
    fun `stats modes`() {
        val raw = restic.stats { mode(StatsMode.RAW_DATA) }
        assertNotNull(raw.totalBlobCount)
        assertNotNull(raw.totalUncompressedSize)
        assertNotNull(raw.compressionRatio)
        assertNotNull(raw.compressionProgress)
        assertNotNull(raw.compressionSpaceSaving)
        assertNull(raw.totalFileCount)

        assertNotNull(restic.stats { mode(StatsMode.BLOBS_PER_FILE) }.totalBlobCount)
        assertTrue(restic.stats { mode(StatsMode.FILES_BY_CONTENTS) }.totalSize > 0)
        assertTrue(restic.stats { mode(StatsMode.RESTORE_SIZE) }.totalSize > 0)
    }

    @Test
    fun `stats can be restricted to snapshots`() {
        val first = restic.snapshots().first { it.tags == listOf("old") }
        assertEquals(1, restic.stats { snapshotId(first.id) }.snapshotsCount)
        assertEquals(1, restic.stats { tag("new") }.snapshotsCount)
        assertEquals(
            3,
            restic.stats {
                host(first.hostname)
                path(fixture.source.toString())
            }.snapshotsCount,
        )
    }
}
