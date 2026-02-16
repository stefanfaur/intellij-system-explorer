#!/bin/bash
set -e

VERSION=$1
OUTPUT_FILE=${2:-"changelog-entry.md"}

if [ -z "$VERSION" ]; then
  echo "Error: Version argument required"
  echo "Usage: $0 <version> [output-file]"
  exit 1
fi

if [ ! -f "CHANGELOG.md" ]; then
  echo "Error: CHANGELOG.md not found"
  exit 1
fi

# Extract content between ## [VERSION] and next ##
# Using awk for reliable extraction
awk -v version="$VERSION" '
  /^## \['"$VERSION"'\]/ { found=1; next }
  found && /^## / { exit }
  found { print }
' CHANGELOG.md > "$OUTPUT_FILE"

# Check if extraction was successful
if [ ! -s "$OUTPUT_FILE" ]; then
  echo "Error: No changelog entry found for version $VERSION"
  exit 1
fi

echo "Extracted changelog for version $VERSION to $OUTPUT_FILE"
cat "$OUTPUT_FILE"
