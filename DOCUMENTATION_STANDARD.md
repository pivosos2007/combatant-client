# Combatant Documentation Standard

**Version:** 1.1  
**Status:** Project policy  
**Applies to:** Java source documentation, implementation comments, and documentation-only review passes

## 1. Authority and scope

This document governs **what is worth documenting**, **how it must be written**, and **which existing comments must be removed** in Combatant. It is not a general Java formatting or refactoring policy.

Use these external references for Javadoc syntax and conventions:

- [Google Java Style Guide, §7 — Javadoc](https://google.github.io/styleguide/javaguide.html#s7-javadoc) — formatting, summary fragments, tags, and cases where Javadoc is optional.
- [Oracle — How to Write Doc Comments for the Javadoc Tool](https://www.oracle.com/java/technologies/javase/writing-doc-comments.html) — documentation as an API contract rather than an explanation of source code.
- [Oracle — Requirements for Writing Java API Specifications](https://www.oracle.com/java/technologies/javase/api-specifications.html) — observable behavior and testable requirements.
- [JDK 25 — Documentation Comment Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html) — supported Javadoc syntax and tags for the project's Java version.

**Precedence:** Combatant's rules below control comment content and necessity; Google and Oracle control conventions and Javadoc semantics where there is no conflict. Do not apply unrelated Google code-formatting rules as part of a documentation pass.

The keywords **MUST**, **MUST NOT**, **SHOULD**, and **MAY** denote requirements, prohibitions, strong recommendations, and optional practices, respectively.

## 2. Core principle: document the contract, not the implementation's appearance

A comment is useful only if it provides information that a competent developer cannot reliably obtain from a well-named declaration, its types, and the nearby code.

Prioritize, where applicable:

1. **Purpose and responsibility** — what role the component serves within the system.
2. **Observable behavior** — what callers can rely on, including important boundary conditions.
3. **Constraints and invariants** — what must remain true, including valid units, ranges, and coordinate spaces.
4. **Ownership and lifecycle** — who owns a resource, when it becomes valid, and who releases it.
5. **Concurrency** — thread affinity, synchronization requirements, publication guarantees, ordering, and cancellation.
6. **Failure semantics** — what errors mean and what state remains after failure.
7. **Non-obvious rationale** — why a necessary constraint or unusual choice exists, supported by code or an external contract.

A comment MUST NOT merely narrate the statements, intermediate values, branch structure, rendering steps, or visual appearance of the implementation. **Less accurate documentation is worse than no documentation.**

When deciding whether to add a comment, ask: **What concrete mistake would a maintainer or caller make without this information?** If there is no clear answer, omit the comment.

## 3. Strict prohibitions: synthetic explanations and AI-generated filler

The following content is **prohibited** in new documentation and MUST be deleted or rewritten when found in existing comments.

### 3.1 Unsupported claims of completeness, equivalence, and fidelity

Do not claim that code:

- "fully recreates", "faithfully reproduces", "perfectly matches", "replicates the original", or "implements the complete behavior";
- "exactly matches vanilla", "provides full parity", or "recreates the original experience";
- is "production-ready", "robust", "seamless", "highly optimized", or "zero-overhead" without a concrete, verified requirement or measurement.

These claims are not technical documentation. If behavioral compatibility is a real contractual requirement, state the **specific observable compatibility condition**, its scope, and, when relevant, its verification source. Never infer fidelity from a class name or implementation shape.

### 3.2 Unrequested denials, disclaimers, and defensive narration

Do not write comments such as:

- "This does not attempt to recreate the full vanilla pipeline."
- "This is intentionally not a complete implementation."
- "We deliberately avoid reproducing the original renderer."
- "This only provides a lightweight approximation."
- "Rather than trying to implement the complete system, we simply ..."
- "This is not intended to ..." when no caller-facing contract depends on that statement.

Do not invent a debate with an imagined previous implementation, claim deliberate intent that the source cannot establish, or justify what the code *does not* do without a concrete reason.

**Exception:** A negative statement is allowed when it defines a necessary, verifiable contract: for example, "Does not transfer ownership of the supplied texture" or "Must not be called after shutdown." The prohibition targets rhetorical self-justification, not real constraints.

### 3.3 Visual narration and implementation walkthroughs

Do not describe UI/rendering output as a tour of drawing operations or subjective effects:

- "Draws a beautiful rounded panel with smooth glow and soft shadows."
- "First renders the background, then draws the icons and labels."
- "Creates a fancy animated visual representation."
- "Uses a simple loop to update every vertex."

For rendering components, document **responsibility and contracts** instead: coordinate space, accepted inputs, clipping rules, ownership, pass requirements, ordering constraints, depth semantics, or output format—only when relevant and verified.

Do not add marketing adjectives, aesthetic praise, or speculative descriptions of what something “looks like.” User-facing visual behavior MAY be specified if it is itself the external API contract, rather than an incidental account of implementation.

### 3.4 Restatements, tautologies, and generic prose

Forbidden patterns include:

- `Gets the value.` / `Sets the value.` / `Returns true if enabled.` when the declaration already says exactly that.
- `Constructor for Foo.` / `Foo implementation.` / `Handles Foo-related operations.`
- `This class is responsible for managing various operations.`
- `Checks whether the condition is met.` / `Processes the given data.`
- Redundant `@param value the value`, `@return the result`, or tags that restate the parameter name.
- Empty Javadoc blocks, long introductions without concrete facts, duplicate parent-interface documentation, and line-by-line translations of code.

Generic wording such as "simple", "helper", "utility", "efficient", "flexible", "various", and "as needed" is not automatically invalid, but MUST be removed if it provides no specific technical meaning.

### 3.5 Fabricated intent and unverified guarantees

Never infer **why** something was written solely from **how** it is implemented. In particular, do not invent performance, security, synchronization, compatibility, thread-safety, or error-recovery guarantees.

Where purpose cannot be confirmed, document only observable facts—or leave the comment absent. Do not cover uncertainty with confident prose.

## 4. Where Javadoc is required

### 4.1 Public and extension API (`api/v0` and other supported extension surfaces)

Javadoc is REQUIRED for public API types and for members with a nontrivial contract. Document parameters, results, exceptions, lifecycle, and extension points as applicable. A genuinely self-explanatory member with no other contract may remain undocumented under Google's exception.

Document what external callers can depend on. Avoid internal execution details unless they create a binding requirement.

### 4.2 Internal classes and methods

Javadoc is REQUIRED when the role or contract is non-obvious, and SHOULD be present on central abstractions, managers, schedulers, ownership boundaries, and stateful runtime components.

Routine getters, setters, forwarding wrappers, obvious constructors, simple predicates, and trivial private methods SHOULD NOT receive comments merely to increase coverage.

### 4.3 Overridden methods

Inherited contracts MUST NOT be copied verbatim into overrides. Omit Javadoc when the inherited contract is sufficient. Document only a meaningful refinement or additional guarantee. Use `{@inheritDoc}` only if useful for a substantial extension, not as a mandatory placeholder.

### 4.4 Fields and constants

Document a field only when its meaning cannot be inferred, especially if it has unusual units, a sentinel value, lifecycle significance, thread-visibility requirements, or a non-obvious invariant. Do not comment every field or enum constant by default.

### 4.5 Implementation comments (`//` and `/* ... */`)

Use sparingly for a **local, non-obvious constraint or rationale** that belongs next to a specific statement. Do not replace a missing Javadoc contract with an implementation comment. Delete comments that merely summarize the next few lines.

## 5. Content and formatting

- Write source documentation in **technical English**. Be precise, concise, neutral, and factual. Use the terminology of the relevant subsystem.
- Use the existing traditional `/** ... */` Javadoc style in Combatant. Do not convert comments to `///` in a documentation-only pass merely because JDK 25 supports it.
- Start with a short summary that states the actual responsibility or behavior. Follow with additional detail **only if needed**.
- Prefer plain affirmative statements. Do not use first-person narration (`we`, `I`), conversational filler, historical anecdotes, or speculative assertions.
- Use `{@code ...}` for code literals and `{@link ...}` or `@see` for useful Java-symbol references. Links MUST resolve to the intended declaration.
- Use `@param` for meaningful constraints (including nullability, permitted range, units, or ownership); `@return` for non-obvious semantics, special results, and nullable outcomes; `@throws` for exceptions and their trigger conditions. Follow Javadoc rules for required tags in published API.
- Use `@since` for externally versioned APIs only where the introduced version is known. Use `@deprecated` with the supported replacement or migration instruction where one exists. Do not invent version numbers or replacement APIs.
- Never include empty, boilerplate, contradictory, or unverifiable tags.
- State units and coordinate spaces explicitly when ambiguous: pixels versus framebuffer pixels, ticks versus milliseconds, world versus local coordinates, radians versus degrees, linear versus sRGB values.
- Avoid implementation details that may change without changing the contract, such as particular collections, loops, cache structures, or call sequences, unless the detail is itself a required invariant.

## 6. Subsystem-specific requirements

### 6.1 Fabric, Minecraft, and Mixin

Document non-obvious injection and compatibility constraints, especially:

- **Target and purpose:** what behavior is intercepted and what contract the hook establishes.
- **Timing:** when the hook runs relative to relevant state changes or events.
- **Cancellation and side effects:** what callers can observe after an injection.
- **Version assumptions:** only when verified against the actual target signatures or metadata.

Do not document a Mixin by paraphrasing every annotation, claim vanilla equivalence, or say that the hook "recreates" original behavior. Beware of false IntelliJ warnings where methods or accessors are invoked by Mixin transformation rather than direct Java references.

### 6.2 Rendering, RHI, OpenGL/Vulkan, and native resources

For resource-bearing API boundaries, explicitly document as appropriate:

- who owns the GPU/native object and who must close it;
- valid render/backend thread and allowed command submission phase;
- when the handle becomes valid, and how it behaves after disposal;
- pass, framebuffer, synchronization, and layout/precondition requirements;
- which memory or GPU resources survive a failed operation.

Do **not** provide a visual walkthrough of shapes, gradients, blur, shaders, animation, or drawing order unless the order is a binding correctness constraint.

### 6.3 Concurrency and asynchronous operations

For nontrivial async APIs, document: executor or thread affinity, ordering guarantees, visibility/ownership of shared state, cancellation behavior, and result/error propagation. Distinguish scheduling work from completing it. Do not say "thread-safe" unless the supported access pattern has been established.

### 6.4 Events and lifecycle

Specify event phase, dispatch context, whether handlers may mutate/cancel the event, and registration/unregistration requirements if relevant. State whether repeated initialization, suspension, reload, and shutdown calls are permitted.

### 6.5 Configuration, persistence, and serialization

Document persisted keys or format contracts only at stable boundaries. State defaults, migration rules, whether writes are atomic, backup/recovery behavior, and how failures surface—when these facts are established. Do not restate fields serialized by each method.

### 6.6 Scripting and extension runtimes

Document exposed capabilities, call context, lifetime of host objects, error propagation, allowed threading, and restrictions that script authors must respect. Avoid generated descriptions of every JavaScript-facing function when the signature is self-explanatory.

### 6.7 Mathematical and protocol code

Document assumptions, ranges, units, numerical stability conditions, bit layouts, wire-format compatibility, and edge-case semantics. A formula or algorithm explanation is appropriate when it prevents a likely error; a line-by-line walkthrough is not.

## 7. Examples: reject versus accept

These examples are illustrative; the correct text must still be verified against the actual implementation.

**Rejected — promotional or defensive:**

```java
/**
 * Faithfully recreates the original renderer with a lightweight solution.
 * This intentionally does not attempt to implement the full vanilla pipeline.
 */
```

**Acceptable — verified responsibility:**

```java
/** Coordinates render passes submitted through the active RHI backend. */
```

**Rejected — visual narration:**

```java
/** Draws a smooth glowing outline around the selected entity. */
```

**Acceptable — if relevant to the API and true:**

```java
/**
 * Submits the selection outline in framebuffer pixel coordinates.
 * Requires an active world-overlay pass.
 */
```

**Rejected — self-evident method:**

```java
/** @return whether the module is enabled */
public boolean isEnabled();
```

**Preferred:** omit the comment if no additional contract exists.

**Acceptable — resource contract:**

```java
/**
 * Returns the texture currently owned by this atlas.
 * The caller must not close the returned texture.
 *
 * @return the atlas texture, or {@code null} before initialization
 */
```

**Acceptable — async contract:**

```java
/**
 * Schedules a configuration write after the current debounce interval.
 * A subsequent request for the same key replaces the pending deadline.
 */
```

**Acceptable — essential negative statement:**

```java
/**
 * Returns a borrowed handle. Closing the handle is the owner's responsibility.
 */
```

The examples above are **templates**, not statements about existing Combatant APIs. Never insert one without verifying it against the relevant code.

## 8. Cleanup policy for existing comments

Documentation passes MUST **audit existing comments before adding new ones**. The goal is accuracy and signal density, not maximizing the number of Javadoc blocks.

Classify each existing comment:

| Action | Criteria |
| --- | --- |
| **KEEP** | Correct, specific, useful, non-redundant. |
| **REWRITE** | Contains a valuable contract but is vague, partly inaccurate, verbose, or mixed with forbidden filler. |
| **DELETE** | Tautology, visual walkthrough, speculative intent, AI-style disclaimer, unsupported fidelity claim, duplicate of an inherited contract, or obsolete information without a useful remainder. |
| **VERIFY / DEFER** | May encode a meaningful constraint, but the claim cannot be confirmed from available code, tests, or verified external APIs. |

When rewriting, preserve **only verified information**. Do not replace one vague comment with another more polished but equally empty one. If an existing misleading comment contains no independent fact worth preserving, delete it rather than manufacturing a replacement.

A deletion MUST NOT remove an actual legal/license notice, supported compatibility requirement, protocol definition, licensing attribution, or critical non-obvious constraint. Treat these as protected until reviewed.

For documentation-only passes:

1. Read the declaration, relevant callers, implementations, and lifecycle paths before changing a contract.
2. Delete or rewrite forbidden comments; add only missing documentation that passes the usefulness test.
3. Do **not** change Java behavior, signatures, imports, formatting outside affected comments, resource files, or line endings as an incidental cleanup.
4. Do not introduce speculative documentation to satisfy inspections. If the code does not prove a claim, leave it undocumented and report it for review.
5. Report counts of kept, rewritten, deleted, and deferred comments, with important semantic uncertainties noted separately.
6. Review changes for Javadoc syntax, links, and semantic correctness. A successful Javadoc parse does not prove that a documented guarantee is true.

## 9. Review and enforcement

A comment is acceptable only if all answers below are **yes**:

- **Useful:** Does it provide meaningful information beyond the declaration and obvious code?
- **True:** Can each technical assertion be supported by the implementation, tests, or verified external contract?
- **Contract-focused:** Does it describe responsibility, guarantees, constraints, or essential rationale rather than a visual or procedural walkthrough?
- **Minimal:** Would deleting any sentence lose useful information?
- **Neutral:** Does it avoid promotional language, hypothetical defenses, completeness claims, and AI-style filler?
- **Current:** Does it agree with the current code and targeted Minecraft/Java environment?

Use Javadoc/DocLint and appropriately scoped IDE/Checkstyle inspections to catch malformed syntax, unresolved links, and missing published-API documentation. **Do not** impose universal Javadoc coverage on private helpers or treat warning counts as a quality metric. No automatic generator or reviewer may waive the semantic checks above.

### Definition of done

A documentation pass is complete when the touched area's comments accurately describe its meaningful contracts, prohibited text is removed, unnecessary comments are absent, and implementation behavior remains unchanged. Documentation density is **not** a success criterion.

## 10. License headers and attribution

- Retain all existing licensing and provenance statements, including original author names,
  third-party links and additional license restrictions. Legal comments are not stylistic noise.
- Use the repository's existing Combatant GPL header for project-authored Java source, tests,
  project scripts and shaders that otherwise lack one. Never invent authorship or an upstream license.
- Keep Java headers before the package declaration. Keep GLSL `#version` as the first directive;
  put the license after the version/extension prelude. Preserve script shebangs where present.
- Do not apply the Combatant copyright statement to third-party or unverified-origin sources.
  Record those cases for human provenance review, without removing their attribution.
- Generated artifacts should ideally inherit their header from their source generator. When a
  checked-in generated file is edited, verify that the loader accepts the header.
- License-only changes must leave runtime source tokens and all resource identifiers unchanged.
