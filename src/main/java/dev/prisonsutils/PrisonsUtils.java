package dev.prisonsutils;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrisonsUtils implements ModInitializer {
    public static final String MOD_ID = "prisonsutils";
    public static final String MOD_VERSION = "0.1.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(PrisonsUtils.class);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] initialized", MOD_ID);
    }
}
