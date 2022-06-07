package sendero;

import sendero.interfaces.Updater;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public final class Merge<T> extends Path<T> implements BaseMerge<T> {

    private final SimpleLists.LockFree.Snapshooter<AtomicBinaryEventConsumer, Boolean> joints = SimpleLists.getSnapshotting(
            AtomicBinaryEventConsumer.class,
            () -> !isIdle()
    );

    private final Updater<T> updater = Inputs.getUpdater(this);

    public Merge() {
        this(myIdentity());
    }

    public Merge(UnaryOperator<Builders.HolderBuilder<T>> operator) {
        super(operator);
    }

    @Override
    public<S> Merge<T> from(
            BasePath<S> path,
            BiFunction<T, S, T> update
    ) {
        if (path == null) {
            throw new IllegalStateException("Observer is null");
        }

        final AtomicBinaryEventConsumer jointAppointer = Appointer.producerConnector(
                path,
                Holders.StreamManager.baseManager(
                        (prev, next, delay) -> updater.update(
                                t -> update.apply(t, next.get())
                        )
                )
        );

        final Pair.Immutables.Bool<Boolean> res = joints.snapshotAdd(jointAppointer);
        if (res.value) jointAppointer.start();
        return this;
    }

    @Override
    public<S> boolean drop(
            BasePath<S> path
    ) {
        return joints.removeIf(booleanConsumer -> booleanConsumer.equalTo(path));
    }

    @Override
    protected void onStateChange(boolean isActive) {
        final AtomicBinaryEventConsumer[] toDispatch = joints.copy();
        final int length = toDispatch.length;
        if (isActive) {
            for (int i = 0; i < length; i++) {
                final AtomicBinaryEventConsumer j = toDispatch[i];
                j.on();
            }
        } else {
            for (int i = 0; i < length; i++) {
                final AtomicBinaryEventConsumer j = toDispatch[i];
                j.off();
            }
        }
    }
}

