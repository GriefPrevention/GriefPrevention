# Contributing Guidelines

Thank you for considering contributing to GriefPrevention! This document outlines the guidelines for contributing to this repository.

## Branches

- **legacy/v16** branch: This branch will only accept bugfixes for the legacy version 16 of GriefPrevention. Small features may also be considered, but it's recommended you help implement those into master.
- **master** branch: This branch follows the roadmap. Please refer to the [roadmap](https://github.com/orgs/TechFortress/projects/6) for more information. You may propose additional features, but the roadmap takes precedence. Discussion of the roadmap can be found in this [discussion thread.](https://github.com/TechFortress/GriefPrevention/discussions/2065)

## Pull Requests

<!--- Please ensure that your pull request adheres to the [pull request template](https://github.com/TechFortress/GriefPrevention/blob/master/.github/PULL_REQUEST_TEMPLATE.md).-->
- Title your pull request to match the style of our commit messages.
- Include a descriptive summary of the changes made in your pull request and its rationale.
- Test your pull request; it should not break any existing functionality.

Thank you for your contribution!

## AI

- [Meat proxies](https://nomeatproxy.com/) are disallowed. Please type your own comments.
- AI must follow all instructions in [AGENTS.md](AGENTS.md). Instructing your AI agent to follow the [AGENTS.md](AGENTS.md) (along with you verifying that it does) helps your agent write better, simpler, readable, and more maintainable code; but most importantly, it **records the original intent** for changing code for future understanding and debugging.

From https://gruhn.me/blog/2026-08-03/

> Reading AI output is extra effort. It's verbose, frequently contains all too plausible nonsense, and is increasingly jargon dense.

> By all means, prompt AI. But don't just relay the output. Read it, understand it, validate it, and then write a response in your own words (a decent certificate that you've done the prior steps). Making that effort is value you can add.
