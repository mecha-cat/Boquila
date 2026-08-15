#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-}"
APP_NAME="${APP_NAME:-Boquila}"
MAIN_CLASS="${MAIN_CLASS:-com.example.workreport.Main}"
MAINTAINER="${MAINTAINER:-Boquila <dev@boquila.example>}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

need() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required tool: $1" >&2
        exit 1
    fi
}

step() { echo "== $* =="; }

need java
need mvn
need jpackage

if [ -z "$VERSION" ]; then
    VERSION="$(cd "$ROOT" && mvn -q help:evaluate -Dexpression=project.version -DforceStdout)"
fi

JAR="$ROOT/target/workreport-$VERSION.jar"
STAGING="$ROOT/target/packaging/staging-linux"
DIST="$ROOT/target/dist"
ICON="$ROOT/packaging/icons/boquila.png"

step "Build jar"
(cd "$ROOT" && mvn -q -DskipTests package)

[ -f "$JAR" ] || { echo "Expected $JAR" >&2; exit 1; }

step "Prepare staging dir"
rm -rf "$STAGING"
mkdir -p "$STAGING"
cp "$JAR" "$STAGING/"
(cd "$ROOT" && mvn -q dependency:copy-dependencies \
    "-DincludeClassifiers=linux" \
    "-DoutputDirectory=$STAGING")

COUNT="$(find "$STAGING" -maxdepth 1 -name 'javafx-*-linux.jar' | wc -l)"
[ "$COUNT" -ge 3 ] || { echo "Expected javafx linux classifier jars, found $COUNT" >&2; exit 1; }

step "Ensure dpkg tooling"
if ! command -v dpkg-deb >/dev/null 2>&1 || ! command -v ar >/dev/null 2>&1; then
    if [ "$(id -u)" -eq 0 ]; then
        apt-get update && apt-get install -y dpkg-dev binutils fakeroot
    else
        sudo apt-get update && sudo apt-get install -y dpkg-dev binutils fakeroot
    fi
fi

step "jpackage --type deb"
rm -rf "$DIST"
mkdir -p "$DIST"
jpackage --type deb \
    --input "$STAGING" \
    --main-jar "$(basename "$JAR")" \
    --main-class "$MAIN_CLASS" \
    --module-path "$STAGING" \
    --add-modules javafx.controls \
    --name "$APP_NAME" \
    --app-version "$VERSION" \
    --vendor "Boquila" \
    --icon "$ICON" \
    --linux-shortcut \
    --linux-menu-group "$APP_NAME" \
    --linux-deb-maintainer "$MAINTAINER" \
    --dest "$DIST"

DEB="$DIST/${APP_NAME}_${VERSION}_amd64.deb"
[ -f "$DEB" ] || { echo "Expected $DEB" >&2; exit 1; }

step "Inject Depends: git (auto-install dependency)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
dpkg-deb -x "$DEB" "$WORK/data"
dpkg-deb -e "$DEB" "$WORK/DEBIAN"
CONTROL="$WORK/DEBIAN/control"
if grep -q '^Depends:' "$CONTROL"; then
    sed -i 's/^Depends: \(.*\)/Depends: \1, git/' "$CONTROL"
else
    printf 'Depends: git\n' >> "$CONTROL"
fi
dpkg-deb --build --root-owner-group "$WORK" "$DEB" >/dev/null

step "Verification"
dpkg-deb -I "$DEB" | grep -E '^(Package|Version|Architecture|Depends):' || true
echo "Linux installer ready: $DEB"