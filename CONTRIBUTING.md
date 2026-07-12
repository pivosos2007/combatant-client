# Contributing to Combatant

Contributions to Combatant are welcome.

I (primary maintainer) will have limited availability because of military service. Pull requests may take time to review, and no fixed review or merge schedule is guaranteed.

Submit changes only when they are complete enough to review without requiring the maintainer to redesign, rewrite, or finish the implementation.

## Project direction

Combatant is intended to remain a general-purpose utility and PvP client.

New functionality should provide value beyond one private setup. Features designed only for a particular server, network, map, minigame, or private configuration are generally discouraged.

This does not mean every contribution has to be large or deeply technical. Small fixes, visual polish, UI refinements, documentation, localization, diagnostics, and cleanup can be valuable when they improve the project for regular users or addon authors.

Existing specialized functionality should not be treated as a precedent for adding more narrowly scoped systems.

A feature that is too specific for the core client is usually better implemented as an addon.

## Anticheat-specific contributions

Contributions targeting a specific anticheat are allowed.

They should:

- solve a real and clearly described problem;
- be useful beyond one particular server;
- avoid unnecessary server-specific hardcoding;
- identify the affected anticheat and relevant version or configuration when known;
- explain the conditions under which the change was tested;
- include practical in-game proof.

A claim that a change bypasses or improves compatibility with an anticheat is not sufficient without evidence.

Acceptable proof may include a video, GIF, screenshots, logs, test results, or another form of evidence appropriate to the behavior being changed.

## Useful contributions

Useful contributions include:

- bug and crash fixes;
- correctness and reliability improvements;
- performance improvements;
- compatibility updates;
- improvements to existing features;
- visual polish and UI refinements;
- broadly useful new features;
- addon API improvements;
- documentation;
- localization fixes;
- reproducible test cases and diagnostics.

Changes should solve an actual problem rather than add infrastructure for hypothetical future use.

## Before submitting

Before opening a pull request:

1. Check existing issues and pull requests for related work.
2. Keep the change focused on one problem or feature.
3. Verify that the change fits the project direction.
4. Test the affected behavior.
5. Prepare evidence that the change works.
6. Review the licensing and attribution requirements.
7. Remove temporary debugging code, unused files, and unrelated formatting changes.

For large changes, open an issue or draft pull request first and describe the intended result.

## Code quality

Submitted code should:

- follow the existing project structure and conventions;
- use clear names;
- avoid unnecessary abstraction;
- avoid duplicating existing utilities;
- handle unavailable game state safely;
- avoid silent exception swallowing;
- release owned resources correctly;
- avoid unnecessary work in frequently executed paths;
- remain understandable without excessive comments;
- include comments where they explain non-obvious behavior or constraints.

Do not include unrelated refactors or broad formatting changes.

Do not replace working project conventions solely with personal style preferences.

## Scope

Prefer small and reviewable pull requests.

A pull request should not combine unrelated changes such as:

- a new feature and an unrelated subsystem rewrite;
- a bug fix and widespread formatting;
- dependency updates and unrelated feature work;
- API changes and unrelated internal cleanup.

Large changes should be split into logical commits or separate pull requests where possible.

## New features

A new feature should:

- have a clear purpose;
- provide value beyond one private setup;
- fit the existing project direction;
- avoid unnecessary server-specific assumptions;
- expose only meaningful settings;
- interact safely with related features;
- include localization entries where required;
- include a concise description of expected behavior.

A large number of settings is not a substitute for a coherent design.

Highly specialized functionality should normally be implemented as an addon.

## Dependencies

New dependencies should be avoided unless they provide substantial value that cannot reasonably be implemented with the existing stack.

A dependency proposal should explain:

- why it is needed;
- its license;
- its maintenance status;
- its runtime and distribution impact;
- whether it introduces native binaries;
- why a smaller alternative is insufficient.

Do not add repositories or dependencies from untrusted or unstable sources.

## Licensing and attribution

Only submit code and assets that can legally be redistributed by the project.

Do not submit:

- proprietary or leaked source code;
- code copied from projects with incompatible or unknown licensing;
- unattributed copied implementations;
- decompiled code without clear redistribution rights;
- assets without redistribution permission;
- unexplained binaries or generated blobs.

When code is derived from, adapted from, or closely based on another project:

1. Identify the original project.
2. Provide the original source link when available.
3. State which files or systems are affected.
4. Preserve the required copyright and license notices.
5. Update `CREDITS.md` and `THIRD_PARTY_NOTICES.md` when necessary.
6. Include the relevant license text if it is not already present.

Significant design or architectural inspiration should also be disclosed when the implementation closely follows another project.

Do not remove existing attribution.

By submitting a contribution, you confirm that you have the right to provide it under the license applicable to the affected part of Combatant.

## Testing and proof

Every pull request must explain how the change was tested.

When the result can be demonstrated in-game, include evidence such as:

- screenshots;
- a short video or GIF;
- before-and-after comparisons;
- logs or profiler output when appropriate.

The evidence should show the actual implementation running in Combatant.

Mockups, edited images, isolated previews, or screenshots from another client do not prove that the submitted change works in the project.

Bug fixes should demonstrate the original problem and the corrected result whenever reasonably possible.

Performance claims require measurements rather than visual impressions.

If in-game proof is genuinely irrelevant or impossible, explain why and provide the closest practical verification.

## Pull request description

A pull request should explain:

- what was changed;
- why the change is useful;
- how the previous behavior was incorrect or insufficient;
- how the new behavior works;
- how the change was tested;
- where the proof can be found;
- any known limitations;
- whether configuration formats or public APIs changed;
- whether third-party code, assets, or design references were used.

Do not rely only on statements such as “tested,” “fixed,” or “works for me.”

## Review

A contribution may be rejected when it:

- is too narrowly server- or mode-specific;
- duplicates an existing feature;
- adds disproportionate complexity;
- lacks clear user value;
- introduces an unsuitable dependency;
- has unresolved licensing concerns;
- is difficult to maintain;
- does not fit the project direction;
- is incomplete or insufficiently tested;
- does not include reasonable proof;
- bundles unrelated changes;
- would be more appropriate as an addon.

Approval is not guaranteed solely because a contribution works technically.

## Conduct

Keep technical discussion focused on the code and the problem being solved.

Disagreement is acceptable. Personal attacks, harassment, spam, and intentionally disruptive behavior are not.
