#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <empty-runtime-directory> <java-8-executable> [compat]" >&2
  exit 2
fi

runtime="$1"
java8="$2"
version="$(sed -n 's/^minecraft_version=//p' gradle.properties | tr -d '\r')-$(sed -n 's/^forge_version=//p' gradle.properties | tr -d '\r')"
installer="${runtime}/forge-${version}-installer.jar"
expected="$(sed -n 's/^forge_installer_sha256=//p' gradle.properties | tr -d '\r')"
if [[ "${3:-}" == compat ]]; then
  version="1.12.2-$(sed -n 's/^forge_compat_version=//p' gradle.properties | tr -d '\r')"
  installer="${runtime}/forge-${version}-installer.jar"
  expected="$(sed -n 's/^forge_compat_installer_sha256=//p' gradle.properties | tr -d '\r')"
elif [[ -n "${3:-}" ]]; then
  echo "Unknown runtime selector: $3" >&2
  exit 2
fi

if [[ -e "$runtime" ]] && [[ -n "$(find "$runtime" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "Packaged Forge destination must be empty: $runtime" >&2
  exit 1
fi
mkdir -p "$runtime"

curl --fail --location --silent --show-error \
  "https://maven.minecraftforge.net/net/minecraftforge/forge/${version}/forge-${version}-installer.jar" \
  --output "$installer"
actual="$(sha256sum "$installer" | awk '{print toupper($1)}')"
if [[ "$actual" != "$expected" ]]; then
  echo "Forge installer SHA-256 is $actual; expected $expected" >&2
  exit 1
fi

(
  cd "$runtime"
  "$java8" -jar "$installer" --installServer .
)
installed="$runtime/forge-${version}.jar"
universal="$runtime/forge-${version}-universal.jar"
if [[ -f "$installed" ]]; then
  cp "$installed" "$universal"
fi
test -f "$runtime/forge-${version}-universal.jar"
test -f "$runtime/minecraft_server.1.12.2.jar"
test -d "$runtime/libraries"
