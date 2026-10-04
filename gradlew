#!/bin/sh
# Lightweight Gradle bootstrap for the FB BM Page Manager project.
#
# The official binary wrapper jar is not shipped here; this script does the
# same job in plain shell: it downloads the Gradle 8.7 distribution once
# into ~/.gradle/wrapper/dists (or $GRADLE_USER_HOME) and reuses it on
# later runs. Requires: curl or wget, unzip, and a JDK 17+.
#
#   ./gradlew assembleDebug

set -e

GRADLE_VERSION="8.7"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
DIST_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
DIST_DIR="$DIST_ROOT/gradle-$GRADLE_VERSION"
ZIP_FILE="$DIST_ROOT/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
    echo "Downloading Gradle $GRADLE_VERSION (one-time)..."
    mkdir -p "$DIST_ROOT"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 -o "$ZIP_FILE" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP_FILE" "$DIST_URL"
    else
        echo "ERROR: curl or wget is required to download Gradle." >&2
        exit 1
    fi
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "$ZIP_FILE" -d "$DIST_ROOT"
    else
        echo "ERROR: unzip is required to extract Gradle." >&2
        exit 1
    fi
    chmod +x "$DIST_DIR/bin/gradle"
fi

exec "$DIST_DIR/bin/gradle" "$@"
