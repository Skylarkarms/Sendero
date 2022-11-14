package sendero.atomics;

import sendero.abstract_containers.Pair;

import java.util.concurrent.atomic.AtomicReference;

class LazyInitSingleton<T> {
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
    final AtomicReference<Pair.Immutables.Int<T>> ref = new AtomicReference<>(getNull());

    LazyInitSingleton() {}

    public T getAndDestroy() {
        return ref.getAndSet(getNull()).value;
    }

    public static final class Supplier<T> extends LazyInitSingleton<T> implements java.util.function.Supplier<T> {
        private final java.util.function.Supplier<T> builder;

        public Supplier(java.util.function.Supplier<T> builder) {
            this.builder = builder;
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

    public static final class Function<S, T> extends LazyInitSingleton<T> implements java.util.function.Function<S, T> {

        private final java.util.function.Function<S, T> builder;

        public Function(java.util.function.Function<S, T> builder) {
            this.builder = builder;
        }

        @Override
        public T apply(S s) {
            Pair.Immutables.Int<T> prev;
            while ((prev = ref.get()).anInt < CREATED) {
                if (ref.compareAndSet(getNull(), getCreating())) {
                    T res = builder.apply(s);
                    ref.set(new Pair.Immutables.Int<>(CREATED, res));
                    return res;
                }
            }
            return prev.value;
        }
    }
}