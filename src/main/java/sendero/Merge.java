package sendero;

import sendero.interfaces.Updater;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public final class Merge<T> extends Path<T> implements BaseMerge<T> {

    private final List<Entry<?, T>> holders = new ArrayList<>();

    /**Lock for list manipulation only.
     * It does not involve any drawbacks to system's reactivity.*/
    private final Object lock = new Object();

    private final Updater<T> updater = Inputs.getUpdater(this);

    public Merge() {
        this(myIdentity());
    }

    @SafeVarargs
    public Merge(Entry<?, T> ...entries) {
        this(myIdentity(), entries);
    }

    @SafeVarargs
    public Merge(UnaryOperator<Builders.HolderBuilder<T>> operator, Entry<?, T> ...entries) {
        super(operator);
        for (Entry<?, T> e:entries) holders.add(e.set(updater));
    }

    public static final class Entry<P, T> extends AbstractMap.SimpleImmutableEntry<BasePath<P>, BiFunction<T, P, T>> {
        private AtomicBinaryEvent consumer = AtomicBinaryEvent.DEFAULT;

        public static<P, T> Entry<P, T> get(BasePath<P> key, BiFunction<T, P, T> value) {
            return new Entry<>(key, value);
        }

        Entry<P, T> set(Updater<T> updater) {
            consumer = getJointAppointer(getKey(), getValue(), updater);
            return this;
        }

        boolean isDefault() {
            return consumer == AtomicBinaryEvent.DEFAULT;
        }

        void start() {
            consumer.start();
        }
        void shutoff() {
            consumer.shutoff();
        }
        void on() {
            consumer.on();
        }
        void off() {
            consumer.off();
        }

        Entry(BasePath<P> key, BiFunction<T, P, T> value) {
            super(key, value);
        }
    }

    @Override
    public <S> void from(Entry<S, T> entry) {
        synchronized (lock) {
            if (!holders.contains(entry)) {
                holders.add(entry.set(updater));
                if (isActive()) entry.start();
            } else throw new IllegalStateException("Entry already present!");
        }
    }

    private static <S, T> AtomicBinaryEvent getJointAppointer(BasePath<S> path, BiFunction<T,S, T> update, Updater<T> updater) {
        return Builders.BinaryEventConsumers.producerConnector(
                path,
                Holders.StreamManager.baseManager(
                        (prev, next, delay) -> updater.updateAndGet(
                                t -> update.apply(t, next.get())
                        )
                )
        );
    }

    @Override
    public <S> boolean drop(Entry<S, T> entry) {
        synchronized (lock) {
            if (holders.remove(entry)) {
                entry.shutoff();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStateChange(boolean isActive) {
        List<Entry<?, T>> entries = new ArrayList<>(holders);
        if (isActive) {
            for (Entry<?, T> e:entries
                 ) {
                e.on();
            }
        } else {
            for (Entry<?, T> e:entries
                 ) {
                e.off();
            }
        }
    }
}

