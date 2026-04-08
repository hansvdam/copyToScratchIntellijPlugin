# Copy To Scratch

An IntelliJ plugin that copies your current text selection into the most recently used scratch file — without switching focus.

## How It Works

1. Select text in any editor
2. Press the shortcut (or use the context menu: **Copy Selection to Scratch**)
3. The selected text is inserted at the cursor position in your last-opened scratch file

If the scratch file is open, text is inserted at its caret position. If it's closed (but in editor history), text is appended to the end.

## Keyboard Shortcuts

| OS              | Shortcut               |
|-----------------|------------------------|
| Windows / Linux | `Ctrl+Alt+Shift+V`     |
| macOS           | `Cmd+Alt+Ctrl+V`       |

## Installation

### From Source

```bash
./gradlew buildPlugin
```

The packaged plugin ZIP will be in `build/distributions/`. Install it via **Settings → Plugins → ⚙️ → Install Plugin from Disk**.

### From JetBrains Marketplace

*(Coming soon)*

## Requirements

- IntelliJ IDEA 2024.1.7+ (or any compatible JetBrains IDE)
