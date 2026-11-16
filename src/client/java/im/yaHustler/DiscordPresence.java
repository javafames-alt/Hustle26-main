package im.yaHustler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local Discord RPC; never uses an account token or blocks the game thread. */
public final class DiscordPresence {
    private static volatile boolean running;
    private static volatile RandomAccessFile connection;
    private static Thread worker;

    private DiscordPresence() {}

    public static synchronized void start() {
        if (running || !System.getProperty("os.name", "").startsWith("Windows")) return;
        running = true;
        worker = new Thread(DiscordPresence::run, "Hustle 26.3 Discord RPC");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        close();
        if (worker != null) worker.interrupt();
    }

    private static void run() {
        while (running) {
            try {
                RandomAccessFile pipe = open();
                connection = pipe;
                if (!running) { close(); break; }
                try (pipe) {
                    JsonObject hello = new JsonObject();
                    hello.addProperty("v", 1);
                    hello.addProperty("client_id", "1540090600316080258");
                    send(pipe, 0, hello.toString());
                    boolean published = false;
                    while (running) {
                        int opcode = Integer.reverseBytes(pipe.readInt());
                        int length = Integer.reverseBytes(pipe.readInt());
                        if (length < 0 || length > 1_048_576) throw new IOException("Invalid RPC frame");
                        byte[] payload = new byte[length];
                        pipe.readFully(payload);
                        String body = new String(payload, StandardCharsets.UTF_8);
                        if (opcode == 2) break;
                        if (opcode == 3) { send(pipe, 4, body); continue; }
                        if (opcode != 1) continue;
                        JsonObject event = JsonParser.parseString(body).getAsJsonObject();
                        if (!published && event.has("evt") && !event.get("evt").isJsonNull() && "READY".equals(event.get("evt").getAsString())) {
                            send(pipe, 1, activity().toString());
                            published = true;
                        }
                        if (event.has("evt") && !event.get("evt").isJsonNull() && "ERROR".equals(event.get("evt").getAsString()))
                            Hustle26.LOGGER.warn("Discord rejected Rich Presence: {}", body);
                    }
                }
            } catch (Exception ignored) {
                // Discord may be closed or restarted; reconnect without affecting Minecraft.
            } finally { close(); }
            if (running) try { Thread.sleep(10_000); } catch (InterruptedException ignored) { break; }
        }
    }

    private static RandomAccessFile open() throws IOException {
        for (int i = 0; i < 10; i++) {
            try { return new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"); }
            catch (IOException ignored) { }
        }
        throw new IOException("Discord desktop is not running");
    }

    private static JsonObject activity() {
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "https://cdn.jsdelivr.net/gh/javafames-alt/Coliseum-@main/17624626104982.gif");
        assets.addProperty("large_text", "Hustle 26.3");
        JsonObject activity = new JsonObject();
        activity.addProperty("name", "Hustle 26.3");
        activity.addProperty("type", 0);
        activity.addProperty("details", "Build:Beta");
        activity.addProperty("state", "Role:Dev");
        activity.add("assets", assets);
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.addProperty("nonce", UUID.randomUUID().toString());
        command.add("args", args);
        return command;
    }

    private static void send(RandomAccessFile pipe, int opcode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        pipe.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(bytes.length).array());
        pipe.write(bytes);
    }

    private static void close() {
        RandomAccessFile pipe = connection;
        connection = null;
        if (pipe != null) try { pipe.close(); } catch (IOException ignored) { }
    }
}
