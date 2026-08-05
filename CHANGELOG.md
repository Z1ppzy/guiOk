# Changelog

## Unreleased

- **Breaking.** Removed the Vault dependency and the built-in `<balance>` tag. Vault
  reached exactly one value — the balance on the sidebar — while costing a soft
  dependency, service discovery, a TTL cache and its own failure handling, and it
  exposes only the one primary economy, which a server with several currencies
  outgrows immediately. Read the balance through PlaceholderAPI instead, which
  already carries every other value on the HUD:
  `<papi:cmi_user_balance_formatted>`, `<papi:vault_eco_balance_formatted>` or any
  placeholder your economy publishes.
- A config that still uses `<balance>` is now rejected on load and on `/guiok reload`
  with the replacement named, because MiniMessage prints an unknown tag verbatim and
  the old line would otherwise reach players as the literal text `<balance>`.
- `/guiok status` no longer reports Vault, and the startup line no longer mentions it.

## 1.1.5

- Added a space provider to both fonts: powers of two from one to 256 pixels in
  each direction, so any offset up to ±511 is a short sequence of code points.
  A negative offset is the only way to draw a picture anywhere other than where
  the text already is, which is what a custom container background needs.
- Added `GuiOkApi#space(int)` and raised the contract to version 3.
- Added the `gui_backpack` glyph: a 176×17 header plate that covers the title
  strip of a chest window without reaching the first row of slots.
- Added a build check that both fonts declare exactly the offsets PackSpaces
  composes from, so a misplaced picture fails the build instead of the server.

## 1.1.4

- Added `/guiok icons`, which lists every pack glyph with its character and ready
  `%guiok_icon_<name>%` placeholder; `/guiok items` lists items and never showed
  glyphs, leaving an operator to guess whether the installed pack carries them.
- Added the glyph count to `/guiok status`.

## 1.1.3

- Added eleven status glyphs in `U+E2xx` — star, crown, ember, pickaxe, shackle,
  skull, gem, clover, both frame brackets and a tall 16×16 crown — registered in
  `minecraft:default` so TAB can print them above a player's head.
- Added `resourcepack/icons` as the source directory for status glyphs; PNGs are
  packed verbatim, and the build fails when a PNG height differs from the height
  its font provider declares.
- Added build-time cross-checks that every status glyph reaches both fonts with
  identical metrics, owns a private-use code point of its own and is published by
  `PackIcons`, so `%guiok_icon_<name>%` can never resolve to nothing.
- Added `GuiOkApi#glyph(String)` and `GuiOkApi#iconIds()`; the API contract is now
  version 2 and stays backwards compatible with version 1.

## 1.1.2

- Added an original two-line HeavenlyWeiner pixel-art mark to the vanilla
  multiplayer pause screen through the default-font `menu.game` glyph.
- Added Russian and English return-button labels for HeavenlyWeiner.
- Added purple normal, orange highlighted and dim disabled vanilla button
  sprites with the official 200×20 nine-slice metadata.
- Registered the sidebar logo and coin glyphs in the default font as well, so
  third-party plugins such as TAB can print them as plain characters.
- Added the optional `guiok` PlaceholderAPI expansion with
  `%guiok_icon_coin%` and `%guiok_icon_logo%`, which stay empty until the
  player has applied the pack.
- Validated every generated pause-menu image, language override and font
  reference as part of the resource-pack build.
- Verified the resource format against official Minecraft 26.1.2 and 26.2
  client assets.

## 1.1.1

- Marked all project-owned Java sources, runtime metadata, JAR manifests and
  the resource pack with the Z1ppzy developer attribution.
- Embedded the project license in `GuiOk.jar`, `GuiOk-api.jar` and
  `GuiOkResourcePack.zip`, with build-time validation against accidental removal.
- Added developer and license identity to startup diagnostics and
  `/guiok version`.
- Changed releases from 1.1.1 onward to the original GuiOk Source-Available
  License 1.0; previously published versions retain their MIT terms.

## 1.1.0

- Added lowercase namespaced custom items from `resourcepack/items.yml`.
- Added build-time generation of modern item definitions, models and textures.
- Added the Bukkit ServicesManager `GuiOkApi`, immutable definitions, PDC item
  identity and an item-catalog reload event.
- Added `/guiok items`, `/guiok give`, item diagnostics and tab completion.
- Added a standalone `GuiOk-api.jar` rolling-release asset for dependent plugins.
- Added strict path, material, MiniMessage and PNG validation before publication.
- Reduced scoreboard packet churn by skipping unchanged titles and lines.

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
