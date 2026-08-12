#!/usr/bin/env bash
#
# Builds the media3 FFmpeg decoder extension AAR into app/libs, following the build instructions
# in libraries/decoder_ffmpeg/README.md of the androidx/media checkout it clones.
#
# The output is LGPL-2.1-or-later: no --enable-gpl, no --enable-nonfree.
# Rerun after every media3 bump. See docs/adr/0021-ffmpeg-audio-decoders.md.
#
# Usage: tools/build-ffmpeg-decoder.sh /path/to/android/ndk [--clean]

set -euo pipefail

readonly FFMPEG_RELEASE="release/9.0"
# Every codec DynamicProfileBuilder advertises, plus alac. Raw PCM is absent on purpose: media3
# plays it without a decoder, which is why AudioRouteSink exempts AUDIO_RAW.
readonly DECODERS=(aac ac3 eac3 dca flac mp3 opus vorbis truehd alac)
readonly ARCHITECTURES=(armeabi-v7a arm64-v8a)

if [ $# -lt 1 ]; then
  echo "usage: $0 /path/to/android/ndk [--clean]" >&2
  exit 2
fi
NDK_PATH="$(cd "$1" && pwd)"

repo() {
  git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel
}
readonly REPO="$(repo)"
readonly STAGING="${REPO}/.media3-build"
readonly CHECKOUT="${STAGING}/media"
readonly OUTPUT="${REPO}/app/libs"

media3_version() {
  sed -n 's/^media3 = "\(.*\)"$/\1/p' "${REPO}/gradle/libs.versions.toml"
}
readonly VERSION="$(media3_version)"
if [ -z "${VERSION}" ]; then
  echo "could not read the media3 version from gradle/libs.versions.toml" >&2
  exit 1
fi

android_sdk() {
  local from_properties
  from_properties="$(sed -n 's/^sdk.dir=//p' "${REPO}/local.properties" 2>/dev/null)"
  echo "${from_properties:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
}
readonly SDK="$(android_sdk)"
if [ -z "${SDK}" ]; then
  echo "set sdk.dir in local.properties, or export ANDROID_HOME" >&2
  exit 1
fi

host_platform() {
  case "$(uname -s)" in
    Darwin) echo "darwin-x86_64" ;;
    Linux) echo "linux-x86_64" ;;
    *) echo "unsupported build host: $(uname -s)" >&2; return 1 ;;
  esac
}

if [ "${2:-}" = "--clean" ]; then
  rm -rf "${STAGING}"
fi
mkdir -p "${STAGING}" "${OUTPUT}"

echo "media3 ${VERSION}, ffmpeg ${FFMPEG_RELEASE}, ${ARCHITECTURES[*]}"

if [ -d "${CHECKOUT}/.git" ]; then
  git -C "${CHECKOUT}" fetch --depth 1 origin "${VERSION}"
  git -C "${CHECKOUT}" checkout --force FETCH_HEAD
else
  git clone --depth 1 --single-branch --branch "${VERSION}" \
    https://github.com/androidx/media.git "${CHECKOUT}"
fi

readonly MODULE="${CHECKOUT}/libraries/decoder_ffmpeg"
readonly FFMPEG_MODULE_PATH="${MODULE}/src/main"
readonly JNI="${FFMPEG_MODULE_PATH}/jni"

if [ -d "${JNI}/ffmpeg/.git" ]; then
  git -C "${JNI}/ffmpeg" fetch --depth 1 origin "${FFMPEG_RELEASE}"
  git -C "${JNI}/ffmpeg" checkout --force FETCH_HEAD
else
  git clone --depth 1 --single-branch --branch "${FFMPEG_RELEASE}" \
    https://github.com/FFmpeg/FFmpeg.git "${JNI}/ffmpeg"
fi

# 'Unknown option "--disable-postproc"' from release/9.0 configure. Dropping the other
# architectures avoids needing nasm, which only the x86 targets assemble with.
python3 - "${JNI}/build_ffmpeg.sh" <<'PATCH'
import re
import sys

path = sys.argv[1]
script = open(path).read()
script = script.replace("    --disable-postproc\n", "")
for architecture in ("x86", "x86_64"):
    stanza = re.compile(
        r"\./configure \\\n(?:(?!\./configure).)*?--libdir=android-libs/"
        + architecture
        + r" \\\n.*?make clean\n",
        re.S,
    )
    script, removed = stanza.subn("", script)
    if removed != 1:
        sys.exit(f"expected one {architecture} block, dropped {removed}")
open(path, "w").write(script)
PATCH

built=true
for architecture in "${ARCHITECTURES[@]}"; do
  [ -f "${JNI}/ffmpeg/android-libs/${architecture}/libavcodec.a" ] || built=false
done

if [ "${built}" = true ]; then
  echo "reusing the static libraries already in ${JNI}/ffmpeg/android-libs"
else
  (
    cd "${JNI}"
    ./build_ffmpeg.sh "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "$(host_platform)" \
      "$(sed -n 's/^ *minSdk = //p' "${REPO}/app/build.gradle.kts" | head -1)" \
      "${DECODERS[@]}"
  )
fi

# CMake is asked for every ABI unless told otherwise, and wants a static library per ABI.
python3 - "${MODULE}/build.gradle" "${ARCHITECTURES[*]}" <<'PATCH'
import sys

path, architectures = sys.argv[1], sys.argv[2].split()
buildscript = open(path).read()
if "abiFilters" in buildscript:
    sys.exit(0)
anchor = "android {\n    namespace 'androidx.media3.decoder.ffmpeg'\n"
if anchor not in buildscript:
    sys.exit("could not find the android block to patch")
filters = ", ".join(f"'{architecture}'" for architecture in architectures)
replacement = anchor + (
    "\n    defaultConfig {\n"
    "        ndk {\n"
    f"            abiFilters {filters}\n"
    "        }\n"
    "    }\n"
)
open(path, "w").write(buildscript.replace(anchor, replacement, 1))
PATCH

echo "sdk.dir=${SDK}" > "${CHECKOUT}/local.properties"
"${CHECKOUT}/gradlew" -p "${CHECKOUT}" :lib-decoder-ffmpeg:assembleRelease

readonly AAR="${OUTPUT}/media3-decoder-ffmpeg-${VERSION}.aar"
find "${MODULE}" -name '*-release.aar' -print -quit | xargs -I {} cp {} "${AAR}"
echo "wrote ${AAR}"
unzip -l "${AAR}" | grep 'jni/.*/$'
