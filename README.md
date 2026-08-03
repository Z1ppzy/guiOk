# guiOk

Отдельный Paper-плагин для красивого sidebar: обычные динамические строки плюс
PNG-логотип, который клиент рисует как кастомный символ шрифта из ресурспака.
Моды игрокам не нужны.

## Что получается

- один персональный sidebar на игрока;
- PNG вместо обычного текстового заголовка;
- текстовый fallback, если игрок отклонил или не загрузил пак;
- строки из MiniMessage, встроенных значений и PlaceholderAPI;
- баланс через Vault, если Vault и economy provider установлены;
- сохранённый `/guiok toggle` через PDC игрока;
- точная версия, git-коммит и SHA-1 пака через `/guiok version`;
- `GuiOk.jar` и `GuiOkResourcePack.zip` автоматически обновляются в rolling
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

> Репозиторий сейчас приватный. GitHub Release из приватного репозитория не
> является публичным URL для Minecraft-клиентов. Значение по умолчанию начнёт
> работать после публикации репозитория; до этого разместите ZIP на публичном
> CDN/веб-сервере и замените `resource-pack.url`.

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
  уменьшит nearest-neighbor логотип до `240×72`, а монетку — до `12×12`.

Затем выполните:

```powershell
.\gradlew.bat clean check jar resourcePackZip --no-daemon
```

Результат:

```text
build/libs/GuiOk.jar
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
| `/guiok reload` | перечитать и проверить config | `guiok.admin` |

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
.\gradlew.bat clean check jar resourcePackZip --no-daemon
.\gradlew.bat runServer
```

CI выполняет ту же проверку на каждом push и pull request. Pull request только
проверяется; успешный push в default branch заменяет два ассета релиза `latest`.

## Лицензия

MIT. Демонстрационный `PRISON` — оригинальный заменяемый пример, не копия
логотипа с референса.
