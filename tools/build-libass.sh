#!/usr/bin/env bash

set -euo pipefail

if [ $# -gt 1 ]; then
  echo "usage: $0 [/path/to/libass-android]" >&2
  exit 2
fi
REPO="$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)"
STAGING="${REPO}/.libass-build"
CHECKOUT="${STAGING}/libass-android"
OUTPUT="${REPO}/app/libs/m2"
VERSION="$(sed -n 's/^libassAndroid = "\(.*\)"$/\1/p' "${REPO}/gradle/libs.versions.toml")"
if [ -z "${VERSION}" ]; then
  echo "could not read the libassAndroid version from gradle/libs.versions.toml" >&2
  exit 1
fi

android_sdk() {
  local from_properties
  from_properties="$(sed -n 's/^sdk.dir=//p' "${REPO}/local.properties" 2>/dev/null)"
  echo "${from_properties:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
}
SDK="$(android_sdk)"
if [ -z "${SDK}" ]; then
  echo "set sdk.dir in local.properties, or export ANDROID_HOME" >&2
  exit 1
fi

if [ $# -eq 1 ]; then
  if [ ! -d "$1" ]; then
    echo "libass fork directory not found: $1" >&2
    exit 1
  fi
  LIBASS_DIR="$(cd "$1" && pwd)"
else
  if [ -z "${LIBASS_REPO_URL:-}" ]; then
    echo "pass a libass-android checkout, or set LIBASS_REPO_URL to clone tag v${VERSION}" >&2
    exit 1
  fi
  mkdir -p "${STAGING}"
  rm -rf "${CHECKOUT}"
  git clone --depth 1 --single-branch --branch "v${VERSION}" "${LIBASS_REPO_URL}" "${CHECKOUT}"
  LIBASS_DIR="${CHECKOUT}"
  (cd "${LIBASS_DIR}" && ./bootstrap.sh)
fi

mkdir -p "${OUTPUT}"
(
  cd "${LIBASS_DIR}"
  # Incremental Kotlin builds in a reused checkout have dropped file facades from the module index.
  rm -rf lib_ass_kt/build/kotlin lib_ass_media/build/kotlin
  ANDROID_HOME="${SDK}" PATH="${SDK}/cmake/3.22.1/bin:${PATH}" ./gradlew \
    -Dmaven.repo.local="${OUTPUT}" -PVERSION_NAME="${VERSION}" \
    :lib_ass_kt:publishToMavenLocal :lib_ass_media:publishToMavenLocal
)

for name in kt media; do
  pom="${OUTPUT}/io/github/peerless2012/ass-${name}/${VERSION}/ass-${name}-${VERSION}.pom"
  if [ ! -f "${pom}" ]; then
    echo "missing published POM: ${pom}" >&2
    exit 1
  fi
  echo "published ${pom%.pom}.aar"
done
