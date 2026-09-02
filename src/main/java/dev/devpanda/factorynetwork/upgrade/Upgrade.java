package dev.devpanda.factorynetwork.upgrade;

/**
 * What fits into a slot.
 *
 * <p><b>Two kinds, and the distinction is sharp:</b> a module (see
 * {@link Ability}) grants an ability that was not there before — a display
 * cannot go wireless without a wireless module. A {@link Card} raises a value
 * on an ability that is already there — the laptop goes wireless even without
 * a card, just not far.
 *
 * <p>Both occupy the same slot. Whoever wants everything must decide what to
 * leave out; that is the point of the fixed slot count.
 *
 * <p>Without any Minecraft reference, so that the calculation on top of it
 * stays testable in ordinary tests — the same approach as in the {@code lang}
 * package.
 */
public sealed interface Upgrade permits Ability, Card {

    /** The name in the registration path, such as {@code wireless_module}. */
    String id();
}
