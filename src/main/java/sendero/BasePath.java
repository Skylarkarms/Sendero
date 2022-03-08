package sendero;

import sendero.event_registers.ConsumerRegister;
import sendero.interfaces.BooleanConsumer;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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

    protected BasePath(Holders.DispatcherHolder.Builder<T> holderBuilder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    protected BasePath(Holders.DispatcherHolder.Builder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    static<S> BooleanConsumer activationListenerCreator(
            Supplier<BasePath<S>> basePathSupplier,
            Consumer<Pair.Immutables.Int<S>> toAppoint
    ) {
        return new BooleanConsumer() {
            //The supplier is for the Client to be allowed to  create a path at super()
            final BasePath<S> basePath = basePathSupplier.get();
            @Override
            public void accept(boolean isActive) {
                if (isActive) basePath.appoint(toAppoint);
                else if (basePath instanceof ToMany) {
                    ((ToMany<S>) basePath).demote(toAppoint);
                } else if (basePath instanceof Injective) {
                    ((Injective<S>) basePath).demote();
                }
            }
        };
    }

    protected abstract void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);

    static class Injective<T> extends BasePath<T> {

        protected Injective(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        protected<S> Injective(Supplier<BasePath<S>> basePathSupplier, Function<Consumer<Pair.Immutables.Int<T>>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
            super(
                    dispatcher -> activationListenerCreator(basePathSupplier, toAppointFun.apply(dispatcher))
            );
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

        protected ToMany(Holders.DispatcherHolder.Builder<T> holderBuilder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        protected ToMany(Holders.DispatcherHolder.Builder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
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

