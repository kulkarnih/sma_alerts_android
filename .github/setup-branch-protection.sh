#!/bin/bash

# Script to set up branch protection for main branch
# Requires GitHub CLI (gh) to be installed and authenticated

set -e

echo "🔒 Setting up branch protection for main branch..."

# Get repository info
REPO=$(git config --get remote.origin.url | sed -E 's/.*github.com[:/]([^/]+\/[^/]+)(\.git)?$/\1/')

if [ -z "$REPO" ]; then
    echo "❌ Error: Could not determine repository name"
    echo "Make sure you're in a git repository with a GitHub remote"
    exit 1
fi

echo "📦 Repository: $REPO"

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ Error: GitHub CLI (gh) is not installed"
    echo "Install it from: https://cli.github.com/"
    echo ""
    echo "Or set up branch protection manually via GitHub UI:"
    echo "1. Go to Settings → Branches"
    echo "2. Add branch protection rule for 'main'"
    echo "3. Enable 'Require pull request reviews before merging'"
    echo "4. Enable 'Include administrators'"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Error: Not authenticated with GitHub CLI"
    echo "Run: gh auth login"
    exit 1
fi

echo "✅ GitHub CLI is installed and authenticated"

# Set up branch protection
echo ""
echo "🛡️  Configuring branch protection rules..."

gh api repos/$REPO/branches/main/protection \
  --method PUT \
  --field required_status_checks='{"strict":true,"contexts":[]}' \
  --field enforce_admins=true \
  --field required_pull_request_reviews=null \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false

echo ""
echo "✅ Branch protection rules configured successfully!"
echo ""
echo "📋 Rules applied:"
echo "   - ✅ Require pull request before merging (no reviews required)"
echo "   - ✅ Require branches to be up to date"
echo "   - ✅ Include administrators"
echo "   - ❌ Force pushes disabled"
echo "   - ❌ Branch deletions disabled"
echo "   - ℹ️  PR reviews skipped (solo contributor)"
echo ""
echo "🔗 View settings: https://github.com/$REPO/settings/branches"

