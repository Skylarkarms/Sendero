package sendero;

import sendero.interfaces.Updater;
import sendero.lists.Removed;

import java.util.AbstractMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public final class Merge<T> extends Path<T> implements BaseMerge<T> {

    private final ExitAppointers<T, Entry<T>> exitAppointers;

    private final Updater<T> updater = Inputs.getUpdater(this);

    public Merge() {
        this(myIdentity());
    }

    @SafeVarargs
    public Merge(Entry<T>...entries) {
        this(myIdentity(), entries);
    }

    @SafeVarargs
    public Merge(UnaryOperator<Builders.HolderBuilder<T>> operator,
                 Entry<T>...entries
    ) {
        super(operator);
        this.exitAppointers = new ExitAppointers<T, Entry<T>>(this::isActive,
                tEntry -> tEntry.getJointAppointer(updater),
                entries
        );
    }

    public static final class Entry<T> extends AbstractMap.SimpleImmutableEntry<BasePath<?>, BiFunction<T, ?, T>> {

        public static<P, T> Entry<T> get(BasePath<P> producer, BiFunction<T, P, T> updatingFun) {
            return new Entry<>(producer, updatingFun);
        }
        final Function<Updater<T>, AtomicBinaryEvent> updaterFun;
        <P> Entry(BasePath<P> key, BiFunction<T, P, T> value) {
            super(key, value);
            updaterFun = updater -> Builders.BinaryEventConsumers.producerListener(
                    key,
                    Holders.StreamManager.baseManager(
                            value,
                            updater
                    )
            );
        }
        <S> AtomicBinaryEvent getJointAppointer(Updater<T> updater) {
            return updaterFun.apply(updater);
        }

    }

    @Override
    public void from(Entry<T> entry) {
        exitAppointers.add(
                entry,
                exit -> exit.getJointAppointer(updater)
        );
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) exitAppointers.on();
        else exitAppointers.off();
    }

    @Override
    public boolean drop(Entry<T> entry) {
        return exitAppointers.remove(entry) != Removed.failed;
    }

}

