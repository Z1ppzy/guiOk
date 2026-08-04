# guiOk

**Разработчик:** [Z1ppzy](https://github.com/Z1ppzy)

**Лицензия:** [GuiOk Source-Available License 1.0](LICENSE)

Paper-плагин для единого оформления режима: динамический sidebar, PNG-глифы и
namespaced custom items с автоматически собираемыми текстурами и публичным API.
Моды игрокам не нужны.

## ESC-меню HeavenlyWeiner

Пока ресурспак GuiOk активен, ванильное multiplayer ESC-меню получает:

- оригинальный двухстрочный pixel-art логотип `HEAVENLY / WEINER` вместо
  заголовка `Game Menu`;
- фиолетовые обычные и оранжевые наведённые кнопки;
- затемнённое disabled-состояние;
- `▶ Вернуться на HeavenlyWeiner` для русского клиента и
  `▶ Return to HeavenlyWeiner` для английского.

Это работает на обычном клиенте без Fabric/Forge. Реальные дополнительные
кнопки и ссылки нельзя создать одним ресурспаком: для них используется
ванильный `pause_screen_additions`/Dialog или Server Links. GuiOk намеренно не
рисует фальшивые кликабельные Discord-панели без настоящего URL.

Важно: `widget/button*` — общие ванильные GUI-спрайты, поэтому тема кнопок
применяется не только к ESC, но и к другим стандартным меню, пока серверный пак
активен. Исходник логотипа находится в `resourcepack/pause-logo.png`; Gradle
обрезает прозрачные поля, уменьшает его nearest-neighbor до безопасного размера
и встраивает как `assets/guiok/textures/font/pause_menu.png`.

## Что получается

- один персональный sidebar на игрока;
- PNG вместо обычного текстового заголовка;
- текстовый fallback, если игрок отклонил или не загрузил пак;
- строки из MiniMessage, встроенных значений и PlaceholderAPI;
- баланс через Vault, если Vault и economy provider установлены;
- custom items через современный `item_model`, без ручного CustomModelData;
- API для Prison/OneBlock-плагинов через Bukkit ServicesManager;
- сохранённый `/guiok toggle` через PDC игрока;
- точная версия, git-коммит и SHA-1 пака через `/guiok version`;
- `GuiOk.jar`, `GuiOk-api.jar` и `GuiOkResourcePack.zip` автоматически обновляются в rolling
  GitHub Release `latest` только после успешных тестов.

Поддерживаемый стек: Java 25, Paper API `26.1.2.build.74-stable`, клиенты
Minecraft 26.1–26.2. Font-pack объявляет диапазон resource-pack formats 84–88.

## Быстрый запуск

1. Скачайте `GuiOk.jar` и положите его в `plugins/`.
2. Запустите сервер один раз. Плагин выгрузит копию своего пака в
   `plugins/GuiOk/GuiOkResourcePack.zip`.
3. Укажите в `plugins/GuiOk/config.yml` публичный прямой HTTPS URL этого ZIP.
4. Выполните `/guiok reload` и `/guiok resend`.
5. Если TAB или другой плагин уже рисует sidebar, отключите его sidebar либо
   оставьте `sidebar.replace-existing-scoreboard: true`, понимая, что владеть
   vanilla sidebar одновременно может только один плагин.

Публичный rolling release репозитория уже подходит как прямой URL пака. Для
приватного форка потребуется собственный публичный CDN/веб-сервер.

## Как заменить картинку

Заменяемые исходники:

```text
resourcepack/logo.png
resourcepack/coin.png
```

Требования:

- PNG с прозрачным фоном;
- оригинальный логотип без чужих товарных знаков;
- для логотипа желателен горизонтальный pixel-art, для монетки — квадратный;
- исходники могут быть большими: Gradle автоматически обрежет прозрачные поля и
  уменьшит nearest-neighbor логотип до `240×72`, а монетку — до `16×16`.

Затем выполните:

```powershell
.\gradlew.bat clean check jar apiJar resourcePackZip --no-daemon
```

Результат:

```text
build/libs/GuiOk.jar
build/libs/GuiOk-api.jar
build/distributions/GuiOkResourcePack.zip
```

SHA-1 собранного ZIP автоматически записывается внутрь JAR. Поэтому при
`resource-pack.sha1: auto` не нужно вручную пересчитывать хеш после замены PNG.
Сам ZIP также вкладывается в JAR и экспортируется при запуске.

Высота и положение глифа меняются в
`resourcepack/pack/assets/guiok/font/hud.json` полями `height` и `ascent`.

## Настройка строк

Каждая строка в `sidebar.lines` — MiniMessage. Доступны:

| Тег | Значение |
| --- | --- |
| `<player>` | имя игрока |
| `<display_name>` | отображаемое имя Component |
| `<world>` | текущий мир |
| `<online>` / `<max_online>` | онлайн |
| `<ping>` | ping |
| `<balance>` | Vault-баланс или `—` |
| `<icon:coin>` / `<icon:logo>` | пробел и глиф после значения; без загруженного пака исчезает целиком |
| `<x>` / `<y>` / `<z>` | координаты |
| `<papi:identifier>` | `%identifier%` из PlaceholderAPI |

Пример для OneBlock нужно подстроить под placeholders именно вашего плагина:

```yaml
sidebar:
  lines:
    - ""
    - "<gray>Игрок:</gray> <white><player></white>"
    - "<gray>Остров:</gray> <white><papi:aoneblock_island_name></white>"
    - "<gray>Уровень:</gray> <green><papi:aoneblock_island_level></green>"
    - "<green><balance></green><icon:coin>"
```

Если PlaceholderAPI или expansion отсутствуют, тег выводит `—`, не ломая весь
sidebar. Допускается максимум 15 строк — это ограничение vanilla sidebar.

## Кастомные предметы и API

Чтобы добавить простую 2D-текстуру предмета:

1. Положите PNG в `resourcepack/items/<namespace>/<path>.png`.
2. Добавьте предмет в `resourcepack/items.yml`.
3. Соберите и опубликуйте новый JAR/ZIP. Gradle сам создаст item definition,
   model JSON и скопирует текстуру в правильные каталоги пака.

```yaml
prison:token:
  material: GOLD_NUGGET
  name: "<gold>Тюремный жетон"
  lore:
    - "<gray>Валюта Prison"
  texture: prison:currency/token
  parent: minecraft:item/generated
  glint: false
```

Исходный PNG для примера выше:
`resourcepack/items/prison/currency/token.png`. Для инструмента используйте
`parent: minecraft:item/handheld`. ID, model и texture всегда пишутся в нижнем
регистре; абсолютные пути и `..` отклоняются сборкой.

На сервере `items.yml` экспортируется в `plugins/GuiOk/items.yml`. Изменения
имени, lore, материала и model key применяются через `/guiok reload`. Новая PNG
или новый model JSON требуют новой сборки ресурспака и `/guiok resend`.

Полный контракт подключения другого плагина и Java-примеры находятся в
[docs/API.md](docs/API.md).

## Иконки в TAB и других плагинах

Глифы GuiOk зарегистрированы дважды с одинаковыми метриками: в собственном
шрифте `guiok:hud` (его использует sidebar) и в `minecraft:default`. Поэтому
любой сторонний плагин выводит иконку обычным символом, без тега шрифта и без
конфликта с `<icon:coin>`.

| Глиф | Имя | Символ | Метрики |
| --- | --- | --- | --- |
| логотип | `logo` | `U+E001` | height 22, ascent 20 |
| монетка | `coin` | `U+E002` | height 11, ascent 10 |
| ESC-логотип | — | `U+E100` | height 64, ascent 16 |

### Иконки статусов

Отдельный набор в диапазоне `U+E2xx` нарисован под строку текста: 8×8 при
`height 8, ascent 7`, то есть ровно по высоте шрифта. Исходники лежат в
`resourcepack/icons/` и копируются в пак **без масштабирования** — PNG обязан
быть той же высоты, что объявлена в шрифте, иначе клиент растянет пиксель-арт и
сборка упадёт на проверке.

| Имя | Символ | Что нарисовано |
| --- | --- | --- |
| `star` | `U+E200` | четырёхлучевая звезда |
| `crown` | `U+E201` | корона с рубинами |
| `ember` | `U+E202` | пламя |
| `pickaxe` | `U+E203` | кирка |
| `shackle` | `U+E204` | замок |
| `skull` | `U+E205` | череп |
| `gem` | `U+E206` | аметист |
| `clover` | `U+E207` | четырёхлистный клевер |
| `bracket_left` | `U+E208` | левый шеврон обводки |
| `bracket_right` | `U+E209` | правый шеврон обводки |
| `crown_tall` | `U+E210` | корона 16×16, `height 16, ascent 12` |

`bracket_left` и `bracket_right` собираются в рамку вокруг тега:
`bracket_left` + текст + `bracket_right`.

`crown_tall` — единственный высокий глиф набора: `ascent 12` поднимает его над
базовой линией, поэтому в нике он рисуется выше строки. Тёмная подложка ника при
этом не растягивается, а в таблисте такой глиф заходит на соседнюю строку — для
списка игроков берите плоские 8-пиксельные иконки.

### Через PlaceholderAPI

Если PlaceholderAPI установлена, GuiOk регистрирует expansion `guiok`, и иконка
пишется читаемым плейсхолдером — без невидимых символов в конфиге:

| Плейсхолдер | Значение |
| --- | --- |
| `%guiok_icon_<имя>%` | глиф с этим именем или пусто, если пак не применён |
| `%guiok_icon_coin%` | монетка или пусто, если пак не применён |
| `%guiok_icon_crown%` | корона или пусто, если пак не применён |

```yaml
header-footer:
  footer:
    - "&7Баланс: &a%vault_eco_balance% %guiok_icon_coin%"
```

Плейсхолдер учитывает состояние пака так же, как `<icon:coin>`: игрок без пака
видит пустую строку, а не квадрат. Без PlaceholderAPI expansion не
регистрируется, остальной плагин работает как обычно.

### Символом напрямую

Не каждый парсер разворачивает YAML-эскейп: TAB, например, выведет `\uE002`
как шесть обычных символов, ему нужен сам глиф. Скопировать его в буфер:

```powershell
powershell -c "Set-Clipboard ([char]0xE002)"
```

Такой символ печатается как есть и про состояние пака не знает: игрок без пака
увидит пустой квадрат. Тег `<font:guiok:hud>` тоже остаётся рабочим вариантом
там, где плагин парсит MiniMessage, но он не нужен.

Логотип высотой 22 пикселя в шапке таблиста перекрывает соседнюю строку — под
такие места заведите отдельный provider с меньшими `height` и `ascent`.

### Над ником игрока

Ник над головой рисуется через scoreboard-team: префикс, имя, суффикс — одна
строка. Второго яруса нет: unlimited nametag mode вырезан в TAB 5.0.0, и
поднять что-то выше строки можно только `ascent` (как у `crown_tall`).

Поскольку глиф зарегистрирован в `minecraft:default`, шрифт выбирать не нужно —
в TAB достаточно вписать плейсхолдер в `tagprefix`:

```yaml
tagprefix: "%guiok_icon_crown% "
```

Одна оговорка про `required: false`: плейсхолдер проверяет пак того игрока, чей
тег считается, а не того, кто смотрит. Ник обладателя статуса видят все, поэтому
игрок, отклонивший пак, увидит у чужих ников квадрат. Если статусы вешаются на
ники, ставьте `required: true`.

## Ресурспак

- `replace-existing-packs: false` наслаивает GuiOk поверх уже отправленных
  серверных паков на современных клиентах.
- `required: false` оставляет игроку выбор и включает текстовый fallback.
- `wait-for-pack: true` не показывает квадрат отсутствующего глифа во время
  скачивания.
- UUID пака должен оставаться стабильным; SHA-1 должен меняться вместе с ZIP.
- Если другой пак уже использует namespace `guiok`, объедините паки или смените
  namespace одновременно в font JSON и `title-with-pack`.

## Команды

| Команда | Назначение | Право |
| --- | --- | --- |
| `/guiok toggle` | скрыть/вернуть sidebar | `guiok.use` |
| `/guiok resend` | повторно отправить пак | `guiok.use` |
| `/guiok version` | JAR, git-коммит, дата, Paper API, SHA-1 | `guiok.use` |
| `/guiok status` | pack state, sidebar, PAPI и Vault | `guiok.use` |
| `/guiok items` | список зарегистрированных item ID | `guiok.admin` |
| `/guiok give <игрок> <id> [количество]` | выдать кастомный предмет | `guiok.admin` |
| `/guiok reload` | атомарно перечитать `config.yml` и `items.yml` | `guiok.admin` |

Невалидная конфигурация не подменяется молча. При старте плагин отключается с
ясной причиной; при `/guiok reload` старая рабочая конфигурация остаётся активна.

## Диагностика ошибки

К баг-репорту приложите:

1. полный вывод `/guiok version`;
2. вывод `/guiok status` от затронутого игрока;
3. клиентскую версию и GUI Scale;
4. строки лога вокруг ошибки;
5. прямой URL пака и результат его открытия без авторизации.

Коммит в `/guiok version` кликабелен и копируется в буфер. На старте те же
данные пишутся в лог, поэтому всегда понятно, какой именно JAR запущен.

## Локальная разработка

```powershell
.\gradlew.bat clean check jar apiJar resourcePackZip --no-daemon
.\gradlew.bat runServer
```

CI выполняет ту же проверку на каждом push и pull request. Pull request только
проверяется; успешный push в default branch заменяет три ассета релиза `latest`.

## Лицензия

GuiOk 1.1.1 и новее распространяется по
[GuiOk Source-Available License 1.0](LICENSE): использование на своих серверах,
приватные модификации и независимые плагины через API разрешены; публикация,
продажа или распространение полного, частичного либо изменённого кода/JAR без
письменного разрешения Z1ppzy запрещены. Передача официального ресурспака
подключающимся игрокам разрешена.

Версии до 1.1.1 сохраняют MIT-лицензию, под которой они были опубликованы.
Демонстрационный `PRISON` — оригинальный заменяемый пример, не копия логотипа с
референса.
