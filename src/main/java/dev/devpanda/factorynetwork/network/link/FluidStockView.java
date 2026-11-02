package dev.devpanda.factorynetwork.network.link;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/** A {@link FluidStock} in the shape of tanks; see {@link ItemStockView}. */
@SuppressWarnings("removal")
public final class FluidStockView implements IFluidHandler, KeyedFluids {

    private final FluidStock stock;
    private final List<Fluid> fluids = new ArrayList<>();
    private final List<Long> amounts = new ArrayList<>();

    private final List<Fluid> wanted;
    private boolean read;

    public FluidStockView(FluidStock stock, List<Fluid> wanted) {
        this.stock = stock;
        this.wanted = wanted;
    }

    /** Reads the sorts on first use; see {@link ItemStockView}. */
    private void read() {
        if (read) {
            return;
        }
        read = true;
        stock.find(wanted, (fluid, amount) -> {
            if (amount > 0) {
                fluids.add(fluid);
                amounts.add(amount);
            }
        });
    }

    @Override
    public IFluidHandler view(List<Fluid> wanted) {
        return new FluidStockView(stock, wanted);
    }

    @Override
    public int getTanks() {
        read();
        return fluids.size() + 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        read();
        if (tank < 0 || tank >= fluids.size()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluids.get(tank), (int) Math.min(amounts.get(tank), Integer.MAX_VALUE));
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        return (int) stock.insert(resource.getFluid(), resource.getAmount(), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        long taken = stock.extract(resource.getFluid(), resource.getAmount(), action.simulate());
        return taken <= 0 ? FluidStack.EMPTY : resource.copyWithAmount((int) taken);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        read();
        for (Fluid fluid : fluids) {
            FluidStack taken = drain(new FluidStack(fluid, maxDrain), action);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return FluidStack.EMPTY;
    }
}
