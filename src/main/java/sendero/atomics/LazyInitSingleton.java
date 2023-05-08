package sendero.atomics;

import sendero.pairs.Pair;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class LazyInitSingleton<T> {
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

    public static final class Function<S, T> extends LazyInitSingleton<T>
            implements java.util.function.Function<S, T> {

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

    public static final class Container {

        public static final class Entry<C> extends AbstractMap.SimpleImmutableEntry<Class<C>, java.util.function.Supplier<C>> {

            public static<T> Entry<T> get(Class<T> type, java.util.function.Supplier<T> tSupplier) {
                return new Entry<>(type, tSupplier);
            }

            Entry(Class<C> key, java.util.function.Supplier<C> value) {
                super(key, value);
            }
        }

        private final Map<Class<?>, Supplier<?>> map;

        public Container(Entry<?>... entries) {
            Map<Class<?>, Supplier<?>> map = new HashMap<>();
            for (Entry<?> e:entries
            ) {
                Class<?> key = e.getKey();
                Supplier<?> prev = map.putIfAbsent(key, new Supplier<>(e.getValue()));
                assert prev == null : "Key [" + key + "] already present in map";
//                ObjectUtils.assertNull(
//                        map.putIfAbsent(key, new Supplier<>(e.getValue())), () -> "Key " + key + " already present in map = " + CollectionUtils.toString(map.entrySet()));
            }
            this.map = Collections.unmodifiableMap(map);
        }

        public<T> T get(Class<T> tClass) {
            Supplier<?> supplier = map.get(tClass);
            assert supplier != null : "Class " + tClass + " not present in map";
//            Supplier<?> supplier = ObjectUtils.getNonNull(map.get(tClass), () -> "Type: " + tClass + " not present in map " + CollectionUtils.toString(map.entrySet()));
            return tClass.cast(supplier.get());
        }
    }
}