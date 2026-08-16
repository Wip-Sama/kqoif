# Release Guide

This document outlines the versioning policy, release process, and automated release workflow for **kqoif**.

---

## 1. Versioning Policy

**kqoif** adheres strictly to [Semantic Versioning 2.0.0 (SemVer)](https://semver.org/):

$$\text{v}\langle\text{MAJOR}\rangle.\langle\text{MINOR}\rangle.\langle\text{PATCH}\rangle$$

- **MAJOR** (`vX.0.0`): Incompatible API changes or breaking binary encoding modifications.
- **MINOR** (`v0.X.0`): Backwards-compatible new features (e.g. new CLI subcommands, new format adapters).
- **PATCH** (`v0.0.X`): Backwards-compatible bug fixes, optimizations, and documentation updates.

---

## 2. Release Prerequisites & What Is Allowed

Before tagging a release, ensure:
1. **Branch Hygiene:** All features and fixes are merged into `master`.
2. **Clean Tests:** `./gradlew check` passes locally on all modules (`kqoif-core`, `kqoif-imageio`, `kqoif-cli`).
3. **Commit Standards:** Use Conventional Commits (e.g. `feat: ...`, `fix: ...`, `docs: ...`) so GitHub can generate clean, structured release notes automatically.

---

## 3. How to Trigger an Automated Release

Releases are completely automated via GitHub Actions triggered by pushing a SemVer git tag:

### Step 1: Create and Push a Tag
```bash
# Ensure you are on main and up to date
git checkout main
git pull origin main

# Create annotated tag (e.g. v1.0.0)
git tag -a v1.0.0 -m "Release v1.0.0"

# Push the tag to GitHub
git push origin v1.0.0
```

### Step 2: Automated Pipeline Execution
Once the tag is pushed, the [Release Workflow](file:///.github/workflows/release.yml) automatically:
1. Validates the test suite across all modules.
2. Builds distribution archives (`.zip`, `.tar`) and module JARs.
3. Automatically generates release notes and a categorized changelog based on commits and pull requests since the previous tag.
4. Publishes a GitHub Release with attached downloadable packages.

---

## 4. Changelog Generation

GitHub Actions uses `generate_release_notes: true` via GitHub's Release Notes API:
- Merged PR titles and commits will be automatically sorted into categories:
  - 🚀 **Features** (`feat`)
  - 🐛 **Bug Fixes** (`fix`)
  - 📝 **Documentation** (`docs`)
  - 🔨 **Maintenance & Refactoring** (`refactor`, `chore`)
- Contributors are automatically credited.
