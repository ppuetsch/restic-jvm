#!/usr/bin/env bash
# Usage: install-restic.sh <version> <target-dir>
# Lädt die offizielle restic-Release-Binary und prüft sie gegen SHA256SUMS.
set -euo pipefail

version="$1"
target="$2"
base="https://github.com/restic/restic/releases/download/v${version}"

case "$(uname -s)" in
  Linux*)  archive="restic_${version}_linux_amd64.bz2" ;;
  MINGW*|MSYS*|CYGWIN*) archive="restic_${version}_windows_amd64.zip" ;;
  *) echo "unsupported OS" >&2; exit 1 ;;
esac

mkdir -p "$target"
work="$(mktemp -d)"
curl -sSfL -o "$work/$archive" "$base/$archive"
curl -sSfL -o "$work/SHA256SUMS" "$base/SHA256SUMS"
( cd "$work" && grep " ${archive}\$" SHA256SUMS | sha256sum -c - )

case "$archive" in
  *.bz2) bunzip2 -c "$work/$archive" > "$target/restic"; chmod +x "$target/restic" ;;
  *.zip) powershell -NoProfile -Command "Expand-Archive -Path '$(cygpath -w "$work/$archive")' -DestinationPath '$(cygpath -w "$target")' -Force"
         mv "$target/restic_${version}_windows_amd64.exe" "$target/restic.exe" ;;
esac
"$target/restic" version
