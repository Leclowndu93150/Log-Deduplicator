package com.leclowndu93150.log_deduplicator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public class LogDeduplicatorFabric implements ModInitializer {
    private static final Logger LOGGER = LogDeduplicatorCommon.LOGGER;
    private static final boolean IS_DEV = FabricLoader.getInstance().isDevelopmentEnvironment();

    @Override
    public void onInitialize() {
        LogDeduplicatorCommon.init(FabricLoader.getInstance().getConfigDir());

        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            LogDeduplicatorCommon.lateInit();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LogDeduplicatorCommon.shutdown();
        });

        if (IS_DEV) {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                System.out.println("HI 1");
                LOGGER.info("HI 2");
            });
        }
    }
}
