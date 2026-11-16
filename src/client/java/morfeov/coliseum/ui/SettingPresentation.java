package morfeov.coliseum.ui;

import morfeov.coliseum.setting.Setting;

/** User-facing setting labels and help for the inline card layout. Storage keys stay unchanged. */
final class SettingPresentation {
    record Info(String label, String help, String icon, String unit) {}
    private SettingPresentation() {}
    static Info of(Setting<?> setting) {
        if(setting instanceof BooleanGroup)return info(setting.name(),"Можно выбрать несколько пунктов","settings","");
        return switch (setting.name()) {
            case "Attack Range" -> info("Дистанция атаки", "Максимальная дистанция удара", "arrow", " бл.");
            case "Extra Range" -> info("Доп. дистанция наводки", "Дополнительная дистанция поиска цели", "arrow", " бл.");
            case "Move Correction" -> info("Коррекция движения", "Как учитывать движение при поворотах", "settings", "");
            case "Target Priority" -> info("Сортировка", "Порядок выбора следующей цели", "settings", "");
            case "Players" -> info("Игроки", "Атаковать других игроков", "player", "");
            case "No Armor" -> info("Без брони", "Включать игроков без экипировки", "player", "");
            case "Hostile Mobs" -> info("Враждебные мобы", "Выбирать враждебных существ", "player", "");
            case "Animals" -> info("Животные", "Выбирать мирных существ", "player", "");
            case "Skip Using Item" -> info("Пропуск использования", "Не атаковать при использовании предмета", "property", "");
            case "Skip Screen Open" -> info("При открытом меню", "Приостанавливать атаку в интерфейсе", "monitor", "");
            case "Skip Behind Wall" -> info("Проверка стен", "Атаковать только видимую цель", "visuals", "");
            case "Crits Only" -> info("Только криты", "Атаковать при возможности критического удара", "property", "");
            case "Adaptive Crits" -> info("Адаптивные криты", "Подстраивать атаку под движение игрока", "activity", "");
            case "Sprint Reset" -> info("Сброс спринта", "Способ сброса спринта перед ударом", "movement", "");
            case "Blur Radius" -> info("Размытие", "Сила размытия за интерфейсом", "visuals", "");
            case "GUI Scale" -> info("Масштаб меню", "Размер интерфейса на экране", "monitor", "");
            case "Theme Blur" -> info("Цвет темы в блюре", "Добавлять оттенок выбранной темы", "theme", "");
            case "GUI Alpha" -> info("Непрозрачность GUI", "От прозрачного фона до плотного стекла", "visuals", "");
            case "GUI Shadow" -> info("Тень GUI", "Мягкая тень вокруг окна", "frame", "");
            case "GUI Shadow Strength", "HUD Shadow Strength" -> info("Интенсивность тени", "Насколько тёмной будет тень", "frame", "%");
            case "GUI Shadow Size", "HUD Shadow Size" -> info("Размер тени", "Расстояние от края панели", "frame", "");
            case "Module Corner Radius" -> info("Скругление карточек", "Радиус углов модулей", "frame", "");
            case "Background Opacity" -> info("Непрозрачность HUD", "Прозрачность панелей интерфейса", "visuals", "%");
            case "HUD Blur" -> info("Размытие HUD", "Размывать мир за виджетами", "visuals", "");
            case "HUD Shadow" -> info("Тени HUD", "Мягкая тень вокруг виджетов", "frame", "");
            case "Watermark" -> info("Watermark", "Логотип, FPS и задержка соединения", "brand", "");
            case "Watermark Logo" -> info("Логотип", "Показывать значок Coliseum", "brand", "");
            case "Watermark FPS" -> info("FPS", "Показывать частоту кадров", "monitor", "");
            case "Watermark PING" -> info("PING", "Показывать задержку соединения", "activity", "");
            case "HUD List Icons" -> info("Иконки списков", "Иконки в Potions, KeyBinds и Cooldowns", "frame", "");
            case "Show Empty HUD Lists" -> info("Пустые списки", "Показывать заголовок, когда список пуст", "frame", "");
            case "Potion Duration" -> info("Время эффектов", "Показывать оставшееся время действия", "clock", "");
            case "Keybind Keys" -> info("Клавиши", "Показывать назначенные клавиши справа", "keyboard", "");
            case "Cooldown Time" -> info("Время перезарядки", "Показывать оставшееся время справа", "clock", "");
            case "Target Equipment" -> info("Экипировка цели", "Броня и предмет в руке цели", "frame", "");
            case "Target Health" -> info("Здоровье числом", "Показывать число HP рядом с именем", "player", "");
            case "HUD Drag Tilt" -> info("Наклон при переносе", "Плавный наклон по направлению движения", "movement", "");
            case "HUD Drag Guides" -> info("Направляющие HUD", "Линии через центр перемещаемого виджета", "frame", "");
            case "HUD Snap" -> info("Привязка HUD", "Выравнивать по центру и краям экрана", "frame", "");
            case "TargetHUD" -> info("TargetHUD", "Информация о выбранной цели", "player", "");
            case "KeyBinds" -> info("Keybinds", "Активные модули с назначенной клавишей", "keyboard", "");
            case "Potions" -> info("Эффекты", "Действующие эффекты и время до окончания", "effects", "");
            case "Cooldowns" -> info("Перезарядки", "Оставшееся время восстановления предметов", "clock", "");
            case "Armor" -> info("Броня", "Экипировка и остаток прочности", "frame", "");
            case "Staff" -> info("Персонал", "Список персонала сервера", "staff", "");
            case "Notifications" -> info("Уведомления", "Изменения модулей и игровые события", "info", "");
            case "Totem Indicator" -> info("Тотемы", "Количество тотемов в инвентаре", "property", "");
            case "Coordinates" -> info("Координаты", "Позиция, скорость движения и TPS", "coords", "");
            case "Custom Hotbar" -> info("Хотбар", "Панель быстрого доступа в стиле темы", "frame", "");
            case "Music" -> info("Музыка", "Текущий трек из системного плеера", "activity", "");
            case "Music Lyrics" -> info("Текст песни", "Синхронизированная строка под обложкой", "activity", "");
            case "Music Cover" -> info("Обложка трека", "Показывать обложку слева от названия", "frame", "");
            case "Music Source" -> info("Источник музыки", "Показывать название плеера рядом с исполнителем", "activity", "");
            case "Music3D Distance" -> info("Дистанция строк", "Расстояние от камеры до новой строки в мире", "arrow", " бл.");
            case "Music3D Size" -> info("Размер текста", "Масштаб объёмных букв песни", "frame", "×");
            case "Music3D Trail" -> info("Шлейф строк", "Сколько секунд плавно исчезает предыдущая строка", "clock", " с");
            case "Music3D Glow" -> info("Свечение текста", "Мягкий контур букв в цвете выбранной темы", "theme", "");
            case "Opacity" -> info("Непрозрачность", "Интенсивность отображения эффекта", "visuals", "");
            case "Mode" -> info("Режим", "Вариант отображения эффекта", "settings", "");
            case "Speed" -> info("Скорость", "Темп воспроизведения эффекта", "activity", "");
            case "On Hover" -> info("При наведении", "Выделять цель под прицелом", "visuals", "");
            case "Aura Target" -> info("Цель AttackAura", "Отображать выделение текущей цели", "player", "");
            case "Players Only" -> info("Только игроки", "Не выделять других существ", "player", "");
            case "Hover Range" -> info("Дистанция наведения", "Радиус поиска цели под прицелом", "arrow", " бл.");
            case "Clouds & Stars" -> info("Облака и звёзды", "Дополнительный слой небесного эффекта", "visuals", "");
            case "Pretty Hands Mod" -> info("Положение предмета", "Использовать собственные смещения рук", "property", "");
            case "Item X" -> info("Смещение X", "Положение предмета по горизонтали", "arrow", "");
            case "Item Y" -> info("Смещение Y", "Положение предмета по вертикали", "arrow", "");
            case "Item Z" -> info("Смещение Z", "Расстояние до предмета", "arrow", "");
            case "Item Size" -> info("Размер предмета", "Масштаб предмета в руке", "frame", "");
            case "Swing Animation" -> info("Анимация удара", "Использовать выбранную анимацию", "activity", "");
            case "Swing Style" -> info("Стиль удара", "Способ движения предмета при ударе", "settings", "");
            case "Animation Strength" -> info("Сила анимации", "Амплитуда движения при ударе", "activity", "");
            case "DropDown Angle" -> info("Угол наклона", "Поворот предмета в анимации DropDown", "arrow", "°");
            case "DropDown Tilt" -> info("Боковой наклон", "Дополнительный наклон в анимации", "arrow", "°");
            case "Only With Aura" -> info("Только с AttackAura", "Применять анимацию при активной AttackAura", "combat", "");
            case "Hand Swap" -> info("Смена рук", "Менять отображение рук", "property", "");
            case "Glow Strength" -> info("Сила свечения", "Яркость контура и ореола", "visuals", "");
            case "Glow Width" -> info("Ширина свечения", "Размер ореола вокруг рук", "visuals", "");
            case "Wave" -> info("Волна", "Интенсивность волнового эффекта", "activity", "");
            case "Wave Speed" -> info("Скорость волны", "Скорость движения волнового эффекта", "activity", "");
            case "Theme Color" -> info("Цвет темы", "Использовать акцент интерфейса", "theme", "");
            case "Rainbow" -> info("Радужный цвет", "Плавная смена цветов эффекта", "theme", "");
            case "Color" -> info("Цвет", "Собственный оттенок эффекта", "theme", "");
            case "Item Colors" -> info("Цвета предмета", "Учитывать цвет предмета в огне", "theme", "");
            case "Trail" -> info("Шлейф", "След пламени при движении", "activity", "");
            case "Flame Height" -> info("Высота пламени", "Размер языков пламени", "arrow", "");
            case "Fire Speed" -> info("Скорость огня", "Темп движения пламени", "activity", "");
            case "Fire Glow" -> info("Свечение огня", "Интенсивность ореола пламени", "visuals", "");
            case "Copy Inventory" -> info("Копировать инвентарь", "Передавать экипировку копии игрока", "frame", "");
            case "Record Movement" -> info("Запись движения", "Записывать траекторию игрока", "activity", "");
            case "Playback" -> info("Воспроизведение", "Повторять записанное движение", "movement", "");
            case "Auto Totem" -> info("Автототем", "Автоматически выдавать тотем копии", "property", "");
            case "Behavior" -> info("Поведение", "Выбрать поведение копии игрока", "settings", "");
            default -> handSetting(setting.name());
        };
    }
    private static Info handSetting(String name) {
        String side = name.startsWith("Right ") ? "Правая рука" : name.startsWith("Left ") ? "Левая рука" : null;
        if (side == null) return info(name, "", "settings", "");
        String field = name.substring(name.indexOf(' ') + 1);
        return switch (field) {
            case "X" -> info(side + ": X", "Горизонтальное смещение руки", "arrow", "");
            case "Y" -> info(side + ": Y", "Вертикальное смещение руки", "arrow", "");
            case "Z" -> info(side + ": Z", "Расстояние до руки", "arrow", "");
            case "Width" -> info(side + ": ширина", "Ширина отображения руки", "frame", "");
            case "Size" -> info(side + ": размер", "Общий масштаб руки", "frame", "");
            default -> info(name, "", "settings", "");
        };
    }
    private static Info info(String label, String help, String icon, String unit) { return new Info(label, help, icon, unit); }
}
