# CI/CD Setup Instructions

## 1. Configure GitHub Secrets

Go to: Repository Settings → Secrets and variables → Actions → New repository secret

Add the following secrets:

- `CERTIFICATE_CHAIN` - JetBrains plugin signing certificate chain
- `PRIVATE_KEY` - JetBrains plugin signing private key
- `PRIVATE_KEY_PASSWORD` - Password for private key
- `PUBLISH_TOKEN` - JetBrains Marketplace API token

## 2. Create Production Environment

Go to: Repository Settings → Environments → New environment

Name: `production`

Protection rules:
- ✓ Required reviewers
  - Add: [your GitHub username or team]
- ✓ Deployment branches
  - Selected branches: `main`

## 3. Enable GitHub Actions

Go to: Repository Settings → Actions → General

Workflow permissions:
- ✓ Read and write permissions
- ✓ Allow GitHub Actions to create and approve pull requests

## 4. Test CI Workflow

```bash
# Create test branch
git checkout -b test/ci-pipeline
git push -u origin test/ci-pipeline

# Check Actions tab - CI should run automatically
```

## 5. Verify Secrets Work

Option A: Test with dry-run publish workflow
- Do NOT approve at approval gate

Option B: Check secrets in test workflow:
```yaml
# Create .github/workflows/test-secrets.yml temporarily
name: Test Secrets
on: workflow_dispatch
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - name: Check secrets
        run: |
          [[ -n "${{ secrets.CERTIFICATE_CHAIN }}" ]] && echo "✓ CERTIFICATE_CHAIN"
          [[ -n "${{ secrets.PRIVATE_KEY }}" ]] && echo "✓ PRIVATE_KEY"
          [[ -n "${{ secrets.PRIVATE_KEY_PASSWORD }}" ]] && echo "✓ PRIVATE_KEY_PASSWORD"
          [[ -n "${{ secrets.PUBLISH_TOKEN }}" ]] && echo "✓ PUBLISH_TOKEN"
```

Delete this test workflow after verification.
