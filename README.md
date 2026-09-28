# DestroyLobby 1.3.0 (Paper 1.21.1)

## Чат - отдельный плагин DestroyChat (1.3.0)

Формат чата, локальный/глобальный каналы, `/color` и чат-префикс переехали в плагин
**DestroyChat**. DestroyLobby отвечает только за правила лобби, и они работают с любым чатом:

- в лобби чат выключен (`chat.block-in-lobby`);
- лобби и SkyPvP не видят сообщения друг друга (`isolation.chat`);
- `/prefix chat ...` перенаправляется в `/chatprefix` плагина DestroyChat.

Настройки `chat.format`, `chat.local-radius` и т.п., которые версия 1.2.0 дописала в этот
config.yml, больше не используются - их можно удалить.

Команды LuckPerms для донат-групп - в `DONATE-SETUP.txt` (права прежние, перевыдавать не нужно).

## Что изменилось

**Таб.** Строка игрока теперь собирается целиком как `playerListName`
(префикс + ник + суффикс), поэтому работают любые hex-цвета и градиенты.
Раньше цвет ника шёл через цвет команды, а он знает только 16 цветов,
отсюда и «не те» оттенки. Команды остались для сортировки по весу
LuckPerms и для ника над головой. Хедер/футер задаются списком строк,
цвета сняты пипеткой со скринов образца.

**Лобби и SkyPvP изолированы.** Игроки в SkyPvP не видят в табе тех,
кто в лобби, и наоборот (`isolation`). Чат тоже разделён: сообщения
из SkyPvP не приходят в лобби. Права `destroylobby.seeall` /
`destroylobby.chat.seeall` дают модерации видеть всех.

**Прыжок.** Вместо двойного — одиночный: обычный пробел в лобби
подбрасывает игрока. Смотришь вниз — ~2 блока, прямо — ~5, вверх —
~9.5 блока и чуть дальше вперёд. Кольцо синего огня от ног, облако
огня при старте, густой след синего огня и душ весь полёт, «хлопок»
при приземлении. Shift + пробел — обычный прыжок (для паркура).
Урона от падения в лобби нет. Флаг полёта, который оставляла старая
версия, снимается автоматически.

**Скорборд.** Как на скрине: дата, ⚡ Ник, ❤ ХП (в сердечках), ⛃ Коины
(иконка из пака), ⚔ Убийства, ☠ Смерти, сайт. Красные цифры справа скрыты.

**Старый config.yml заменяется сам.** При первом запуске новой версии
старый конфиг переименовывается в `config-old-<время>.yml`, создаётся новый,
а мир лобби, игровые миры, точка спавна, разрешённые команды и настройки
`/prefix` переносятся. Так уходит и старая строка «⚡ antidepressant ✓»
из футера таба лобби.

## Ресурс-пак

В пак добавлен глиф `\uE030` — оранжевый смайлик из таба лобби
(`assets/destroy/textures/icon/smile.png` + запись в `font/default.json`).
Залей обновлённый `SP-DS.zip` туда, откуда сервер раздаёт пак, и обнови
`resource-pack-sha1` в `server.properties`.

## Настройка групп LuckPerms под вид со скрина

```
# обычный игрок: серые мечи, ник лавандовый (#CDCDFF из config.yml)
/lp group default meta setprefix 1 "&7⚔ "

# донат: белые уголки + цветное название, ник серый
/lp group legend meta setprefix 100 "&f⌜&#FB6A4ALegend&f⌟ "
/lp group deluxe meta setprefix 100 "&f⌜&6Deluxe&f⌟ "

# персонал: бейджи из ресурс-пака (цвет белый, чтобы картинка была в родных цветах)
/lp group admin     meta setprefix 100 "&f🤪 "
/lp group manager   meta setprefix 100 "&fɱ "
/lp group curator   meta setprefix 100 "&f "
/lp group moderator meta setprefix 100 "&f "
/lp group media     meta setprefix 100 "&f "
```

Символы в кавычках у curator/moderator/media — это приватные символы
пака (U+E014, U+E00E, U+E006), в чате они могут выглядеть пустыми, но
это нормально. Проще скопировать команды из этого файла.

Порядок в табе — по `weight` группы: `/lp group admin setweight 1000` и т.д.

## Сборка

```
mvn clean package
# target/destroy-lobby-1.3.0.jar
```

## Команды и права

- `/destroylobby reload|setspawn` — `destroylobby.admin`
- `/coins give|take|set <ник> <число>` — `destroylobby.admin`
- `/prefix set <текст>` / `/prefix reset` — `destroylobby.customprefix`
- `destroylobby.lobby.bypass` — обходить защиту лобби
- `destroylobby.chat.bypass` — писать в чат в лобби
- `destroylobby.seeall` / `destroylobby.chat.seeall` — видеть всех / весь чат

## Плейсхолдеры

`%destroy_coins%`, `%destroy_kills%`, `%destroy_deaths%`, `%destroy_group%`,
`%destroy_namecolor%`, `%destroy_nick%`. Префикс/суффикс — `%luckperms_prefix%` / `%luckperms_suffix%`.
