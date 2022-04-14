package sendero;

import sendero.interfaces.AtomicBinaryEventConsumer;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

//A BooleanConsumer is bounded by a BasePath Producer, so Appointer can be final.
class ActivePathListener<T> {
    private final ActivationManager manager;
    private final Appointers.HolderAppointer<T> appointerCache;

    Holders.ColdHolder<T> getColdHolder() {
        return appointerCache.getColdHolder();
    }

    protected ActivePathListener(ActivationManager manager, Appointers.HolderAppointer<T> appointerCache) {
        this.manager = manager;
        this.appointerCache = appointerCache;
    }

    /**should be protected*/
    protected  <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
        manager.setActivationListener(
                appointerCache.setPathAndGet(basePath, map)
        );
    }

    protected  <S, P extends BasePath<S>> void bindUpdate(P basePath, BiFunction<T, S, T> update) {
        manager.setActivationListener(
                appointerCache.setPathUpdateAndGet(basePath, update)
        );
    }

    void forcedSet(AtomicBinaryEventConsumer activationListener) {
        manager.swapActivationListener(
                appointerCache.getAndClear(), activationListener
        );
    }

    /**should be protected*/
    protected  <S, P extends BasePath<T>> void bind(P basePath) {
        bindMap(basePath, UnaryOperator.identity());
    }

    protected boolean unbound() {
        final BinaryEventConsumers.Appointer<?> app = appointerCache.getAndClear();
        if (app != null) return manager.swapActivationListener(app, AtomicBinaryEventConsumer.CLEARED);
        return false;
    }
}
