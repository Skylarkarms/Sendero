package sendero;

import sendero.event_registers.ConsumerRegister;
import sendero.interfaces.BooleanConsumer;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
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

    protected abstract void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);

    static class Injective<T> extends BasePath<T> {

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


        @Override
        protected void dispatch(Pair.Immutables.Int<T> t) {
            final Consumer<Pair.Immutables.Int<T>>[] subs = remote.copy();
            if (subs.length == 0) return;
            parallelDispatch(0, subs, t, UnaryOperator.identity());
            //Last local observers on same thread

        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            //From local observers on forked thread (if extended)
            final Consumer<Pair.Immutables.Int<T>>[] subs = remote.copy();
            final int length = subs.length;
            if (length == 0) return;
            if (length > 1) parallelDispatch(1, subs, t, UnaryOperator.identity());

            subs[0].accept(t); //Keep in thread
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

