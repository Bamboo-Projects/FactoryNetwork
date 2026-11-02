package dev.devpanda.factorynetwork.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Development-only container for GameTests; never included in FactoryNetwork's release jar. */
@Mod("factorynetwork_tests")
public final class FactoryNetworkTestMod {
    public FactoryNetworkTestMod(IEventBus modBus) {
        GameTests.register(modBus);
    }
}
