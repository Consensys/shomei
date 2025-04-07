#!/bin/bash

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export TEST_PATH=$BASE_DIR/tests
export REPORTS_DIR=$BASE_DIR/reports
export GOSS_PATH="$TEST_PATH/goss-linux-amd64"
export GOSS_OPTS="$GOSS_OPTS --format junit"
export GOSS_FILES_STRATEGY=cp
DOCKER_TEST_IMAGE="shomei_goss"
DOCKER_IMAGE=$1
DOCKER_FILE="${2:-$BASE_DIR/openjdk-21/Dockerfile}"

# Create the reports directory if it doesn't exist
mkdir -p "$REPORTS_DIR"

# # Create test Docker image
TEST_CONTAINER_ID=$(docker create "$DOCKER_IMAGE")
docker commit "$TEST_CONTAINER_ID" "$DOCKER_TEST_IMAGE"

# Initialize the exit code
i=0

# # Checks on the Dockerfile
GOSS_FILES_PATH=$TEST_PATH/00 \
bash $TEST_PATH/dgoss \
dockerfile $DOCKER_IMAGE $DOCKER_FILE \
> "$REPORTS_DIR/00.xml" || i=$((i + 1))
# fail fast if we dont pass static checks
if [[ $i != 0 ]]; then exit $i; fi

# Test for normal startup with ports opened
# we test that things listen on the right interface/port, not what interface the advertise
GOSS_FILES_PATH=$TEST_PATH/01 \
bash $TEST_PATH/dgoss \
run $DOCKER_IMAGE --rpc-http-host="0.0.0.0" --data-path="/opt/shomei/database" \
> "$REPORTS_DIR/01.xml" || i=$((i + 1))

# Remove the test Docker image
docker image rm "$DOCKER_TEST_IMAGE"

echo "test.sh Exit code: $i"
exit $i
