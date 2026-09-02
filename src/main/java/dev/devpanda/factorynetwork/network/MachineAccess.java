package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * How a resource kind is read from and written to a foreign machine.
 *
 * <p><b>The second axis.</b> {@link ResourceStore} says where a kind sits in
 * the network — that belongs to this mod, and so it could be put behind an
 * interface. The machine belongs to someone else: there {@code IItemHandler}
 * and {@code IFluidHandler} from NeoForge and {@code IChemicalHandler} from
 * Mekanism sit side by side, are named differently on every method and count
 * in different units. A common supertype does not exist.
 *
 * <p><b>What is shared is not the type, but the action.</b> There are three of
 * them, and they are not invented but read off: {@code ChemicalStores} had
 * exactly these three, because the chemicals were built last and in doing so
 * needed the seam to the compatibility module.
 *
 * <p><b>What this is for:</b> a foreign mod registers a resource kind (see
 * {@code ResourceKinds}) and supplies here how its machines give it out and
 * take it in. Without that, its kind can sit in the network and be named in a
 * program — but it could not be moved.
 *
 * <p><b>The three built-in ones still go their own way.</b> Pulling them in
 * here is a separate cut, and it is waiting on a round of playtesting:
 * {@code move} is the place where this mod holds items in hand, and a bug
 * there costs stock rather than a message. See {@code maschinenzugriff.md},
 * section 5.
 */
public interface MachineAccess {

    /**
     * How much of it sits in the machine.
     *
     * <p>{@code keys} carries keys in the form that
     * {@code ResourceKind.type()} names for this kind. <b>Empty does not mean
     * "everything".</b> An empty selection already mixed up a gas once on
     * 26.08.; whoever means everything says so with a full list.
     */
    long count(Level level, BlockPos pos, Direction side, Collection<?> keys);

    /**
     * From the network store into the machine.
     *
     * @return how much arrived — less than requested is normal
     */
    long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
            Collection<?> keys, long limit);

    /**
     * From the machine into the network store.
     *
     * <p><b>Ask first, then pull.</b> What the store does not take must not
     * leave the machine in the first place — with items the rest could be put
     * back, with a gas it could not. This rule lives here and not at the
     * caller, because otherwise it gets forgotten at the fourth entry.
     *
     * @return how much arrived in the network
     */
    long drain(Level level, BlockPos pos, Direction side,
            Collection<?> keys, ResourceStore into, long limit);

    /**
     * A kind that reaches no machine.
     *
     * <p>The default, and not a stopgap: a kind may sit in the network without
     * any machine knowing it — just as it may be moved without being storable.
     * Whoever still wants to move it gets a message and not a silent zero.
     */
    MachineAccess NONE = new MachineAccess() {

        @Override
        public long count(Level level, BlockPos pos, Direction side, Collection<?> keys) {
            return 0;
        }

        @Override
        public long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
                Collection<?> keys, long limit) {
            return 0;
        }

        @Override
        public long drain(Level level, BlockPos pos, Direction side,
                Collection<?> keys, ResourceStore into, long limit) {
            return 0;
        }
    };
}
