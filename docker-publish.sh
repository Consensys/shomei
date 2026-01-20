#!/usr/bin/env bash
set -euo pipefail

### CONFIG ###########################################################

IMAGE_NAME="matkt/linea-shomei"
DOCKERFILE="docker/openjdk-21/Dockerfile"
DIST_DIR="build/distributions"

# Multi-architecture platforms (same as the CI workflow)
PLATFORMS="linux/amd64,linux/arm64"

# Name of the buildx builder
BUILDX_BUILDER_NAME="shomei-builder"

######################################################################

log() {
  echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] $*"
}

### 1. Pre-flight checks #############################################

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found in PATH" >&2
  exit 1
fi

if ! command -v ./gradlew >/dev/null 2>&1; then
  echo "gradlew not found at project root" >&2
  exit 1
fi

### 2. Retrieve project version #####################################

log "Retrieving project version from Gradle…"

# Fetch the 'version' property from Gradle
VERSION=$(
  ./gradlew -q properties \
    | grep "^version:" \
    | awk '{print $2}'
)

if [[ -z "${VERSION:-}" ]]; then
  echo "Unable to determine project version from Gradle." >&2
  exit 1
fi

log "Detected version: ${VERSION}"

TAR_FILE="${DIST_DIR}/shomei-${VERSION}.tar.gz"

### 3. Build project distributions ##################################

log "Building project distributions (assemble)…"
./gradlew --no-daemon clean assemble

if [[ ! -f "${TAR_FILE}" ]]; then
  echo "Expected archive not found: ${TAR_FILE}" >&2
  echo "Check that the distribution filename matches what Gradle generates." >&2
  exit 1
fi

log "Archive found: ${TAR_FILE}"

### 4. Build metadata ################################################

BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
VCS_REF=$(git rev-parse --short HEAD || echo "unknown")

log "BUILD_DATE=${BUILD_DATE}"
log "VCS_REF=${VCS_REF}"

### 5. Configure Docker Buildx ######################################

log "Setting up Docker Buildx (builder: ${BUILDX_BUILDER_NAME})…"

if ! docker buildx inspect "${BUILDX_BUILDER_NAME}" >/dev/null 2>&1; then
  log "Builder not found, creating it…"
  docker buildx create --name "${BUILDX_BUILDER_NAME}" --use
else
  log "Builder already exists, using it…"
  docker buildx use "${BUILDX_BUILDER_NAME}"
fi

log "Builder details:"
docker buildx inspect "${BUILDX_BUILDER_NAME}"

### 6. Multi-arch build & push ######################################

log "Building multi-architecture images and pushing to registry…"

# Tags same as CI
TAG_VERSION="${IMAGE_NAME}:${VERSION}"
TAG_LATEST="${IMAGE_NAME}:latest"

docker buildx build \
  --platform "${PLATFORMS}" \
  -f "${DOCKERFILE}" \
  --build-arg "TAR_FILE=${TAR_FILE}" \
  --build-arg "BUILD_DATE=${BUILD_DATE}" \
  --build-arg "VCS_REF=${VCS_REF}" \
  --build-arg "VERSION=${VERSION}" \
  -t "${TAG_VERSION}" \
  -t "${TAG_LATEST}" \
  --push \
  .

log "Build & push completed."
log "Pushed images:"
log "  - ${TAG_VERSION}"
log "  - ${TAG_LATEST}"

log "Multi-architecture Docker publish successfully completed."
