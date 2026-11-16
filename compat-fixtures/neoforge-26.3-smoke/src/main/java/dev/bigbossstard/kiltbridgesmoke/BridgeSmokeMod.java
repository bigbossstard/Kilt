package dev.bigbossstard.kiltbridgesmoke;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Minimal, unmodified NeoForge 26.3 mod used to exercise the Fabric-hosted bridge.
 *
 * It deliberately touches only three contracts:
 * 1. JavaFML @Mod discovery and constructor injection;
 * 2. mod event-bus registration and FMLCommonSetupEvent delivery;
 * 3. deferred item registration and registry lookup.
 */
@Mod(BridgeSmokeMod.MOD_ID)
public final class BridgeSmokeMod {
    public static final String MOD_ID = "kilt_bridge_smoke";

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);

    private static final DeferredItem<Item> PROBE_ITEM =
            ITEMS.registerSimpleItem("probe_item");

    public BridgeSmokeMod(IEventBus modEventBus, ModContainer modContainer) {
        System.out.println("[KILT-BRIDGE-SMOKE] MOD_CONSTRUCTED");

        ITEMS.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        System.out.println("[KILT-BRIDGE-SMOKE] COMMON_SETUP");
        System.out.println(
                "[KILT-BRIDGE-SMOKE] ITEM_REGISTERED="
                        + BuiltInRegistries.ITEM.getKey(PROBE_ITEM.get())
        );
    }
}
