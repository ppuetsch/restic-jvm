package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class AdminCommandsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixture() {
        fixture = RepositoryFixture(temporaryDirectory)
    }

    private fun resticWithPassword(password: String): Restic =
        Restic(
            ResticConfiguration
                .builder()
                .binary(resticBinaryForTests)
                .repository(fixture.repository.toString())
                .password(password)
                .build(),
        )

    @Test
    fun `key list shows the current key`() {
        val keys = restic.keys().list()
        val key = keys.single()
        assertTrue(key.current)
        assertEquals(
            64,
            restic
                .list(ListObjectType.KEYS)
                .single()
                .id.length,
        )
        assertTrue(
            restic
                .list(ListObjectType.KEYS)
                .single()
                .id
                .startsWith(key.id),
        )
        assertNotNull(key.created)
        assertTrue(key.userName.isNotEmpty() && key.hostName.isNotEmpty())
    }

    @Test
    fun `key add with a new password makes the new password usable`() {
        val result =
            restic.keys().add {
                newPassword("second-password")
                user("alice")
                host("laptop")
            }
        assertEquals(64, result.keyId?.length)
        assertEquals(2, restic.keys().list().size)
        val other = resticWithPassword("second-password")
        assertEquals(2, other.keys().list().size)
        assertTrue(other.keys().list().any { it.userName == "alice" && it.hostName == "laptop" && it.current })
    }

    @Test
    fun `key add from a password file`() {
        val passwordFile = temporaryDirectory.resolve("new-pw.txt").also { it.writeText("from-file") }
        restic.keys().add(NewKeyOptions.builder().newPasswordFile(passwordFile).build())
        assertEquals(2, resticWithPassword("from-file").keys().list().size)
    }

    @Test
    fun `key add without password`() {
        restic.keys().add { newInsecureNoPassword() }
        val noPassword =
            Restic(
                ResticConfiguration
                    .builder()
                    .binary(
                        resticBinaryForTests,
                    ).repository(fixture.repository.toString())
                    .insecureNoPassword()
                    .build(),
            )
        assertEquals(2, noPassword.keys().list().size)
    }

    @Test
    fun `key remove deletes another key`() {
        val added = restic.keys().add { newPassword("temporary") }
        restic.keys().remove(added.keyId.orEmpty())
        assertEquals(1, restic.keys().list().size)
        assertThrows<WrongPasswordException> { resticWithPassword("temporary").keys().list() }
    }

    @Test
    fun `key passwd replaces the current key`() {
        restic.keys().changePassword { newPassword("changed-password") }
        assertThrows<WrongPasswordException> { restic.keys().list() }
        val changed = resticWithPassword("changed-password")
        assertEquals(1, changed.keys().list().size)
        assertTrue(changed.snapshots().isEmpty())
    }

    @Test
    fun `unlock removes stale locks and accepts remove all`() {
        restic.maintenance().unlock()
        restic.maintenance().unlock(removeAll = true)
        assertTrue(restic.list(ListObjectType.LOCKS).isEmpty())
    }

    @Test
    fun `migrate lists available migrations`() {
        val output = restic.maintenance().migrate()
        assertTrue(output.lines.isNotEmpty())
        assertTrue(
            restic
                .maintenance()
                .migrate(force = true)
                .lines
                .isNotEmpty(),
        )
    }

    @Test
    fun `cache with all flags`() {
        assertTrue(
            restic
                .maintenance()
                .cache()
                .lines
                .isNotEmpty(),
        )
        assertTrue(
            restic
                .maintenance()
                .cache { noSize() }
                .lines
                .isNotEmpty(),
        )
        restic.maintenance().cache {
            cleanup()
            maxAgeDays(1000)
        }
    }

    @Test
    fun `features lists feature flags`() {
        val features = restic.maintenance().features()
        assertTrue(features.isNotEmpty())
        val flag = features.first { it.name == "safe-forget-keep-tags" }
        assertEquals("stable", flag.stage)
        assertTrue(flag.enabledByDefault)
        assertTrue(flag.description.isNotEmpty())
    }

    @Test
    fun `recover and repair index and packs`() {
        fixture.backup()
        assertTrue(
            restic
                .maintenance()
                .recover()
                .lines
                .isNotEmpty(),
        )
        assertTrue(
            restic
                .repair()
                .index()
                .lines
                .isNotEmpty(),
        )
        assertTrue(
            restic
                .repair()
                .index(readAllPacks = true)
                .lines
                .isNotEmpty(),
        )
        assertTrue(restic.maintenance().check().isHealthy())
        val pack = restic.list(ListObjectType.PACKS).first().id
        val packBackupDirectory = temporaryDirectory.resolve("pack-backups").also(Files::createDirectories)
        resticFor(fixture.repository) { workingDirectory(packBackupDirectory) }.repair().packs(listOf(pack))
        assertTrue(Files.exists(packBackupDirectory.resolve("pack-$pack")), "restic keeps a copy of the repaired pack")
    }

    @Test
    fun `repair snapshots with filters`() {
        fixture.backup { tag("t") }
        val dryRun = restic.repair().snapshots(RepairSnapshotsOptions.builder().dryRun().build())
        assertTrue(dryRun.lines.isNotEmpty())
        val id = restic.snapshots().single().id
        restic.repair().snapshots {
            snapshotId(id)
            host(restic.snapshots().single().hostname)
            tag("t")
            path(fixture.source.toString())
            forget()
        }
        assertEquals(1, restic.snapshots().size)
        assertFalse(Files.notExists(fixture.repository))
    }
}
