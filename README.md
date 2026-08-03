# guiOk

**Разработчик:** [Z1ppzy](https://github.com/Z1ppzy)

**Лицензия:** [GuiOk Source-Available License 1.0](LICENSE)

Paper-плагин для единого оформления режима: динамический sidebar, PNG-глифы и
namespaced custom items с автоматически собираемыми текстурами и публичным API.
Моды игрокам не нужны.

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
| `<icon:coin>` | пробел и монетка после значения; без загруженного пака исчезает целиком |
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
