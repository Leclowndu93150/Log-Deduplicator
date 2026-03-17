package com.leclowndu93150.log_deduplicator;

import com.leclowndu93150.log_deduplicator.core.DeduplicationFilter;
import com.leclowndu93150.log_deduplicator.core.DeduplicationManager;
import com.leclowndu93150.log_deduplicator.stream.SmartPrintStream;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;

import java.io.PrintStream;

@Mod(LogDeduplicator.MODID)
public class LogDeduplicator {
    public static final String MODID = "log_deduplicator";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static DeduplicationFilter FILTER;

    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static SmartPrintStream dedupOut;
    private static SmartPrintStream dedupErr;

    public LogDeduplicator(IEventBus modEventBus, ModContainer modContainer) {
        Config.init();
        injectDeduplicationFilter();
        wrapSystemStreams();

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Log Deduplicator initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(this::injectDeduplicationFilter);
    }

    private void injectDeduplicationFilter() {
        try {
            if (FILTER == null) {
                FILTER = new DeduplicationFilter();
                FILTER.start();
            }

            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);

            ctx.getConfiguration().addFilter(FILTER);

            ctx.updateLoggers();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void wrapSystemStreams() {
        if (originalOut != null) {
            return;
        }

        try {
            originalOut = System.out;
            originalErr = System.err;

            dedupOut = new SmartPrintStream(originalOut, false);
            dedupErr = new SmartPrintStream(originalErr, true);

            System.setOut(dedupOut);
            System.setErr(dedupErr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void restoreSystemStreams() {
        if (originalOut != null && originalErr != null) {
            try {
                System.setOut(originalOut);
                System.setErr(originalErr);

                if (dedupOut != null) {
                    dedupOut.flush();
                }
                if (dedupErr != null) {
                    dedupErr.flush();
                }

                LOGGER.info("System streams restored");
            } catch (Exception e) {
                LOGGER.error("Failed to restore System streams", e);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        System.out.println("HI 1");
        LOGGER.info("HI 2");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (Config.systemStreamsEnabled) {
            restoreSystemStreams();
        }
        DeduplicationManager.getInstance().shutdown();
    }
}
