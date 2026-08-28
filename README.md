# Flutter and Dart IntelliJ Plugins (Monorepo)

This repository is a monorepo containing both the **Flutter plugin** and the **Dart plugin** for IntelliJ IDEA and Android Studio.

## Repository Structure

- **[`flutter/`](flutter/)**: The Flutter IntelliJ Plugin codebase.
- **[`dart/`](dart/)**: The Dart IntelliJ Plugin codebase.

---

## Building and Testing Separately

Both plugins are built independently using their respective Gradle configurations.

### Flutter Plugin

To build and test the Flutter plugin, navigate to the `flutter/` directory:

```bash
cd flutter

# Build the plugin zip archive
./gradlew buildPlugin

# Run unit tests
./gradlew test

# Verify plugin compatibility with target IDEs
./gradlew verifyPlugin

# Launch an IDE instance with the Flutter plugin loaded
./gradlew runIde
```

### Dart Plugin

To build and test the Dart plugin, navigate to the `dart/` directory (or `dart/third_party/`):

```bash
cd dart

# Build the plugin zip archive
./gradlew buildPlugin

# Run unit tests (from third_party directory)
cd third_party
./gradlew :test --tests "com.jetbrains.lang.dart.*"

# Verify plugin compatibility
./gradlew verifyPlugin

# Launch an IDE instance with the Dart plugin loaded
./gradlew runIde
```

---

## AI Coding Agent Skills

This repository comes with custom configuration and automation skills for AI coding agents.

These skills are located in the [.agents/skills/](.agents/skills/) directory. They are automatically discovered and loaded by agentic workflows when they analyze the workspace.

### Available Workspace Skills:
* **[Add Missing Unit Test](.agents/skills/add-missing-unit-test/SKILL.md):** Add a new unit test for a class that currently lacks one or add a new test case to an existing test file.
* **[Audit Accessibility](.agents/skills/audit-accessibility/SKILL.md):** Ensure custom UI components are accessible to users with screen readers and other assistive technologies.
* **[Audit Dependencies](.agents/skills/audit-dependencies/SKILL.md):** Optimize plugin size and security by removing unused dependencies and updating outdated libraries.
* **[Audit UI Thread Safety](.agents/skills/audit-ui-thread-safety/SKILL.md):** Prevent UI freezes and ensure a responsive user experience by validating threading rules and migrating blocking calls off the EDT.
* **[Cleanup Code Inspections](.agents/skills/cleanup-code-inspections/SKILL.md):** Systematically resolve static analysis warnings and reduce technical debt.
* **[Cleanup Unused Assets](.agents/skills/cleanup-unused-assets/SKILL.md):** Reduce plugin size by scanning resources/icons and removing unreferenced assets.
* **[Code Review](.agents/skills/code-review/SKILL.md):** Performs a pedantic, multi-perspective code review on changes against the styleguide and best practices.
* **[Implement Dart Language Feature](.agents/skills/implement-dart-language-feature/SKILL.md):** Guidelines for implementing and reviewing new Dart language features, parsing logic, and grammar modifications.
* **[Migrate DAS to LSP](.agents/skills/migrate-das-to-lsp/SKILL.md):** Guide for converting legacy Dart Analysis Server (DAS) feature implementations to JetBrains LSP.
* **[Migrate IntelliJ Util](.agents/skills/migrate-intellij-util/SKILL.md):** Optimize memory usage and consistency by migrating standard Java/Kotlin classes to IntelliJ's specialized `com.intellij.util` implementations.
* **[Patch Copied LSP Sources](.agents/skills/patch-copied-lsp-sources/SKILL.md):** Automates copying and patching of JetBrains LSP sources.
* **[Plugin Issue Triage](.agents/skills/plugin-issue-triage/SKILL.md):** Automates the triage of GitHub issues in the Dart and Flutter IntelliJ plugins.
* **[Port PR](.agents/skills/port-pr/SKILL.md):** Fetches a Pull Request and conceptually ports its changes between plugins.
* **[Release Dart Plugin](.agents/skills/release-dart-plugin/SKILL.md):** Step-by-step guide for preparing, validating, testing, and publishing monthly releases of the Dart IntelliJ plugin.
* **[Release Flutter Plugin](.agents/skills/release-plugin/SKILL.md):** Prepare and execute a new release for the Flutter IntelliJ plugin.
* **[Remove Platform Version](.agents/skills/remove-platform-version/SKILL.md):** Remove support for an older IntelliJ Platform / Android Studio version.
* **[Resolve Verification Issues](.agents/skills/resolve-verification-issues/SKILL.md):** Eliminate plugin verification warnings and errors identified by `./gradlew verifyPlugin`.
* **[Root Cause Regression](.agents/skills/root-cause-regression/SKILL.md):** Investigates a GitHub issue reporting a regression, analyzes recent commits, and identifies possible culprits.
* **[Verify EAP Compatibility](.agents/skills/verify-eap-compatibility/SKILL.md):** Ensure the plugin remains compatible with the latest IntelliJ Platform releases and EAP builds.

