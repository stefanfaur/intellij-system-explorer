# CI/CD Testing Guide

## Local Testing

### Test Scripts Locally

```bash
# Test version update
./.github/scripts/update-version.sh 1.0.1
git diff gradle.properties
git restore gradle.properties

# Test changelog extraction
./.github/scripts/extract-changelog.sh 1.0.0 /tmp/changelog.md
cat /tmp/changelog.md

# Test next version calculation
./.github/scripts/next-version.sh 1.0.0
# Expected: 1.0.1-SNAPSHOT
```

### Test Gradle Tasks

```bash
# Run tests locally (same as CI)
./gradlew test
./gradlew testIdeUi --tests "ro.faur.explorer.light.*"
./gradlew testIdeUi --tests "ro.faur.explorer.heavy.*"

# UI tests (requires display)
./gradlew testUi

# Verification
./gradlew verifyPlugin
./gradlew runPluginVerifier

# Build
./gradlew buildPlugin
```

## CI Workflow Testing

### Test on Feature Branch

1. Create test branch:
   ```bash
   git checkout -b test/ci-workflow
   ```

2. Push to trigger CI:
   ```bash
   git push -u origin test/ci-workflow
   ```

3. Observe CI workflow in GitHub Actions

4. Expected: Tests run, but no snapshot build (not main branch)

### Test on Main Branch

1. Merge to main (or push directly)
2. Observe CI workflow
3. Expected: Tests + verification + snapshot build
4. Check artifacts in GitHub Actions UI

## Publish Workflow Testing

### Dry Run (without actual publish)

1. Prepare CHANGELOG.md:
   ```markdown
   ## [1.0.1] - 2026-02-16
   ### Fixed
   - Test release
   ```

2. Manually trigger workflow:
   - Go to Actions → Publish Release
   - Click "Run workflow"
   - Enter version: `1.0.1`

3. Workflow will:
   - Run all validations ✓
   - Run all tests ✓
   - Wait for approval ⏸️

4. Do NOT approve - cancel instead to avoid actual publish

### Full Release Test (with dummy secrets)

⚠️ Only do this if you have test/staging secrets configured

1. Configure test secrets in repository settings
2. Follow dry run steps
3. Approve at approval gate
4. Verify version commits and tag creation
5. Check marketplace for plugin update

## Troubleshooting

### CI Fails on Test

- Check test reports in artifacts
- Run tests locally: `./gradlew test --info`

### Verifier Fails

- Download verifier reports from artifacts
- Check IDE compatibility issues
- Update `pluginSinceBuild`/`pluginUntilBuild` if needed

### Publish Fails at Marketplace

- Check `PUBLISH_TOKEN` secret is valid
- Verify plugin metadata in `gradle.properties`
- Check JetBrains Marketplace status page

### Version Already Exists Error

- Check existing git tags: `git tag -l`
- Choose different version number
- Or delete tag: `git tag -d v1.0.1 && git push origin :v1.0.1`
