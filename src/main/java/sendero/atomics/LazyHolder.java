package sendero.atomics;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class LazyHolder<S> implements Supplier<S> {
    private final LazyInitSingleton<S> value;
    @Override
    public S get() {
        delayer.interrupt();
        return value.get();
    }
    private void attemptDestroy() {
        S destroyed = value.getAndDestroy();
        if (destroyed != null) onDestroyed.accept(destroyed);
    }
    private final AtomicUtils.OverlapDropExecutor.Long delayer;
    private final Consumer<S> onDestroyed;
    public LazyHolder(
            Supplier<S> onCreate,
            long millisToDestroy,
            Consumer<S> onDestroyed
    ) {
        this.value = new LazyInitSingleton<>(onCreate);
        this.onDestroyed = onDestroyed;
        this.delayer = new AtomicUtils.OverlapDropExecutor.Long(millisToDestroy);
    }
    public LazyHolder(
            Supplier<S> onCreate,
            Consumer<S> onDestroyed
    ) {
        this(onCreate, 0, onDestroyed);
    }
    public void destroy() {
        delayer.scheduleOrSwap(
                this::attemptDestroy
        );
    }
    @Override
    public String toString() {
        return "DelayedDestroyer{" +
                "Value=" + value +
                "}@" + hashCode();
    }
}
