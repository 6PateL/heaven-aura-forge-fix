package com.example.heavenaura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * config/heaven_aura.json
 * Amplifier рахується від 0: 0 = рівень I, 2 = рівень III, 3 = рівень IV. Значення -1 вимикає ефект.
 */
public final class AuraConfig {
    public boolean enabled = true;

    /** Предмети, які вмикають ауру, поки лежать у руці. Дізнатись id: F3+H у грі. */
    public List<String> items = new ArrayList<>(List.of("timelesspower:made_in_heaven"));
    public boolean mainHandOnly = false;          // true = лише основна рука (друга рука не рахується)

    public int radius = 50;                       // радіус дії, блоків
    public int refreshTicks = 20;                 // як часто оновлювати (20 = раз на секунду)
    // Ефекти безкінечні, поки артефакт у руці, і знімаються, коли його прибрали або вийшли з радіусу.
    // Так прискорення часу від Hourglass (Made in Heaven) не може їх «з'їсти» і вони не мигають.

    public int strengthAmplifier = 2;             // Сила III
    public int resistanceAmplifier = 3;           // Стійкість IV (-80% урону)
    public int regenerationAmplifier = 1;         // Регенерація II
    public List<String> extraEffects = new ArrayList<>(); // додаткові, наприклад "minecraft:speed:1"

    public boolean affectPlayers = true;          // інші гравці
    public boolean affectMobs = true;             // неворожі моби, пет-тварини, жителі, големи, рекрути
    public int maxMobTargets = 100;               // стеля мобів за один імпульс (захист від лагів)
    public boolean skipAggroedMobs = true;        // не бафати тих, хто зараз атакує гравця

    /** Ціна: носій втрачає їжу. 4.0 виснаження = 1 пункт голоду. 0 = безкоштовно. */
    public double holderExhaustionPerSecond = 0.2;

    private static AuraConfig instance = new AuraConfig();
    public static AuraConfig get() { return instance; }

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("heaven_aura.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) {
                    AuraConfig loaded = gson.fromJson(r, AuraConfig.class);
                    if (loaded != null) instance = loaded;
                }
            }
            try (Writer w = Files.newBufferedWriter(path)) {
                gson.toJson(instance, w);
            }
        } catch (IOException | JsonParseException e) {
            HeavenAura.LOGGER.error("Не вдалося прочитати конфіг, використовую стандартний", e);
        }
    }
}
