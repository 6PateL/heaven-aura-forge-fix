package com.example.heavenaura;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HeavenAura.MOD_ID)
public class HeavenAura {
    public static final String MOD_ID = "heaven_aura";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeavenAura() {
        AuraConfig.load();
        LOGGER.info("Heaven Aura loaded");
    }
}
