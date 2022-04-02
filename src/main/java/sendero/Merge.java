package sendero;

import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Merge<T> extends Path<T> implements BaseMerge<T> {

    private final SimpleLists.SimpleList.LockFree.Snapshotting<BooleanConsumer, Boolean> joints = SimpleLists.getSnapshotting(
            BooleanConsumer.class,
            () -> !isIdle()
    );

    public Merge(T initialValue, Predicate<T> expectOutput) {
        super(
                Builders.getHolderBuild(tHolderBuilder -> tHolderBuilder.expectOut(expectOutput).withInitial(initialValue))
        );
    }

    @Override
    public<S> Merge<T> from(
            BasePath<S> path,
            Function<Updater<T>, Consumer<S>> observer
    ) {
        if (path == null) {
            throw new IllegalStateException("Observer is null");
        }

        final BooleanConsumer jointAppointer = Appointers.Appointer.booleanConsumerAppointer(
                path,
                new Consumer<Pair.Immutables.Int<S>>() {
                    final Consumer<S> sConsumer = observer.apply(holder);
                    final Holders.DispatcherHolder<S> simpleHolder = new Holders.DispatcherHolder<S>(){
                        @Override
                        void coldDispatch(Pair.Immutables.Int<S> versionValue) {
                            sConsumer.accept(versionValue.getValue());
                        }
                    };
                    @Override
                    public void accept(Pair.Immutables.Int<S> sInt) {
                        simpleHolder.acceptVersionValue(sInt);
                    }
                }
        );

        final Pair.Immutables.Bool<Boolean> res = joints.add(jointAppointer);
        if (res.value) jointAppointer.accept(true);
        return this;
    }

    @Override
    public<S> boolean drop(
            BasePath<S> path
    ) {
        return joints.removeIf(booleanConsumer -> booleanConsumer.equals(path));
    }

    @Override
    protected void onStateChange(boolean isActive) {
        final BooleanConsumer[] toDispatch = joints.copy();
        final int length = toDispatch.length;
        for (int i = 0; i < length; i++) {
            final BooleanConsumer j = toDispatch[i];
            j.accept(isActive);
        }
    }
}

