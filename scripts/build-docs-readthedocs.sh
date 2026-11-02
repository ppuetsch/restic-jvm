#!/usr/bin/env bash
# Baut die API-Doku auf Read the Docs. Dessen Build-Images bringen kein Java mit, deshalb wird ein fest
# vorgegebenes Temurin-JDK geladen und vor dem Entpacken gegen seine Prüfsumme verifiziert.
set -euo pipefail

readonly JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz"
readonly JDK_SHA256="68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb"
readonly JDK_DIRECTORY="$HOME/jdk"
readonly DOKKA_OUTPUT="build/dokka/html"

main() {
    install_verified_jdk
    build_api_documentation
    publish_to_read_the_docs
}

install_verified_jdk() {
    local archive
    archive="$(mktemp)"
    curl --fail --silent --show-error --location "$JDK_URL" --output "$archive"
    echo "$JDK_SHA256  $archive" | sha256sum --check --quiet -
    mkdir -p "$JDK_DIRECTORY"
    tar --extract --gzip --file "$archive" --directory "$JDK_DIRECTORY" --strip-components=1
    export JAVA_HOME="$JDK_DIRECTORY"
}

build_api_documentation() {
    ./gradlew --no-daemon --console=plain dokkaGeneratePublicationHtml
}

publish_to_read_the_docs() {
    mkdir -p "$READTHEDOCS_OUTPUT/html"
    cp -r "$DOKKA_OUTPUT/." "$READTHEDOCS_OUTPUT/html/"
}

main "$@"
