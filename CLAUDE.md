# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

IntelliJ Platform plugin ("Copy To Scratch") that copies selected editor text into the most recently used scratch file. Single-action plugin written in Kotlin, built with Gradle.

## Build Commands

```bash
./gradlew build          # Build and verify plugin
./gradlew runIde         # Launch IDE sandbox with plugin installed
./gradlew buildPlugin    # Package plugin for distribution (output: build/distributions/)
```

No test suite or linter is configured.

## Architecture

The entire plugin is one action class: `CopyToScratchAction` (`src/main/kotlin/com/copyhelper/CopyToScratchAction.kt`). It extends `AnAction` + `DumbAware` and:

1. Grabs selected text from the active editor
2. Finds the last-opened scratch file (checks open editors first, then editor history via reflection on `EditorHistoryManager`)
3. Inserts text at cursor position (if scratch is open) or appends (if closed), wrapped in `WriteCommandAction` for undo support
4. Shows a balloon notification on success/failure

Plugin registration, keyboard shortcuts (`Ctrl+Alt+Shift+V` / `Cmd+Alt+Ctrl+V`), and notification group are declared in `src/main/resources/META-INF/plugin.xml`.

## Build Configuration

- Kotlin JVM 21 toolchain, targeting JVM 17 bytecode
- Platform: IntelliJ Community (IC) 2024.1.7, minimum build 241
- Gradle 9.0 via wrapper
