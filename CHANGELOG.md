# Changelog

## 1.0.4

- Redrew the Vault coin as a hand-pixeled micro-sprite with a dark outline,
  copper rim, highlight, shaded edge and central mint mark.
- Increased the source glyph budget to 16×16 while keeping its HUD render
  compact at 11 pixels high.

## 1.0.3

- Added a compact gold coin glyph for Vault balances.
- Replaced the labelled balance row with `<balance><icon:coin>`.
- Pack icons now disappear cleanly when the resource pack is unavailable.

## 1.0.2

- Replaced the generic `GUI OK` demo artwork with a compact `PRISON` mode logo.
- Updated the no-resource-pack sidebar fallback title to `PRISON`.

## 1.0.1

- Reduced the bitmap sidebar title from 48 to 22 pixels for a compact HUD.

## 1.0.0

- Configurable per-player Paper sidebar with a resource-pack bitmap logo.
- Text fallback when the pack is disabled, declined, discarded or fails.
- Built-in and PlaceholderAPI-powered dynamic lines.
- Optional Vault balance without a hard runtime dependency.
- `/guiok version` includes the exact Git commit and pack SHA-1.
- Reproducible plugin/resource-pack builds and rolling `latest` release.
