package morfeov.coliseum.hud;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public final class MediaSession {

    private static final long POLL_MS = 350L;
    private static final String SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            Add-Type -AssemblyName System.Runtime.InteropServices.WindowsRuntime
            $null = [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($WinRtTask, $ResultType) {
                $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
                $netTask = $asTask.Invoke($null, @($WinRtTask))
                $netTask.Wait(-1) | Out-Null
                $netTask.Result
            }
            [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null
            $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            $session = $manager.GetCurrentSession()
            if ($session -eq $null) { exit }
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $title = $props.Title
            $artist = $props.Artist
            $source = $session.SourceAppUserModelId
            $st = if ($playback -ne $null) { [string]$playback.PlaybackStatus } else { '' }
            $pos = 0
            $dur = 0
            if ($timeline -ne $null) {
                $basePos = [int64]$timeline.Position.TotalMilliseconds
                $dur = [int64]$timeline.EndTime.TotalMilliseconds
                $pos = $basePos
                if ($st -eq 'Playing' -or $st -eq '4') {
                    $now = [DateTimeOffset]::UtcNow
                    $last = $timeline.LastUpdatedTime.ToUniversalTime()
                    $elapsed = ($now - $last).TotalMilliseconds
                    if ($elapsed -gt 0 -and $elapsed -lt 86400000) {
                        $pos = $basePos + [int64]$elapsed
                    }
                }
                if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
            }
            $dir = $PSScriptRoot
            if (-not $dir) { $dir = '.' }
            $coverPath = Join-Path $dir 'cover.png'
            $stampPath = Join-Path $dir 'cover.txt'
            $key = "$artist|$title"
            $coverStamp = ''
            $known = ''
            if (Test-Path $stampPath) { $known = (Get-Content $stampPath -Raw).Trim() }
            if ($known -eq $key -and (Test-Path $coverPath)) {
                $coverStamp = $key
            } elseif ($props.Thumbnail -ne $null) {
                try {
                    $ref = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $asStreamMethod = ([System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 })[0]
                    $netStream = $asStreamMethod.Invoke($null, @($ref))
                    $ms = New-Object System.IO.MemoryStream
                    $netStream.CopyTo($ms)
                    $bytes = $ms.ToArray()
                    [System.IO.File]::WriteAllBytes($coverPath, $bytes)
                    $netStream.Dispose()
                    $ms.Dispose()
                    Set-Content -Path $stampPath -Value $key -NoNewline
                    $coverStamp = $key
                } catch {
                    Set-Content -Path (Join-Path $dir 'cover_error.txt') -Value $_.Exception.ToString()
                }
            }
            Write-Output "$title`t$artist`t$source`t$pos`t$dur`t$st`t$coverStamp"
            """;

    private static volatile String title = "";
    private static volatile String artist = "";
    private static volatile String source = "";
    private static volatile Playback playback = new Playback(0,0,false,0);
    private static volatile boolean present;
    private static volatile String coverKey = "";
    private static volatile byte[] artwork;
    private static Thread worker;
    private static volatile long lastWanted;

    private MediaSession() {
    }

    /** One authoritative player sample; seeking replaces it immediately, without clock drift. */
    record Playback(long position, long duration, boolean playing, long readAtNanos) {
        long positionAt(long nowNanos) {
            long elapsed=playing?Math.max(0,nowNanos-readAtNanos)/1_000_000L:0;
            long result=Math.max(0,position+elapsed);
            return duration>0?Math.min(result,duration):result;
        }
    }

    public static void keepAlive() {
        lastWanted = System.currentTimeMillis();
        start();
    }

    public static boolean present() {
        return present;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    public static String source() {
        return prettySource(source);
    }

    public static String coverKey() {
        return artwork != null && artwork.length > 0 ? title + "|" + artist : coverKey;
    }

    public static byte[] artwork() {
        return artwork;
    }

    public static File coverFile() {
        File folder = new File(Minecraft.getInstance().gameDirectory, "coliseum/media");
        return new File(folder, "cover.png");
    }

    public static boolean playing() {
        return playback.playing();
    }

    public static long durationMs() {
        return playback.duration();
    }

    public static long positionMs() {
        return playback.positionAt(System.nanoTime());
    }

    private static synchronized void start() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(MediaSession::loop, "Coliseum-Media");
        worker.setDaemon(true);
        worker.start();
    }

    private static void loop() {
        while (System.currentTimeMillis() - lastWanted < 5000L) {
            poll();
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
        present = false;
    }

    private static void poll() {
        try {
            File script = scriptFile();
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-File", script.getAbsolutePath());
            builder.redirectErrorStream(false);
            Process process = builder.start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }
            process.waitFor();
            if (line == null || line.isBlank()) {
                fallback();
                return;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                fallback();
                return;
            }
            coverKey = parts.length > 6 ? parts[6].trim() : "";
            title = parts[0].trim();
            artist = parts[1].trim();
            if (artist.isEmpty() && title.contains(" - ")) {
                int dash = title.indexOf(" - ");
                artist = title.substring(0, dash).trim();
                title = title.substring(dash + 3).trim();
            }
            source = parts[2].trim();
            String st = parts[5].trim();
            playback = new Playback(parseLong(parts[3]),parseLong(parts[4]),
                    st.equalsIgnoreCase("Playing") || st.equals("4"),System.nanoTime());
            present = !title.isEmpty();
        } catch (Exception exception) {
            fallback();
        }
    }

    private static void fallback() {
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-Command", "Get-Process spotify,aimp,foobar2000,vlc,yandexmusic,'VK Музыка' "
                    + "-ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | "
                    + "Select-Object -First 1 | ForEach-Object { \"$($_.ProcessName)`t$($_.MainWindowTitle)\" }");
            Process process = builder.start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }
            process.waitFor();
            if (line == null || line.isBlank()) {
                present = false;
                return;
            }
            String[] parts = line.split("\t", -1);
            String window = parts.length > 1 ? parts[1].trim() : "";
            if (window.isEmpty() || window.equalsIgnoreCase(parts[0].trim())) {
                present = false;
                return;
            }
            int dash = window.indexOf(" - ");
            artist = dash > 0 ? window.substring(0, dash).trim() : "";
            title = dash > 0 ? window.substring(dash + 3).trim() : window;
            source = parts[0].trim();
            playback = new Playback(0,0,true,System.nanoTime());
            present = !title.isEmpty();
        } catch (Exception exception) {
            present = false;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static File scriptFile() throws Exception {
        File folder = new File(Minecraft.getInstance().gameDirectory, "coliseum/media");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File script = new File(folder, "media.ps1");
        String current = script.exists() ? Files.readString(script.toPath(), StandardCharsets.UTF_8) : "";
        if (!current.equals(SCRIPT)) {
            Files.writeString(script.toPath(), SCRIPT, StandardCharsets.UTF_8);
        }
        return script;
    }

    private static String prettySource(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("spotify")) return "Spotify";
        if (lower.contains("soundcloud")) return "SoundCloud";
        if (lower.contains("yandex") || lower.contains("music.yandex")) return "Яндекс Музыка";
        if (lower.contains("vk")) return "VK Музыка";
        if (lower.contains("deezer")) return "Deezer";
        if (lower.contains("tidal")) return "TIDAL";
        if (lower.contains("apple") || lower.contains("itunes")) return "Apple Music";
        if (lower.contains("youtube")) return "YouTube";
        if (lower.contains("chrome")) return "Chrome";
        if (lower.contains("msedge") || lower.contains("edge")) return "Edge";
        if (lower.contains("firefox")) return "Firefox";
        if (lower.contains("opera")) return "Opera";
        if (lower.contains("zen")) return "Zen";
        if (lower.contains("aimp")) return "AIMP";
        if (lower.contains("foobar")) return "foobar2000";
        if (lower.contains("vlc")) return "VLC";
        int dot = id.indexOf('.');
        String name = dot > 0 ? id.substring(0, dot) : id;
        name = name.replace("exe", "").replace("!", "").trim();
        return name.isEmpty() ? "Музыка" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
