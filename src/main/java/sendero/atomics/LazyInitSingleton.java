package sendero.atomics;

import sendero.abstract_containers.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LazyInitSingleton<T> implements Supplier<T> {
    private static final int NULL_PHASE = -1, CREATING_PHASE = 0, CREATED = 1;
    private static final Pair.Immutables.Int<?> NULL = new Pair.Immutables.Int<>(NULL_PHASE, null),
            CREATING = new Pair.Immutables.Int<>(CREATING_PHASE, null);
    @SuppressWarnings("unchecked")
    private static<T> T getNull() {
        return (T) NULL;
    }
    @SuppressWarnings("unchecked")
    private static<T> T getCreating() {
        return (T) CREATING;
    }
    private final AtomicReference<Pair.Immutables.Int<T>> ref = new AtomicReference<>(getNull());
    private final Supplier<T> builder;
    public LazyInitSingleton(Supplier<T> builder) {
        this.builder = builder;
    }
    public T getAndDestroy() {
        return ref.getAndSet(getNull()).value;
    }
    @Override
    public T get() {
        Pair.Immutables.Int<T> prev;
        while ((prev = ref.get()).anInt < CREATED) {
            if (ref.compareAndSet(getNull(), getCreating())) {
                T res = builder.get();
                ref.set(new Pair.Immutables.Int<>(CREATED, res));
                return res;
            }
        }
        return prev.value;
    }
}