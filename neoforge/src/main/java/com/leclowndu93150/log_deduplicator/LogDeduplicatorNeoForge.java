package com.leclowndu93150.log_deduplicator;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

@Mod(LogDeduplicatorCommon.MOD_ID)
public class LogDeduplicatorNeoForge {
    private static final Logger LOGGER = LogDeduplicatorCommon.LOGGER;
    private static final boolean IS_DEV = !FMLLoader.isProduction();

    public LogDeduplicatorNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        LogDeduplicatorCommon.init(FMLPaths.CONFIGDIR.get());

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);

        if (FMLLoader.getDist() == Dist.CLIENT) {
            registerConfigScreen(modContainer);
        }
    }

    private static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
            net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            (container, parent) -> ConfigScreen.create(parent));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(LogDeduplicatorCommon::lateInit);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (IS_DEV) {
            System.out.println("HI 1");
            LOGGER.info("HI 2");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LogDeduplicatorCommon.shutdown();
    }
}
