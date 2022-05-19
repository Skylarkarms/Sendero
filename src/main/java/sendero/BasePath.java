package sendero;

import sendero.event_registers.ConsumerRegisters;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.isIdentity;
import static sendero.functions.Functions.myIdentity;

public abstract class BasePath<T> extends Holders.ExecutorHolder<T> implements Forkable<T> {

    <S> BasePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator, BasePath<S> basePath, Function<S, T> map) {
        super(builderOperator,
                Builders.withFixed(
                        (Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer>) coldHolder ->
                                BinaryEventConsumers.producerHolderConnector(basePath, coldHolder, map)

                ));
    }

    <S> BasePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(builderOperator,
                Builders.withFixed(
                        (Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer>) coldHolder ->
                                BinaryEventConsumers.producerHolderConnector(basePath, coldHolder, updateFun)

                )
        );
    }

    BasePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            UnaryOperator<Builders.ManagerBuilder> mngrBuilderOperator
    ) {
        super(builderOperator, mngrBuilderOperator);
    }

    BasePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        super(builderOperator);
    }

    protected abstract void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);

    /**boolean continuation is:
     * <li>true if: is a dispatch product from the continuation of events in a forking path stream</li>
     * <li>false if: is a dispatch product from a client's input via update() or accept()</li>
     * */
    abstract void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t);

    // ------------------ Forking Functions ------------------

    protected abstract void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer);

    private <S> Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer> mainForkingFunctionBuilder(
            Function<Consumer<Pair.Immutables.Int<S>>, Consumer<Pair.Immutables.Int<T>>> converter
    ) {
        return intConsumer -> {
            final Consumer<Pair.Immutables.Int<T>> converted = converter.apply(intConsumer);
            return BinaryEventConsumers.fixedAppointer(this, converted);
        };
    }

    <S> Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer> mapFunctionBuilder(Function<T, S> map) {
        @SuppressWarnings("unchecked")
        final Function<Consumer<Pair.Immutables.Int<S>>, Consumer<Pair.Immutables.Int<T>>> finalFunction = isIdentity(map) ?
                holder ->
                        tInt -> holder.accept((Pair.Immutables.Int<S>) tInt) :
                tColdHolder ->
                        tInt -> tColdHolder.accept(new Pair.Immutables.Int<>(tInt.getInt(), map.apply(tInt.getValue())));
        return mainForkingFunctionBuilder(
                finalFunction
        );
    }

    <S> Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer> switchFunctionBuilder(Function<T, BasePath<S>> switchMap) {
        return intConsumer -> AtomicBinaryEventConsumer.switchMapEventConsumer(
                intConsumer,
                this,
                switchMap
        );
    }

    static abstract class PathAbsDispatcher<T> implements Dispatcher<T>, IBasePath<T> {

        private final Holders.ExecutorHolder<T> executorHolder;

        void scheduleExecution(long delay, Runnable action) {
            executorHolder.scheduleExecution(delay, action);
        }

        <S> void parallelDispatch(int beginAt, Consumer<? super S>[] subs, Pair.Immutables.Int<T> t, Function<Pair.Immutables.Int<T>, S> map) {
            executorHolder.parallelDispatch(beginAt, subs, t, map);
        }

            abstract Pair.Immutables.Bool<Integer> onAddRegister(Consumer<Pair.Immutables.Int<T>> subscriber);
        abstract boolean onRegisterRemoved(Consumer<Pair.Immutables.Int<T>> intConsumer);

        @SuppressWarnings("unchecked")
        @Override
        public void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            executorHolder.onAdd(
                    subscriber,
                    consumer -> onAddRegister((Consumer<Pair.Immutables.Int<T>>) consumer),
                    myIdentity()
            );
        }

        @Override
        public void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            if (onRegisterRemoved(intConsumer)) executorHolder.deactivate();
        }

        protected PathAbsDispatcher(Holders.ExecutorHolder<T> owner) {
            this.executorHolder = owner;
        }

        abstract boolean isInactive();

        int getVersion(){
            return executorHolder.getVersion();
        }
    }

    static final class ToManyPathsAbsDispatcher<T> extends PathAbsDispatcher<T> {
        private final SimpleLists.LockFree.Snapshooter<Consumer<Pair.Immutables.Int<T>>, Integer>
                remote;

        ToManyPathsAbsDispatcher(Holders.ExecutorHolder<T> owner) {
            super(owner);
            remote = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);
        }

        @Override
        public void dispatch(long delay, Pair.Immutables.Int<T> t) {
            //Last local observers on same thread
            scheduleExecution(
                        delay,
                        () -> pathDispatch(false, t)
                );
        }

        @Override
        public void coldDispatch(Pair.Immutables.Int<T> t) {
            //From local observers on forked thread (if extended)
            pathDispatch(false, t);
        }

        @Override
        Pair.Immutables.Bool<Integer> onAddRegister(Consumer<Pair.Immutables.Int<T>> subscriber) {
            return remote.snapshotAdd(subscriber);
        }

        @Override
        boolean onRegisterRemoved(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            return remote.remove(intConsumer);
        }

        @Override
        public void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
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

        boolean isInactive() {
            return remote.isEmpty();
        }

    }

    static final class InjectivePathAbsDispatcher<T> extends PathAbsDispatcher<T> {

        private final ConsumerRegisters.IConsumerRegister.SnapshottingConsumerRegister<Integer, Pair.Immutables.Int<T>>
                remote;

        InjectivePathAbsDispatcher(Holders.ExecutorHolder<T> executorHolder) {
            super(executorHolder);
            remote = ConsumerRegisters.IConsumerRegister.getInstance(this::getVersion);
        }

        @Override
        public void dispatch(long delay, Pair.Immutables.Int<T> t) {
            pathDispatch(false, t);
        }

        @Override
        public void coldDispatch(Pair.Immutables.Int<T> t) {
            pathDispatch(false, t);
        }

        @Override
        Pair.Immutables.Bool<Integer> onAddRegister(Consumer<Pair.Immutables.Int<T>> subscriber) {
            return remote.snapshotRegister(subscriber);
        }

        @Override
        public void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
            if (fullyParallel) throw new IllegalStateException("Injective.class dispatch cannot be parallel.");
        }

        @Override
        boolean onRegisterRemoved(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            return remote.unregister() != null;
        }

        boolean isInactive() {
            return !remote.isRegistered();
        }

    }
}

