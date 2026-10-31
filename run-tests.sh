#!/bin/bash
# Shell script to run all tests for the date-parser project
# This script sets up the environment and runs Maven tests

set -e

# Configuration
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOMEDefault="/home/juergens/.jdks/azul-18"
M2_HOMEDefault="/tmp/apache-maven-3.8.6"
M2_URL="https://archive.apache.org/dist/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.tar.gz"
M2_TAR="$(basename "$M2_URL")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print error and exit
fail() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

# Function to print info
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

# Function to print warning
warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Function to show help
show_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -h, --help        Show this help message"
    echo "  -m, --module M    Run tests for specific module (e.g., date-rule-combinators)"
    echo "  -c, --clean       Run 'clean' before tests"
    echo "  -o, --offline     Run Maven in offline mode"
    echo "  -s, --skip        Skip tests (just compile)"
    echo ""
    echo "Examples:"
    echo "  $0                          Run all tests"
    echo "  $0 -m date-rule-combinators  Run tests for date-rule-combinators only"
    echo "  $0 -c                        Clean and run all tests"
    echo ""
    exit 0
}

# Parse command line arguments
TARGET_MODULE=""
SKIP_TESTS=false
CLEAN=false
OFFLINE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            show_help
            ;;
        -m|--module)
            TARGET_MODULE="$2"
            shift
            ;;
        -c|--clean)
            CLEAN=true
            ;;
        -o|--offline)
            OFFLINE=true
            ;;
        -s|--skip)
            SKIP_TESTS=true
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            ;;
    esac
    shift
done

echo "=========================================="
echo "  Date Parser - Test Runner"
echo "=========================================="
echo ""

# Check and setup Java
if [ -z "$JAVA_HOME" ]; then
    if command_exists java; then
        JAVA_HOME="$(readlink -f "$(which java)" | sed 's:/bin/java::')"
    elif [ -d "$JAVA_HOMEDefault" ]; then
        JAVA_HOME="$JAVA_HOMEDefault"
    else
        fail "Java not found. Please set JAVA_HOME or install Java 17+"
    fi
fi

if [ ! -d "$JAVA_HOME" ]; then
    fail "JAVA_HOME directory not found: $JAVA_HOME"
fi

JAVA_BIN="$JAVA_HOME/bin"
if [ ! -x "$JAVA_BIN/java" ]; then
    fail "Java executable not found in $JAVA_BIN"
fi

info "Using Java from: $JAVA_HOME"
JAVA_VERSION=$("$JAVA_BIN/java" -version 2>&1 | head -1)
info "Java version: $JAVA_VERSION"

# Setup Maven
if [ -z "$M2_HOME" ]; then
    M2_HOME="$M2_HOMEDefault"
fi

# Download and extract Maven if needed
if [ ! -d "$M2_HOME" ]; then
    info "Maven not found at $M2_HOME, downloading..."
    
    # Create temp directory
    TMP_DIR=$(mktemp -d)
    trap "rm -rf $TMP_DIR" EXIT
    
    # Download Maven
    if command_exists curl; then
        curl -sS -o "$TMP_DIR/$M2_TAR" "$M2_URL" || fail "Failed to download Maven from $M2_URL"
    elif command_exists wget; then
        wget -q -O "$TMP_DIR/$M2_TAR" "$M2_URL" || fail "Failed to download Maven from $M2_URL"
    else
        fail "Neither curl nor wget found. Please install one of them."
    fi
    
    # Extract Maven
    mkdir -p "$M2_HOME"
    tar xzf "$TMP_DIR/$M2_TAR" -C "$(dirname "$M2_HOME")" --strip-components=1 || \
        fail "Failed to extract Maven"
    
    info "Maven downloaded and extracted to $M2_HOME"
fi

if [ ! -x "$M2_HOME/bin/mvn" ]; then
    fail "Maven executable not found in $M2_HOME/bin"
fi

info "Using Maven from: $M2_HOME"

# Setup PATH
export PATH="$M2_HOME/bin:$JAVA_HOME/bin:$PATH"

# Change to project directory
cd "$PROJECT_DIR"
info "Project directory: $PROJECT_DIR"

# Build Maven command
MAVEN_CMD="mvn"

if [ "$OFFLINE" = true ]; then
    MAVEN_CMD="$MAVEN_CMD -o"
fi

if [ "$CLEAN" = true ]; then
    MAVEN_CMD="$MAVEN_CMD clean"
fi

if [ "$SKIP_TESTS" = true ]; then
    MAVEN_CMD="$MAVEN_CMD compile"
else
    MAVEN_CMD="$MAVEN_CMD test"
fi

if [ -n "$TARGET_MODULE" ]; then
    MAVEN_CMD="$MAVEN_CMD -pl $TARGET_MODULE"
fi

# Check if .mvn directory exists and needs to be temporarily moved
RENAMED_MVN=false
if [ -d ".mvn" ]; then
    # Temporarily rename .mvn to avoid settings.xml path resolution issues
    mv .mvn .mvn.bak 2>/dev/null || true
    RENAMED_MVN=true
fi

# Function to cleanup on exit
cleanup() {
    if [ "$RENAMED_MVN" = true ]; then
        mv .mvn.bak .mvn 2>/dev/null || true
    fi
}

# Register cleanup function
trap cleanup EXIT

info "Running: $MAVEN_CMD"
info "="
echo ""

# Execute Maven command
$MAVEN_CMD

RESULT=$?
echo ""

if [ $RESULT -eq 0 ]; then
    info "All tests passed!"
else
    # Cleanup before exit
    cleanup
    fail "Tests failed with exit code $RESULT"
fi

exit 0
