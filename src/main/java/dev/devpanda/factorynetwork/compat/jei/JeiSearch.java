package dev.devpanda.factorynetwork.compat.jei;

import dev.devpanda.factorynetwork.client.ExternalSearch;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI's search field as the terminal's external search. Installed by the
 * plugin while JEI's runtime exists.
 */
final class JeiSearch implements ExternalSearch.Source {

    private final IJeiRuntime runtime;

    JeiSearch(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String text() {
        return runtime.getIngredientFilter().getFilterText();
    }

    @Override
    public void setText(String text) {
        runtime.getIngredientFilter().setFilterText(text);
    }
}
