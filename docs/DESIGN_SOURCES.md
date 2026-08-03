# Design sources and clean-room boundary

GuiOk не содержит исходный код или ассеты Oraxen. Архитектура item-модуля
написана отдельно под Paper 26.1.2 и использует только публично описанные идеи и
стандартные API Paper/Bukkit.

Из официальной документации Oraxen были взяты высокоуровневые практики:

- конфигурация item ID как источник истины;
- автоматическая генерация model JSON из простой PNG;
- стабильная идентификация предмета через PDC;
- API для получения builder/ItemStack и извлечения ID;
- ранняя диагностика конфликтов и битых resource locations;
- современный `item_model` вместо ручного распределения числового CMD.

Полезные официальные страницы:

- <https://docs.oraxen.com/creating-content/items>
- <https://docs.oraxen.com/creating-content/items/appearance>
- <https://docs.oraxen.com/developers/api>
- <https://github.com/oraxen/oraxen/blob/master/LICENSE.md>

Лицензия Oraxen разрешает личное изучение, но запрещает распространять полные,
частичные или модифицированные копии пакета. Поэтому в GuiOk не переносились их
классы, методы, тексты конфигураций или ресурсы. Совпадающие базовые понятия
(`NamespacedKey`, PDC, item model definitions) являются частью Minecraft/Paper
экосистемы, а реализация GuiOk имеет собственные имена, API и жизненный цикл.

Сам GuiOk версии 1.1.1 и новее распространяется по собственной оригинальной
[GuiOk Source-Available License 1.0](../LICENSE), правообладатель и разработчик —
Z1ppzy. Текст лицензии Oraxen в проект не копировался.

## ESC-меню

Оформление pause screen опирается на ванильные client resources, а не на NMS
или клиентский мод:

- официальный changelog 1.20.2 описывает отдельные GUI sprites в
  `textures/gui/sprites`, которые resource pack может переопределять;
- официальный changelog 1.21.6 описывает `pause_screen_additions` и Dialog для
  настоящих серверных кнопок;
- официальный changelog 26.2 фиксирует resource-pack format 88 и актуальные
  pause-menu sprites.

Источники:

- <https://feedback.minecraft.net/hc/en-us/articles/19703470383757-Minecraft-Java-Edition-1-20-2>
- <https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6>
- <https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2>

Дополнительно структура проверена непосредственно по официальным Mojang client
JAR 26.1.2 и 26.2 с обязательной SHA-1-проверкой загрузок. В обеих версиях
сохраняются `minecraft:default` reference providers и стандартные
`widget/button`, `button_highlighted`, `button_disabled` размером 200×20.
