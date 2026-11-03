rootProject.name = "restic-jvm"

// Optional artifacts with the restic binary for one platform each, plus one that pulls in all of them.
val bundledPlatforms = listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "macos-x86_64", "macos-aarch64")

bundledPlatforms.forEach { platform ->
    include(":restic-jvm-binary-$platform")
    project(":restic-jvm-binary-$platform").projectDir = file("binaries/$platform")
}
include(":restic-jvm-binaries-all")
project(":restic-jvm-binaries-all").projectDir = file("binaries/all")
