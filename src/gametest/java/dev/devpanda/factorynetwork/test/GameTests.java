package dev.devpanda.factorynetwork.test;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Registers the annotated game tests with the test system of 26.1.
 *
 * <p>Each {@link GameTest} method becomes a test function in the registry
 * and a test instance pointing at it, named after the class and the method,
 * as the tests were named before.
 */
final class GameTests {

    /** The classes holding tests. A new class goes here. */
    private static final List<String> HOLDERS = List.of(
            "FactoryNetworkGameTests",
            "HandoffGameTests",
            "EditorMultiplayerGameTests",
            "StorageLinkGameTests",
            "WornDeviceGameTests",
            "EssentiaGameTests",
            // Compiled only with Mekanism; missing otherwise.
            "MekanismGameTests");

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, FactoryNetwork.MOD_ID);

    private record Found(String name, Method method, GameTest annotation) {
    }

    private static final List<Found> FOUND = new ArrayList<>();

    private GameTests() {
    }

    static void register(IEventBus modBus) {
        for (String holderName : HOLDERS) {
            Class<?> holder;
            try {
                holder = Class.forName(GameTests.class.getPackageName() + "." + holderName);
            } catch (ClassNotFoundException missing) {
                continue;
            }
            for (Method method : holder.getDeclaredMethods()) {
                GameTest annotation = method.getAnnotation(GameTest.class);
                if (annotation == null) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("Game test must be static: " + method);
                }
                method.setAccessible(true);
                String name = (holder.getSimpleName() + "." + method.getName()).toLowerCase(Locale.ROOT);
                FOUND.add(new Found(name, method, annotation));
                FUNCTIONS.register(name, () -> helper -> invoke(method, helper));
            }
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTests::onRegister);
    }

    private static void onRegister(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Found found : FOUND) {
            GameTest test = found.annotation();
            Identifier id = Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, found.name());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment,
                    Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, test.template()),
                    test.timeoutTicks(), test.setupTicks(), test.required());
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
