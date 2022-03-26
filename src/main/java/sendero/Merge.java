package sendero;

import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Merge<T> extends Path<T> implements BaseMerge<T> {
    private final Updater<T> updater = Merge.this::update;

    private final SimpleLists.SimpleList.LockFree.Snapshotting<BooleanConsumer, Boolean> joints = SimpleLists.getSnapshotting(
            BooleanConsumer.class,
            () -> !isIdle()
    );

    public Merge(T initialValue, Predicate<T> expectOutput) {
        super(true);
        setActivationListener(
                isActive -> {
                    final BooleanConsumer[] toDispatch = joints.copy();
                    final int length = toDispatch.length;
                    for (int i = 0; i < length; i++) {
                        final BooleanConsumer j = toDispatch[i];
                        j.accept(isActive);
                    }
                }
        );
        setExpectOutput(expectOutput);
        accept(initialValue);
    }

    @Override
    public<S> Merge<T> from(
            BasePath<S> path,
            Function<Updater<T>, Consumer<S>> observer
    ) {
        if (path == null) {
            throw new IllegalStateException("Observer is null");
        }
        final BooleanConsumer joint = new BooleanConsumer() {
            final BasePath<S> jointDomain = path;
            final Consumer<S> sConsumer = observer.apply(updater);
            final Consumer<Pair.Immutables.Int<S>> toAppoint = new Consumer<Pair.Immutables.Int<S>>() {
                final Holders.DispatcherHolder<S> simpleHolder = new Holders.DispatcherHolder<S>(){
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<S> versionValue) {
                        sConsumer.accept(versionValue.getValue());
                    }
                };
                @Override
                public void accept(Pair.Immutables.Int<S> sInt) {
                    simpleHolder.acceptVersionValue(sInt);
                }
            };
            @Override
            public void accept(boolean isActive) {
                if (isActive) jointDomain.appoint(toAppoint);
                else {
//                    if (jointDomain instanceof Injective) {
//                        ((Injective<S>) jointDomain).demote();
//                    } else if (jointDomain instanceof ToMany) {
//                        ((ToMany<S>) jointDomain).demote(toAppoint);
//                    }
                    jointDomain.demotionOverride(toAppoint);
                }
            }

            @Override
            public boolean equals(Object o) {
                return jointDomain.equals(o);
            }

            @Override
            public int hashCode() {
                return jointDomain.hashCode();
            }
        };
        final Pair.Immutables.Bool<Boolean> res = joints.add(joint);
        if (res.value) joint.accept(true);
        return this;
    }

    @Override
    public<S> boolean drop(
            BasePath<S> path
    ) {
        return joints.removeIf(booleanConsumer -> booleanConsumer.equals(path));
    }

}

