# Branch Protection Setup

This repository enforces branch protection rules to ensure code quality and prevent direct commits to the `main` branch.

## Branch Protection Rules

### Main Branch Protection

The `main` branch is protected with the following rules:

- ✅ **Require pull request** before merging (no reviews required for solo contributors)
- ✅ **Require status checks** to pass before merging
- ✅ **Require branches to be up to date** before merging
- ✅ **Do not allow bypassing the above settings**
- ✅ **Include administrators** (even admins must use PRs)
- ❌ **Allow force pushes**: Disabled
- ❌ **Allow deletions**: Disabled
- ⚠️ **PR Reviews**: Not required (solo contributor setup)

## Setting Up Branch Protection

### Option 1: GitHub Web UI

1. Go to your repository on GitHub
2. Click **Settings** → **Branches**
3. Under **Branch protection rules**, click **Add rule**
4. Configure the following:
   - **Branch name pattern**: `main`
   - **Require a pull request before merging**: ✅
     - **Do NOT enable "Require approvals"** (solo contributor - reviews skipped)
   - **Require status checks to pass before merging**: ✅
     - **Require branches to be up to date before merging**: ✅
   - **Restrict who can push to matching branches**: Leave empty (or add specific teams)
   - **Do not allow bypassing the above settings**: ✅
   - **Include administrators**: ✅
5. Click **Create** or **Save changes**

### Option 2: GitHub CLI

If you have GitHub CLI installed:

```bash
gh api repos/:owner/:repo/branches/main/protection \
  --method PUT \
  --field required_status_checks='{"strict":true,"contexts":[]}' \
  --field enforce_admins=true \
  --field required_pull_request_reviews=null \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false
```

Replace `:owner` and `:repo` with your GitHub username and repository name.

### Option 3: GitHub API (curl)

```bash
curl -X PUT \
  -H "Authorization: token YOUR_GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/OWNER/REPO/branches/main/protection \
  -d '{
    "required_status_checks": {
      "strict": true,
      "contexts": []
    },
    "enforce_admins": true,
    "required_pull_request_reviews": null,
    "restrictions": null,
    "allow_force_pushes": false,
    "allow_deletions": false
  }'
```

## Workflow

### Creating a Feature Branch

```bash
# Create and switch to a new branch
git checkout -b feature/your-feature-name

# Or for bug fixes
git checkout -b fix/your-bug-fix

# Make your changes and commit
git add .
git commit -m "Your commit message"

# Push to GitHub
git push origin feature/your-feature-name
```

### Creating a Pull Request

1. Go to your repository on GitHub
2. Click **Pull requests** → **New pull request**
3. Select your feature branch as the source and `main` as the destination
4. Fill in the PR description
5. Click **Create pull request**

### Merging a Pull Request

1. Wait for any required status checks to pass
2. Click **Merge pull request** (no approval needed for solo contributors)
3. Optionally delete the feature branch after merging

## Benefits

- ✅ **Code Review**: All changes are reviewed before merging
- ✅ **Quality Assurance**: Catches bugs and issues before they reach main
- ✅ **History**: Better commit history and documentation
- ✅ **Rollback**: Easier to identify and revert problematic changes
- ✅ **CI/CD**: Ensures all automated tests pass before merging

## Troubleshooting

### "Cannot push to main"

If you see this error, it means branch protection is working! Create a feature branch instead:

```bash
git checkout -b feature/your-changes
git push origin feature/your-changes
```

### "Required status checks not passing"

- Check the **Actions** tab to see which checks are failing
- Fix any issues in your branch
- Push new commits to update the PR

### "Needs approval"

- Not applicable - PR reviews are disabled for solo contributors
- You can merge PRs directly after CI checks pass

