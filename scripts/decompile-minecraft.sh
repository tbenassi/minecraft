#!/usr/bin/env bash
#
# Decompile the Yarn-mapped Minecraft JAR into source_code/ using Vineflower.
#
# Usage:
#   ./scripts/decompile-minecraft.sh              # auto-detect mod with highest Yarn build
#   ./scripts/decompile-minecraft.sh hot_deposit   # use a specific mod's mappings
#
set -euo pipefail

VINEFLOWER_VERSION="1.11.2"
VINEFLOWER_URL="https://github.com/Vineflower/vineflower/releases/download/${VINEFLOWER_VERSION}/vineflower-${VINEFLOWER_VERSION}.jar"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TOOLS_DIR="$PROJECT_DIR/.tools"
VINEFLOWER_JAR="$TOOLS_DIR/vineflower-${VINEFLOWER_VERSION}.jar"
OUTPUT_DIR="$PROJECT_DIR/source_code"
DECOMPILE_INFO="$OUTPUT_DIR/.decompile-info"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

die() { echo "ERROR: $*" >&2; exit 1; }

read_prop() {
    # read_prop <file> <key> — extract value from a .properties file
    local file="$1" key="$2"
    grep -E "^${key}=" "$file" | head -1 | cut -d= -f2- | tr -d '[:space:]'
}

# ---------------------------------------------------------------------------
# Determine which mod directory to use
# ---------------------------------------------------------------------------

if [[ $# -ge 1 ]]; then
    MOD_DIR="$PROJECT_DIR/$1"
    [[ -f "$MOD_DIR/gradle.properties" ]] || die "No gradle.properties in $MOD_DIR"
else
    # Auto-detect: pick the mod whose yarn_mappings has the highest build number
    best_mod=""
    best_build=0
    for props in "$PROJECT_DIR"/*/gradle.properties; do
        dir="$(dirname "$props")"
        yarn="$(read_prop "$props" yarn_mappings 2>/dev/null || true)"
        if [[ -n "$yarn" ]]; then
            # Extract build number from e.g. "1.21.11+build.4"
            build_num="$(echo "$yarn" | grep -oE 'build\.([0-9]+)' | grep -oE '[0-9]+' || echo 0)"
            if [[ "$build_num" -gt "$best_build" ]]; then
                best_build="$build_num"
                best_mod="$dir"
            fi
        fi
    done
    [[ -n "$best_mod" ]] || die "No mod with yarn_mappings found"
    MOD_DIR="$best_mod"
fi

MOD_NAME="$(basename "$MOD_DIR")"

# ---------------------------------------------------------------------------
# Read Minecraft version & Yarn mappings
# ---------------------------------------------------------------------------

MINECRAFT_VERSION="$(read_prop "$MOD_DIR/gradle.properties" minecraft_version)"
YARN_MAPPINGS="$(read_prop "$MOD_DIR/gradle.properties" yarn_mappings)"

[[ -n "$MINECRAFT_VERSION" ]] || die "minecraft_version not found in $MOD_DIR/gradle.properties"
[[ -n "$YARN_MAPPINGS" ]] || die "yarn_mappings not found in $MOD_DIR/gradle.properties"

echo "Mod:                $MOD_NAME"
echo "Minecraft version:  $MINECRAFT_VERSION"
echo "Yarn mappings:      $YARN_MAPPINGS"

# ---------------------------------------------------------------------------
# Locate the mapped JAR in Fabric Loom's Gradle cache
# ---------------------------------------------------------------------------

# Yarn directory component: dots in version become underscores (e.g. 1.21.11 -> 1_21_11)
YARN_DIR="${MINECRAFT_VERSION//./_}"

# Full version string used in the cache path
CACHE_VERSION="${MINECRAFT_VERSION}-net.fabricmc.yarn.${YARN_DIR}.${YARN_MAPPINGS}-v2"

MAPPED_JAR="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/${CACHE_VERSION}/minecraft-merged-${CACHE_VERSION}.jar"

if [[ ! -f "$MAPPED_JAR" ]]; then
    echo ""
    echo "Mapped JAR not found at:"
    echo "  $MAPPED_JAR"
    echo ""
    echo "Run one of these from the mod directory first:"
    echo "  cd $MOD_NAME && ./gradlew build"
    echo "  cd $MOD_NAME && ./gradlew genSources"
    exit 1
fi

echo "Mapped JAR:         $MAPPED_JAR"

# ---------------------------------------------------------------------------
# Check if already decompiled with same versions
# ---------------------------------------------------------------------------

if [[ -f "$DECOMPILE_INFO" ]]; then
    prev_yarn="$(read_prop "$DECOMPILE_INFO" yarn_mappings 2>/dev/null || true)"
    prev_vf="$(read_prop "$DECOMPILE_INFO" vineflower_version 2>/dev/null || true)"

    if [[ "$prev_yarn" == "$YARN_MAPPINGS" && "$prev_vf" == "$VINEFLOWER_VERSION" ]]; then
        file_count="$(find "$OUTPUT_DIR" -name '*.java' | wc -l | tr -d '[:space:]')"
        echo ""
        echo "Already decompiled with Yarn $YARN_MAPPINGS + Vineflower $VINEFLOWER_VERSION"
        echo "  $file_count .java files in $OUTPUT_DIR"
        echo "  To force re-decompile, delete $DECOMPILE_INFO"
        exit 0
    fi
fi

# ---------------------------------------------------------------------------
# Download Vineflower if needed
# ---------------------------------------------------------------------------

if [[ ! -f "$VINEFLOWER_JAR" ]]; then
    echo ""
    echo "Downloading Vineflower ${VINEFLOWER_VERSION}..."
    mkdir -p "$TOOLS_DIR"
    curl -fSL -o "$VINEFLOWER_JAR" "$VINEFLOWER_URL"
    echo "Saved to $VINEFLOWER_JAR"
fi

# ---------------------------------------------------------------------------
# Clean and decompile
# ---------------------------------------------------------------------------

echo ""
echo "Cleaning $OUTPUT_DIR..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

echo "Decompiling (this may take a minute)..."
java -jar "$VINEFLOWER_JAR" \
    -dgs=1 \
    -asc=1 \
    -rsy=1 \
    -ind="    " \
    "$MAPPED_JAR" "$OUTPUT_DIR"

# ---------------------------------------------------------------------------
# Write metadata
# ---------------------------------------------------------------------------

file_count="$(find "$OUTPUT_DIR" -name '*.java' | wc -l | tr -d '[:space:]')"

cat > "$DECOMPILE_INFO" <<EOF
yarn_mappings=$YARN_MAPPINGS
vineflower_version=$VINEFLOWER_VERSION
minecraft_version=$MINECRAFT_VERSION
mod_source=$MOD_NAME
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
file_count=$file_count
EOF

echo ""
echo "Done! Decompiled $file_count .java files to $OUTPUT_DIR"
