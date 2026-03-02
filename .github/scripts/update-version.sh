#!/bin/bash
set -e

VERSION=$1

if [ -z "$VERSION" ]; then
  echo "Error: Version argument required"
  echo "Usage: $0 <version>"
  exit 1
fi

# Validate semantic versioning format
if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9._-]+)?$ ]]; then
  echo "Error: Invalid version format. Expected X.Y.Z or X.Y.Z-suffix"
  exit 1
fi

# Update gradle.properties
if [ ! -f "gradle.properties" ]; then
  echo "Error: gradle.properties not found"
  exit 1
fi

# Update the pluginVersion line
sed -i.bak "s/^pluginVersion = .*/pluginVersion = $VERSION/" gradle.properties
rm -f gradle.properties.bak

echo "Updated version to $VERSION in gradle.properties"
