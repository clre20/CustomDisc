package clre20.customDisc.pack;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 產生 Java 版資源包（.zip）與基岩版資源包（.mcpack）。
 * 內容固定排序、時間戳固定，所以內容沒變時雜湊值也不會變，玩家不用重新下載。
 */
public final class PackBuilder {

    public static final String NS = DatapackService.NAMESPACE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long FIXED_TIME = 1_700_000_000_000L;

    private static final UUID BEDROCK_HEADER = UUID.nameUUIDFromBytes("customdisc:bedrock:header".getBytes(StandardCharsets.UTF_8));
    private static final UUID BEDROCK_MODULE = UUID.nameUUIDFromBytes("customdisc:bedrock:module".getBytes(StandardCharsets.UTF_8));

    /** 打包結果。 */
    public record Built(byte[] bytes, byte[] sha1, String sha1Hex) {
    }

    private PackBuilder() {
    }

    /** 把聲音的設定和音檔對起來；找不到音檔的會被略過並記錄警告。 */
    public static Map<CustomSound, SoundLibrary.Entry> resolve(List<CustomSound> sounds, SoundLibrary library,
                                                              Consumer<String> warn) {
        Map<CustomSound, SoundLibrary.Entry> map = new LinkedHashMap<>();
        for (CustomSound s : sounds) {
            SoundLibrary.Entry e = library.find(s.soundFile());
            if (e == null || !e.valid()) {
                warn.accept(s.type().displayName() + " " + s.name() + " 的音檔「" + s.soundFile() + "」不存在或無法讀取，暫時不會發出聲音");
                continue;
            }
            map.put(s, e);
        }
        return map;
    }

    /** 不重複的音檔清單（id 相同的只取第一個）。 */
    private static List<SoundLibrary.Entry> uniqueFiles(List<SoundLibrary.Entry> entries, Consumer<String> warn) {
        Map<String, SoundLibrary.Entry> byId = new LinkedHashMap<>();
        for (SoundLibrary.Entry e : entries) {
            if (!e.valid()) {
                continue;
            }
            SoundLibrary.Entry prev = byId.putIfAbsent(e.id(), e);
            if (prev != null && !prev.path().equals(e.path())) {
                warn.accept("音檔「" + e.path() + "」和「" + prev.path() + "」轉換後名稱相同，只會使用後者");
            }
        }
        return new ArrayList<>(byId.values());
    }

    // ------------------------------------------------------------------ Java

    public static Built buildJava(List<SoundLibrary.Entry> files, Map<CustomSound, SoundLibrary.Entry> sounds,
                                  Consumer<String> warn) throws IOException {
        List<SoundLibrary.Entry> unique = uniqueFiles(files, warn);
        JsonObject soundsJson = new JsonObject();

        // 每個音檔都有一個試聽用的事件 customdisc:file.<id>
        for (SoundLibrary.Entry e : unique) {
            soundsJson.add("file." + e.id(), javaEvent(e, 1.0, 16, true));
        }
        for (Map.Entry<CustomSound, SoundLibrary.Entry> en : sounds.entrySet()) {
            CustomSound s = en.getKey();
            SoundLibrary.Entry e = en.getValue();
            double volume = s.volume() / 100.0;
            if (s.type() == SoundType.DISC) {
                // 唱片機播放時音量是 4，所以衰減距離 = 範圍 / 4
                int attenuation = Math.max(1, (int) Math.ceil(s.range() / 4.0));
                soundsJson.add(s.soundEvent(), javaEvent(e, volume, attenuation, true));
            } else {
                soundsJson.add(s.soundEvent(), javaEvent(e, volume, 16, s.length() > 10));
            }
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pack.mcmeta", json(javaMeta()));
        entries.put("assets/" + NS + "/sounds.json", json(soundsJson));
        for (SoundLibrary.Entry e : unique) {
            entries.put("assets/" + NS + "/sounds/f/" + e.id() + ".ogg", Files.readAllBytes(e.file().toPath()));
        }
        byte[] zip = zip(entries);
        byte[] sha1 = digest("SHA-1", zip);
        return new Built(zip, sha1, hex(sha1));
    }

    private static JsonObject javaEvent(SoundLibrary.Entry e, double volume, int attenuation, boolean stream) {
        JsonObject sound = new JsonObject();
        sound.addProperty("name", NS + ":f/" + e.id());
        sound.addProperty("stream", stream);
        sound.addProperty("volume", volume);
        sound.addProperty("attenuation_distance", attenuation);
        JsonArray list = new JsonArray();
        list.add(sound);
        JsonObject event = new JsonObject();
        event.add("sounds", list);
        return event;
    }

    private static JsonObject javaMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "CustomDisc 自訂唱片音樂");
        pack.addProperty("pack_format", 34);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", 34);
        supported.addProperty("max_inclusive", 9999);
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", 34);
        pack.addProperty("max_format", 9999);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    // --------------------------------------------------------------- Bedrock

    public static Built buildBedrock(List<SoundLibrary.Entry> files, Map<CustomSound, SoundLibrary.Entry> sounds,
                                     Consumer<String> warn) throws IOException {
        List<SoundLibrary.Entry> unique = uniqueFiles(files, warn);
        JsonObject defs = new JsonObject();
        for (SoundLibrary.Entry e : unique) {
            JsonObject d = bedrockDef(e, 1.0, 16, true);
            defs.add(NS + ":file." + e.id(), d);
            defs.add(NS + ".file." + e.id(), d);
        }
        for (Map.Entry<CustomSound, SoundLibrary.Entry> en : sounds.entrySet()) {
            CustomSound s = en.getKey();
            JsonObject d = bedrockDef(en.getValue(), s.volume() / 100.0, s.range(),
                    s.type() == SoundType.DISC || s.length() > 10);
            // Geyser 會把 customdisc:disc.xxx 原樣轉給基岩版，另外加一個不含冒號的名稱備用
            defs.add(NS + ":" + s.soundEvent(), d);
            defs.add(NS + "." + s.soundEvent(), d);
        }
        JsonObject soundDefs = new JsonObject();
        soundDefs.addProperty("format_version", "1.14.0");
        soundDefs.add("sound_definitions", defs);

        Map<String, byte[]> content = new LinkedHashMap<>();
        content.put("sounds/sound_definitions.json", json(soundDefs));
        for (SoundLibrary.Entry e : unique) {
            content.put("sounds/" + NS + "/" + e.id() + ".ogg", Files.readAllBytes(e.file().toPath()));
        }

        // 版本號由內容決定：內容變了版本號就跟著變，基岩版玩家才會重新下載
        byte[] contentHash = digest("SHA-256", zip(content));
        int a = (contentHash[0] & 0x7F) << 8 | (contentHash[1] & 0xFF);
        int b = (contentHash[2] & 0x7F) << 8 | (contentHash[3] & 0xFF);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", json(bedrockManifest(a, b)));
        entries.putAll(content);
        byte[] zip = zip(entries);
        byte[] sha1 = digest("SHA-1", zip);
        return new Built(zip, sha1, hex(sha1));
    }

    private static JsonObject bedrockDef(SoundLibrary.Entry e, double volume, int range, boolean stream) {
        JsonObject sound = new JsonObject();
        sound.addProperty("name", "sounds/" + NS + "/" + e.id());
        sound.addProperty("stream", stream);
        sound.addProperty("volume", volume);
        sound.addProperty("load_on_low_memory", true);
        JsonArray list = new JsonArray();
        list.add(sound);
        JsonObject def = new JsonObject();
        def.addProperty("category", "record");
        def.addProperty("max_distance", (float) range);
        def.add("sounds", list);
        return def;
    }

    private static JsonObject bedrockManifest(int a, int b) {
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(a);
        version.add(b);
        JsonArray engine = new JsonArray();
        engine.add(1);
        engine.add(20);
        engine.add(0);

        JsonObject header = new JsonObject();
        header.addProperty("name", "CustomDisc");
        header.addProperty("description", "CustomDisc 自訂唱片音樂");
        header.addProperty("uuid", BEDROCK_HEADER.toString());
        header.add("version", version);
        header.add("min_engine_version", engine);

        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty("uuid", BEDROCK_MODULE.toString());
        module.add("version", version.deepCopy());
        JsonArray modules = new JsonArray();
        modules.add(module);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", 2);
        root.add("header", header);
        root.add("modules", modules);
        return root;
    }

    // --------------------------------------------------------------- 工具

    private static byte[] json(Object o) {
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(FIXED_TIME);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] digest(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 給 resolve 用的空 Map（避免 null）。 */
    static Map<CustomSound, SoundLibrary.Entry> empty() {
        return new HashMap<>();
    }
}
