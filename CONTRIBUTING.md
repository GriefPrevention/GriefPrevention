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

### AI

- All commits that use AI (or contain sections of AI-generated code) must be attributed (ideally with model and method used) in the commit message.
- All comments that contain text from AI must be in block quotes, as if citing a source.
    - This helps us understand which is your thoughts vs. the AI's output.
    - Comments typed by you, the human, **greatly** assists in communication and clarity, especially when communicating rationale and design decisions. AI-written comments are generally not as concise, and takes longer to review due to the additional overhead of trying to understand the AI's rationale.

> start a line with the right angle bracket `>` to create a block quote

```md
> use the right angle bracket `>` to create a block quote
```

Thank you for your contribution!
