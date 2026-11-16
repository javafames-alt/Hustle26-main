package im.yaHustler.command;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import im.yaHustler.Hustle26;
import im.yaHustler.bot.client.BotManager;
import im.yaHustler.module.Module;
import im.yaHustler.util.chat.ChatUtil;
import im.yaHustler.module.impl.combat.aura.rotation.neuro.NeuroRotation;
import im.yaHustler.module.impl.combat.aura.rotation.neuro.HustleModel;
import im.yaHustler.module.impl.combat.aura.rotation.neuro.HustleRecorder;
import im.yaHustler.module.impl.combat.aura.rotation.neuro.HustleTrainer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class ClientCommands {
    private ClientCommands() {}

    public static void initialize() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !execute(message));
    }

    public static boolean execute(String message) {
        if (message == null || !message.startsWith(".")) return false;
        String[] args = message.strip().split("\\s+");
        String command = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (command) {
                case ".vclip" -> vclip(args);
                case ".bind" -> bind(args);
                case ".gps" -> gps(args);
                case ".help" -> help();
                case ".bot" -> bot(message.strip());
                case ".friend" -> friend(args);
                case ".blockesp" -> blockesp(args);
                case ".autosell" -> autoSell(args);
                case ".dataset", ".neuro" -> dataSet(args);
                default -> { return false; }
            }
        } catch (Exception e) {
            say(command + " — " + e.getMessage());
        }
        return true;
    }

    private static void bot(String message) {
        String[] args = message.split("\\s+", 4);
        BotManager manager = BotManager.instance();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("stop") && args.length == 2
            || (action.equals("follow") || action.equals("mirror")) && args.length == 3 && args[2].equalsIgnoreCase("off")) {
            manager.stopGroup();
            say("Групповое управление остановлено.");
            return;
        }
        if ((action.equals("follow") || action.equals("mirror")) && args.length == 3) {
            boolean mirror = action.equals("mirror");
            manager.moveGroup(Minecraft.getInstance().level, args[2], mirror);
            say((mirror ? "Боты повторяют движение: " : "Боты следуют за игроком: ") + args[2]);
            return;
        }
        if (action.equals("chat") && args.length == 4) {
            String text = args[3];
            if (text.isBlank() || text.length() > 256) throw new IllegalArgumentException("Сообщение должно содержать от 1 до 256 символов");
            var recipients = manager.sessions().stream()
                .filter(session -> args[2].equalsIgnoreCase("all") || session.config().name().equalsIgnoreCase(args[2]))
                .filter(session -> !session.closed() && session.play() != null && session.play().worldPackets().player().positioned())
                .toList();
            if (recipients.isEmpty()) {
                say("Нет подключённых ботов с таким именем.");
                return;
            }
            recipients.forEach(session -> session.action(handler -> handler.sendChat(text)));
            say("Сообщение передано ботам: " + recipients.size());
            return;
        }
        say(".bot follow <ник> · .bot mirror <ник> · .bot stop · .bot chat <бот|all> <сообщение>");
    }

    private static void dataSet(String[] args) throws java.io.IOException {
        HustleRecorder recorder = NeuroRotation.getInstance().recorder();
        if (args.length == 2 && args[1].equalsIgnoreCase("stop")) { say(recorder.stop()); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("setup")) { HustleTrainer.setup(); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("cancel")) { HustleTrainer.cancel(); say("Обучение остановлено."); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("status")) {
            String running = HustleTrainer.runningStatus();
            if (running != null) {
                say(running);
                return;
            }
            say("Запись: " + (recorder.isRecording() ? "идёт" : "остановлена") + " · строк данных: " + HustleRecorder.totalTicks());
            say("Активная модель: " + (HustleModel.active() == null ? "не загружена" : HustleModel.activeName()));
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("record")) { recorder.start(args[2]); say("Запись в " + args[2] + ".csv начата. Для остановки введите .dataset stop"); return; }
        if (args.length == 3 && args[1].equalsIgnoreCase("use")) {
            if (!HustleModel.select(args[2])) throw new java.io.IOException("Модель не найдена или повреждена");
            NeuroRotation.getInstance().reset(); say("Выбрана модель " + args[2]); return;
        }
        if ((args.length == 3 || args.length == 4) && args[1].equalsIgnoreCase("train")) {
            var aura = Hustle26.MODULES.get(im.yaHustler.module.impl.combat.aura.AttackAura.class);
            int epochs = args.length == 4 ? Integer.parseInt(args[3]) : aura != null ? aura.dataSetTrainingEpochs() : 10;
            if (epochs < 1 || epochs > 1000) throw new java.io.IOException("Число эпох: 1–1000");
            HustleTrainer.train(args[2], epochs); return;
        }
        say(".dataset record <имя> · stop · status · setup · train <датасет> [эпохи] · use <датасет> · cancel");
    }

    private static void vclip(String[] args) {
        if (args.length != 2) { say("Использование: .vclip <блоки> (плюс — вверх, минус — вниз)"); return; }
        double offset = Double.parseDouble(args[1]);
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { say("Команда доступна только в игре."); return; }
        double y = mc.player.getY() + offset;
        if (!Double.isFinite(offset) || !Double.isFinite(y)) { say("Укажите корректное расстояние."); return; }
        double x = mc.player.getX(), z = mc.player.getZ();
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, mc.player.horizontalCollision));
        mc.player.setPos(x, y, z);
        say("Перемещение по Y: " + (offset > 0 ? "+" : "") + format(offset));
    }
    private static void autoSell(String[] args) {
        var module=Hustle26.MODULES.get(im.yaHustler.module.impl.misc.AutoSell.class);
        if(module==null)return;
        if(args.length==2 && (args[1].equalsIgnoreCase("hand")||args[1].equalsIgnoreCase("inventory"))) {
            if(Minecraft.getInstance().player==null){say("Команда доступна только в игре.");return;}
            module.setEnabled(true);module.start(args[1].equalsIgnoreCase("inventory"));say("AutoSell: запуск / остановка продажи.");return;
        }
        if(args.length==2 && args[1].equalsIgnoreCase("stop")){module.setEnabled(false);say("AutoSell остановлен.");return;}
        say(".autosell hand — продать из руки; .autosell inventory — продать инвентарь и содержимое шалкеров; .autosell stop");
    }
    private static void blockesp(String[] args) {
        var module=Hustle26.MODULES.get(im.yaHustler.module.impl.render.BlockESP.class);
        if(module==null)return;
        if(args.length==2 && args[1].equalsIgnoreCase("clear")) { module.clearBlocks();say("Список BlockESP очищен.");return; }
        if(args.length==2 && args[1].equalsIgnoreCase("list")) { say("BlockESP: "+(module.blockIds().isEmpty()?"список пуст":String.join(", ",module.blockIds())));return; }
        if(args.length==3 && args[1].equalsIgnoreCase("add")) {
            try { boolean added=module.addBlock(args[2]);module.setEnabled(true);say(added?"Добавлен блок: "+args[2]:"Блок уже добавлен."); }
            catch(IllegalArgumentException error) { say(error.getMessage()); }
            return;
        }
        if(args.length==3 && args[1].equalsIgnoreCase("remove")) { say(module.removeBlock(args[2])?"Блок удалён: "+args[2]:"Блок не найден в списке.");return; }
        say(".blockesp add <блок> · .blockesp remove <блок> · .blockesp clear · .blockesp list");
    }
    private static void friend(String[] args) {
        try {
            if (args.length == 2 && args[1].equalsIgnoreCase("clear")) { im.yaHustler.friend.Friends.clear(); say("Список друзей очищен."); return; }
            if (args.length == 2 && args[1].equalsIgnoreCase("list")) { say("Друзья: " + String.join(", ",im.yaHustler.friend.Friends.all())); return; }
            if (args.length == 3 && im.yaHustler.friend.Friends.valid(args[2])) {
                if (args[1].equalsIgnoreCase("add")) { say(im.yaHustler.friend.Friends.add(args[2]) ? "Добавлен в друзья: " + args[2] : "Уже в друзьях: " + args[2]); return; }
                if (args[1].equalsIgnoreCase("remove")) { say(im.yaHustler.friend.Friends.remove(args[2]) ? "Удалён из друзей: " + args[2] : "Ник не найден."); return; }
            }
            say(".friend add <ник> · .friend remove <ник> · .friend clear · .friend list");
        } catch (java.io.IOException error) { say("Не удалось сохранить друзей: " + error.getMessage()); }
    }

    private static void bind(String[] args) {
        if (args.length < 2) { bindHelp(); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length != 4) { bindHelp(); return; }
                Module module = module(args[2]);
                if (module == null) { say("Модуль не найден: " + args[2]); return; }
                Integer key = parseKey(args[3]);
                if (key == null) { say("Неизвестная клавиша: " + args[3]); return; }
                module.setKey(key);
                say("Модуль " + module.name() + " привязан к " + module.keyName());
            }
            case "remove" -> {
                if (args.length != 3) { bindHelp(); return; }
                Module module = module(args[2]);
                if (module == null) { say("Модуль не найден: " + args[2]); return; }
                if (!module.hasKey()) { say("У модуля " + module.name() + " нет бинда."); return; }
                String key = module.keyName(); module.setKey(-1);
                say("Модуль " + module.name() + " отвязан от " + key);
            }
            case "list" -> {
                if (args.length != 2) { bindHelp(); return; }
                var bound = Hustle26.MODULES.all().stream().filter(Module::hasKey).toList();
                if (bound.isEmpty()) { say("Список биндов пуст."); return; }
                say("Список биндов:");
                for (Module module : bound) say(module.name() + " → " + module.keyName());
            }
            case "clear" -> {
                if (args.length != 2) { bindHelp(); return; }
                for (Module module : Hustle26.MODULES.all()) if (module.hasKey()) module.setKey(-1);
                say("Список биндов очищен.");
            }
            default -> bindHelp();
        }
    }

    private static void gps(String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            GpsMarker.clear(); say("GPS отключён."); return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            say("GPS-метка игрока " + args[2] + (GpsMarker.removePlayerTarget(args[2]) ? " удалена." : " не найдена.")); return;
        }
        if (args.length != 3) { say("Использование: .gps <x> <z> или .gps off"); return; }
        double x = Double.parseDouble(args[1]), z = Double.parseDouble(args[2]);
        if (!Double.isFinite(x) || !Double.isFinite(z)) { say("Укажите корректные координаты."); return; }
        GpsMarker.set(x, z);
        say("GPS установлен на X: " + format(x) + " Z: " + format(z));
    }

    public static Integer parseKey(String input) {
        String name = input.toUpperCase(Locale.ROOT);
        if (name.matches("MOUSE[1-8]")) {
            int button = name.charAt(5) - '0';
            if (button == 1) button = InputConstants.MOUSE_BUTTON_LEFT;
            else if (button == 2) button = InputConstants.MOUSE_BUTTON_RIGHT;
            else if (button == 3) button = InputConstants.MOUSE_BUTTON_MIDDLE;
            return -100 - button;
        }
        name = switch (name) {
            case "ENTER" -> "RETURN";
            case "LCTRL" -> "LCONTROL";
            case "RCTRL" -> "RCONTROL";
            case "LSUPER" -> "LGUI";
            case "RSUPER" -> "RGUI";
            default -> name.replace("_", "");
        };
        try { return InputConstants.class.getField("KEY_" + name).getInt(null); }
        catch (ReflectiveOperationException e) { return null; }
    }

    private static Module module(String name) {
        for (Module module : Hustle26.MODULES.all()) if (module.name().equalsIgnoreCase(name) || module.id().equalsIgnoreCase(name)) return module;
        return null;
    }
    private static String format(double value) { return value == Math.rint(value) && Math.abs(value) <= Long.MAX_VALUE ? Long.toString((long)value) : Double.toString(value); }
    private static void bindHelp() { say("Использование: .bind add <модуль> <клавиша>, .bind remove <модуль>, .bind list, .bind clear"); }
    public static net.minecraft.network.chat.MutableComponent prefixed(String message) {
        return net.minecraft.network.chat.Component.empty()
            .append(net.minecraft.network.chat.Component.literal("Hustle 26.3").withStyle(style -> style.withBold(true).withColor(im.yaHustler.module.impl.themes.Theme.accent())))
            .append(net.minecraft.network.chat.Component.literal(" " + message).withStyle(style -> style.withBold(false).withColor(0xffe6e9ef)));
    }
    private static void help() {
        var message = prefixed("Command");
        String[][] entries = {
            {".help", "Показать список команд"},
            {".bot", "Следование, повтор движения и чат ботов"},
            {".dataset record|stop|status|setup|train|use|cancel", "Запись датасета и обучение модели"},
            {".autosell hand|inventory|stop", "Продать предмет из руки, инвентарь или остановить продажу"},
            {".blockesp add|remove <блок>", "Добавить или удалить подсветку блока, например diamond_ore"},
            {".blockesp clear|list", "Очистить или показать список BlockESP"},
            {".friend add|remove <ник>", "Добавить или удалить друга"},
            {".friend clear|list", "Очистить или показать список друзей"},
            {".vclip <число>", "Сместиться по вертикали: .vclip +5 — вверх, .vclip -5 — вниз"},
            {".gps <x> <z>", "Поставить GPS-метку на координатах"},
            {".gps off", "Убрать GPS-метку"},
            {".gps remove <ник>", "Удалить GPS-метку игрока"},
            {".bind add <модуль> <клавиша>", "Назначить клавишу, например .bind add Sprint R"},
            {".bind remove <модуль>", "Убрать привязку клавиши"},
            {".bind list", "Показать назначенные клавиши"},
            {".bind clear", "Убрать все привязки"},
        };
        for (var entry : entries) message.append(net.minecraft.network.chat.Component.literal("\n  " + entry[0]).withColor(0xfff0f2f6))
            .append(net.minecraft.network.chat.Component.literal(" — " + entry[1]).withColor(0xff9da7b7));
        ChatUtil.sendMessage(message);
    }
    private static void say(String message) { ChatUtil.sendMessage(prefixed(message)); }
}
