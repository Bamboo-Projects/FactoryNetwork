package dev.devpanda.factorynetwork.platform;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tooltip lines in the shape the item classes were written for.
 *
 * <p>Since 1.21.5 an item hands its tooltip lines to a consumer instead of
 * adding them to a list. The tooltip code of this mod reads like a list, and
 * sometimes counts what it already added. This list passes every line on
 * the moment it is added and otherwise behaves like any other list.
 */
public final class Tooltips {

    private Tooltips() {
    }

    public static List<Component> lines(Consumer<Component> sink) {
        return new ArrayList<>() {
            @Override
            public boolean add(Component line) {
                sink.accept(line);
                return super.add(line);
            }
        };
    }
}
