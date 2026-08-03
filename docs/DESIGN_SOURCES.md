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
