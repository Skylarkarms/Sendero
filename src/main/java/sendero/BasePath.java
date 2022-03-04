package sendero;

import sendero.event_registers.ConsumerRegister;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public abstract class BasePath<T> extends Holders.ExecutorHolder<T> {

    public BasePath(boolean activationListener) {
        super(activationListener);
    }

    public BasePath() {
    }

    protected BasePath(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
        super(selfMap);
    }

    protected BasePath(T initialValue, Function<Updater<T>, BooleanConsumer> selfMap, Predicate<T> expectOut) {
        super(initialValue, selfMap, expectOut);
    }

    protected BasePath(Holders.DispatcherHolder<T> holder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
        super(holder, actMgmtBuilder);
    }

    protected BasePath(Holders.DispatcherHolder<T> holder, ActivationManager.Builder actMgmtBuilder) {
        super(holder, actMgmtBuilder);
    }

    protected abstract void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);

    static class Injective<T> extends BasePath<T> {

        public Injective(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        private final ConsumerRegister.IConsumerRegister.SnapshottingConsumerRegister<Integer, Pair.Immutables.Int<T>>
                remote = ConsumerRegister.IConsumerRegister.getInstance(this::getVersion);

        @Override
        protected void dispatch(Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            onAdd(
                    subscriber,
                    consumer -> remote.snapshotRegister((Consumer<Pair.Immutables.Int<T>>) consumer),
                    UnaryOperator.identity()
            );
        }

        @Override
        protected boolean deactivationRequirements() {
            return !remote.isRegistered();
        }

        protected void demote() {
            if (remote.unregister() != null) deactivate();
        }

    }

    static class ToMany<T> extends BasePath<T> {
        private final SimpleLists.SimpleList.LockFree.Snapshotting<Consumer<Pair.Immutables.Int<T>>, Integer>
                remote = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);

        public ToMany(boolean activationListener) {
            super(activationListener);
        }

        public ToMany() {
            super();
        }

        protected ToMany(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        protected ToMany(T initialValue, Function<Updater<T>, BooleanConsumer> selfMap, Predicate<T> expectOut) {
            super(initialValue, selfMap, expectOut);
        }

        protected ToMany(Holders.DispatcherHolder<T> holder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
            super(holder, actMgmtBuilder);
        }

        protected ToMany(Holders.DispatcherHolder<T> holder, ActivationManager.Builder actMgmtBuilder) {
            super(holder, actMgmtBuilder);
        }

        @Override
        protected void dispatch(Pair.Immutables.Int<T> t) {
            //Last local observers on same thread
            dispatchRemotes(false, t);

        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            //From local observers on forked thread (if extended)
            dispatchRemotes(false, t);
        }

        protected void dispatchRemotes(boolean fullyParallel, Pair.Immutables.Int<T> t) {
            final Consumer<Pair.Immutables.Int<T>>[] subs = remote.copy();
            final int length = subs.length;
            if (length == 0) return;
            if (!fullyParallel) {
                if (length > 1) parallelDispatch(1, subs, t, UnaryOperator.identity());

                subs[0].accept(t); //Keep in thread
            } else {
                parallelDispatch(0, subs, t, UnaryOperator.identity());
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            onAdd(
                    subscriber,
                    consumer -> remote.add((Consumer<Pair.Immutables.Int<T>>) consumer),
                    UnaryOperator.identity()
            );
        }

        @Override
        protected boolean deactivationRequirements() {
            return remote.isEmpty();
        }

        protected void demote(Consumer<Pair.Immutables.Int<T>> subscriber) {
            if (remote.remove(subscriber)) {
                deactivate();
            }
        }

    }
}

