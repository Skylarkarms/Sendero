package sendero;

import sendero.interfaces.Updater;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public final class Merge<T> extends Path<T> implements BaseMerge<T> {

    private final SimpleLists.LockFree.Snapshooter<Appointer<?>, Boolean> joints = SimpleLists.getSnapshotting(
            AtomicBinaryEventConsumer.class,
            () -> !isIdle()
    );

    private final HolderInput.Updater<T> updater = new HolderInput.Updater<>(baseTestDispatcher);

    public Merge() {
        this(myIdentity());
    }

    public Merge(UnaryOperator<Builders.HolderBuilder<T>> operator) {
        super(operator);
    }

    @Override
    public<S> Merge<T> from(
            BasePath<S> path,
            Function<Updater<T>, Consumer<S>> observer
    ) {
        if (path == null) {
            throw new IllegalStateException("Observer is null");
        }

        final Appointer<?> jointAppointer = BinaryEventConsumers.fixedAppointer(
                path,
                new Consumer<Pair.Immutables.Int<S>>() {
                    final Consumer<S> sConsumer = observer.apply(updater);
                    final Holders.BaseColdHolder<S> simpleHolder = new Holders.BaseColdHolder<S>(){
                        @Override
                        void coldSwapped(S prev, Pair.Immutables.Int<S> next) {
                            sConsumer.accept(next.getValue());
                        }

                        //                    final Holders.AbsDispatcherHolder<S> simpleHolder = new Holders.AbsDispatcherHolder<S>(){
//                        @Override
//                        void coldDispatch(Pair.Immutables.Int<S> versionValue) {
//                            sConsumer.accept(versionValue.getValue());
//                        }
                    };
                    @Override
                    public void accept(Pair.Immutables.Int<S> sInt) {
                        simpleHolder.accept(sInt);
//                        simpleHolder.acceptVersionValue(sInt);
                    }
                }
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

