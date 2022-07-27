package sendero.atomics;

import sendero.abstract_containers.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LazyInitSingleton<T> implements Supplier<T> {
    private static final int NULL_PHASE = -1, CREATING_PHASE = 0, CREATED = 1; 
    private final Pair.Immutables.Int<T> NULL = new Pair.Immutables.Int<>(NULL_PHASE, null),
    CREATING = new Pair.Immutables.Int<>(CREATING_PHASE, null); 
    private final AtomicReference<Pair.Immutables.Int<T>> ref = new AtomicReference<>(NULL);
    private final Supplier<T> builder; 
    public LazyInitSingleton(Supplier<T> builder) {
        this.builder = builder; 
    } 
    public T getAndDestroy() { 
        return ref.getAndSet(NULL).value; 
    } 
    @Override 
    public T get() { 
        Pair.Immutables.Int<T> prev; 
        do { 
            if (ref.compareAndSet(NULL, CREATING)) { 
                T res = builder.get(); 
                ref.set(new Pair.Immutables.Int<>(CREATED, res)); 
                return res; 
            } else { 
                prev = ref.get(); 
                if (prev.anInt == CREATED) return prev.value;
            } 
        } while (prev.anInt < CREATED);
        return prev.value; 
    } 
}