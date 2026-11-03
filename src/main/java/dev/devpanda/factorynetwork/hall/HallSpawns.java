package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * No mob spawns in a hall by itself.
 *
 * <p>The hall's biome has no spawn list, so vanilla spawns nothing there. A
 * pack can still add spawns to biomes with a biome modifier, and some mods
 * spawn on their own; both go through this check. Only natural spawning is
 * refused: an egg, a spawner or a summon still works, as in Compact Machines.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class HallSpawns {

    private HallSpawns() {
    }

    @SubscribeEvent
    public static void refuseNaturalSpawns(MobSpawnEvent.PositionCheck event) {
        if (event.getLevel().getLevel().dimension() != Halls.DIMENSION) {
            return;
        }
        EntitySpawnReason reason = event.getSpawnType();
        if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
                || reason == EntitySpawnReason.PATROL || reason == EntitySpawnReason.EVENT) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
