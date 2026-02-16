#!/bin/bash
set -e

CURRENT_VERSION=$1

if [ -z "$CURRENT_VERSION" ]; then
  echo "Error: Current version argument required"
  echo "Usage: $0 <current-version>"
  exit 1
fi

# Remove -SNAPSHOT if present
BASE_VERSION=${CURRENT_VERSION%-SNAPSHOT}

# Validate format
if ! [[ $BASE_VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: Invalid version format. Expected X.Y.Z"
  exit 1
fi

# Split version into components
IFS='.' read -r major minor patch <<< "$BASE_VERSION"

# Increment patch version
patch=$((patch + 1))

# Output next snapshot version
echo "$major.$minor.$patch-SNAPSHOT"
