package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

class ActivePathListener<T> {
    private final ActivationManager manager;
    private final Appointers.UnboundPathListenerImpl<T> appointerCache;

    Holders.StreamManager<T> getStreamManager() {
//    ColdHolder<T> getColdHolder() {
//    Holders.ColdHolder<T> getColdHolder() {
        return appointerCache.getStreamManager();
    }

    public ActivePathListener(ActivationManager manager, Appointers.UnboundPathListenerImpl<T> appointerCache) {
        this.manager = manager;
        this.appointerCache = appointerCache;
    }

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

    protected boolean unbound() {
        final AtomicBinaryEventConsumer binaryEventConsumer = appointerCache.getAndClear();
        if (binaryEventConsumer != null) return manager.swapActivationListener(binaryEventConsumer, AtomicBinaryEventConsumer.CLEARED);
        return false;
    }
}
