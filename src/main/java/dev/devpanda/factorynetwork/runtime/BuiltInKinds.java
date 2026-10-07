package dev.devpanda.factorynetwork.runtime;

/**
 * Brings the built-in kinds to {@link Kinds} when it asks for them.
 *
 * <p>Named in {@code META-INF/services} and found from there. All it does is
 * touch {@link ResourceKinds}: that class registers items, fluids and
 * chemicals when it is first used, and once it has been, there is nothing
 * left to do here.
 */
public final class BuiltInKinds implements Kinds.Source {

    @Override
    public void load() {
        ResourceKinds.ITEM.prefix();
    }
}
