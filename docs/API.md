# GuiOk item API

Разработчик и владелец GuiOk: **Z1ppzy**. API распространяется вместе с GuiOk
по [GuiOk Source-Available License 1.0](../LICENSE); независимые плагины,
использующие API без включения исходников GuiOk, разрешены лицензией.

`GuiOkApi` — небольшой стабильный слой между общим ресурспаком и игровыми
плагинами Prison/OneBlock. API не требует Oraxen и не использует NMS.

## Подключение

Скачайте `GuiOk-api.jar` из rolling release, положите его в `libs/` проекта и
подключите только для компиляции:

```kotlin
dependencies {
    compileOnly(files("libs/GuiOk-api.jar"))
}
```

В `plugin.yml` зависимого плагина укажите:

```yaml
depend: [GuiOk]
```

Так Paper гарантирует порядок загрузки и доступность классов API через plugin
classloader dependency.

## Получение сервиса

```java
import dev.z1ppzy.guiok.api.GuiOkApi;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<GuiOkApi> registration =
        getServer().getServicesManager().getRegistration(GuiOkApi.class);
if (registration == null) {
    throw new IllegalStateException("GuiOk API is unavailable");
}
GuiOkApi guiOk = registration.getProvider();
```

## Создание и проверка предметов

```java
ItemStack token = guiOk.create("prison:token", 8);
player.getInventory().addItem(token);

if (guiOk.is(player.getInventory().getItemInMainHand(), "prison:token")) {
    // Это именно предмет Prison, а не просто GOLD_NUGGET с похожим названием.
}

guiOk.idOf(item).ifPresent(id -> getLogger().info("GuiOk item: " + id));
guiOk.definition("prison:token").ifPresent(definition -> {
    getLogger().info(definition.model().toString());
});
```

`give(player, id, amount)` сам делит большое количество на стаки и возвращает
не поместившиеся предметы так же, как Bukkit `Inventory#addItem`.

Все методы создания/выдачи должны вызываться на server thread. API проверяет это
явно, чтобы сторонний плагин не создавал ItemMeta асинхронно.

## Глифы пака

```java
String crown = guiOk.glyph("crown")
        .orElseThrow(() -> new IllegalStateException("GuiOk не публикует иконку crown"));
Component tag = Component.text(crown + " VIP");

Set<String> icons = guiOk.iconIds();
```

`glyph` отдаёт символ из приватной области Unicode, который GuiOk регистрирует и
в `guiok:hud`, и в `minecraft:default`. Второй шрифт — причина, по которой символ
работает там, где шрифт выбрать нельзя: ник над головой, scoreboard-team префикс,
чат, таблист.

Метод ничего не подставляет вместо неизвестного имени и возвращает пустой
`Optional` — на этом можно построить строгую проверку конфига при загрузке
своего плагина, а не ловить пустой квадрат в чужом нике.

Что `glyph` знать не может: применил ли конкретный игрок ресурспак. Если это
важно, берите `%guiok_icon_<имя>%` — плейсхолдер отдаёт пустую строку игроку без
пака. Для ников это спасает лишь наполовину: тег считается для владельца ника, а
смотрят на него все, поэтому при статусах в никах пак стоит сделать
`required: true`.

## Отступы

```java
String plate = guiOk.space(-8).orElseThrow()
        + guiOk.glyph("gui_backpack").orElseThrow()
        + guiOk.space(-169).orElseThrow();
```

`space` собирает нужный сдвиг из степеней двойки, зарегистрированных в паке, и
работает до ±511 пикселей. За пределами диапазона возвращается пустой
`Optional`: сдвиг нельзя обрезать молча, иначе картинка уедет по экрану вместо
того, чтобы не нарисоваться вовсе.

## Стабильная идентичность

Каждый предмет получает PDC `guiok:item_id` со своим полным ID, например
`prison:token`. Материал, display name или lore не используются для
идентификации, поэтому переименование и локализация не ломают проверки.

`GuiOkApi#idOf` читает PDC даже для предмета, который был удалён из текущего
каталога. `exists` и `definition` отвечают только по активному каталогу.

## Reload

`/guiok reload` сначала полностью проверяет `config.yml` и `items.yml`, затем
одним присваиванием публикует новый неизменяемый каталог. При ошибке старый
каталог продолжает работать. После успешной публикации вызывается
`GuiOkItemsReloadedEvent` с новым набором ID.

Reload обновляет серверные свойства создаваемых ItemStack, но не может изменить
уже скачанный клиентом ZIP. Новую PNG/JSON-модель нужно собрать, опубликовать и
отправить игрокам через `/guiok resend`.

## Версионирование

Текущая версия контракта: `GuiOkApi.API_VERSION == 3`. Версия 2 добавила
`iconIds()` и `glyph(String)`, версия 3 — `space(int)`; ничего не убиралось. Runtime-значение можно
проверить через `guiOk.apiVersion()`. Версия плагина и git-коммит доступны через
`/guiok version`.
