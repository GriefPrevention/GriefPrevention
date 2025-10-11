# Contributing Guidelines

Thank you for considering contributing to GriefPrevention! This document outlines the guidelines for contributing to this repository.

## Branches

-   **legacy/v16** branch: This branch will only accept bugfixes for the legacy version 16 of GriefPrevention. Small features may also be considered, but it's recommended you help implement those into master.
-   **master** branch: This branch follows the roadmap. Please refer to the [roadmap](https://github.com/orgs/TechFortress/projects/6) for more information. You may propose additional features, but the roadmap takes precedence. Discussion of the roadmap can be found in this [discussion thread.](https://github.com/TechFortress/GriefPrevention/discussions/2065)

## Pull Requests

<!--- Please ensure that your pull request adheres to the [pull request template](https://github.com/TechFortress/GriefPrevention/blob/master/.github/PULL_REQUEST_TEMPLATE.md).-->

-   Please ensure that your pull request has a descriptive title and summary.
-   Please ensure that your pull request is tested and does not break any existing functionality.
-   Please ensure that your code is formatted. (see below)

Thank you for your contribution!

## Code Formatting (Spotless)

This project uses [Spotless](https://github.com/diffplug/spotless) to keep the codebase consistently formatted.

-   Before opening a pull request, run:

```bash
mvn spotless:apply
```

-   Optional: install a Git pre-push hook to automatically run Spotless on push:

```bash
mvn spotless:install-git-pre-push-hook
```
