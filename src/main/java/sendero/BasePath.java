package sendero;

import sendero.event_registers.ConsumerRegisters;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class BasePath<T> extends Holders.ExecutorHolder<T> implements Forkable<T> {

    <S> BasePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map
    ) {
        super(builderOperator,
                Builders.withFixed(
                        (Function<Holders.StreamManager<T>, AtomicBinaryEvent>) coldHolder ->
                                Builders.BinaryEventConsumers.producerConnector(basePath, coldHolder, map)

                ));
    }

    <S> BasePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(builderOperator,
                Builders.withFixed(
                        (Function<Holders.StreamManager<T>, AtomicBinaryEvent>) coldHolder ->
                                Builders.BinaryEventConsumers.producerHolderConnector(basePath, coldHolder, updateFun)

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

    protected abstract void appoint(Receptor<T> receptor);

    abstract void pathDispatch(boolean fullyParallel, Immutable<T> t);

    // ------------------ Forking Functions ------------------

    protected abstract void demotionOverride(Receptor<T> intConsumer);

    <S> Function<Holders.StreamManager<S>, AtomicBinaryEvent> mapFunctionBuilder(Function<T, S> map) {
        return holder -> Builders.BinaryEventConsumers.producerConnector(this,
                holder, map
        );
    }

    <S> Function<Holders.StreamManager<S>, AtomicBinaryEvent> switchFunctionBuilder(Function<T, BasePath<S>> switchMap) {
        return intConsumer -> AtomicBinaryEventConsumer.switchMapEventConsumer(
                intConsumer,
                this,
                switchMap
        );
    }

    interface Receptor<T> extends Holders.ColdConsumer<T>, Holders.Invalidator {
        static<S, T> Receptor<T> withManagerInput(Holders.StreamManager<S> manager, InputMethod.Type<S, T> type) {
            return new ReceptorInputMethod<>(manager, type);
        }
        boolean equalTo(InputMethod.Type<?, ?> other);
    }
    static class ReceptorInputMethod<T> implements Receptor<T> {
        private final Holders.StreamManager<?> coreReceptor;
        private final Holders.ColdConsumer<T> mapped;
        private final InputMethod.Type<?, T> type;

        private <S> ReceptorInputMethod(Holders.StreamManager<S> coreReceptor, InputMethod.Type<S, T> type) {
            this.coreReceptor = coreReceptor;
            this.type = type;
            this.mapped = tImmutable -> type.acceptorMethod(coreReceptor, tImmutable);
        }

        @Override
        public void accept(Immutable<T> tImmutable) {
            mapped.accept(tImmutable);
        }

        @Override
        public boolean equalTo(InputMethod.Type<?, ?> other) {
            return other != null
                    && other == this.type;
        }

        @Override
        public String toString() {
            return "ReceptorInputMethod{" +
                    "\n core receptor=" + coreReceptor +
                    "\n}";
        }

        @Override
        public void invalidate() {
            coreReceptor.invalidate();
        }
    }

    static abstract class PathAbsDispatcher<T> extends Holders.DispatcherReader<T> implements Dispatcher<T>, IBasePath<T> {

        private final Holders.ExecutorHolder<T> executorHolder;

        @Override
        Immutable<T> getSnapshot() {
            return executorHolder.getSnapshot();
        }

        void scheduleExecution(long delay, Runnable action) {
            executorHolder.scheduleExecution(delay, action);
        }

        <S> void parallelDispatch(
                int beginAt,
                Consumer<? super S>[] subs,
                Immutable<T> t,
                Function<Immutable<T>, S> map
        ) {
            executorHolder.parallelDispatch(beginAt, subs, t, map);
        }

        /**Returns true if this is the first item added
         * @param core: receptor of the Immutable value*/
        abstract Pair.Immutables.Bool<Immutable.Values> onAddRegister(Receptor<T> core);

        abstract boolean onRegisterRemoved(Receptor<T> core);

        @Override
        public void appoint(Receptor<T> receptor) {
            executorHolder.onAdd(
                    receptor,
                    () -> onAddRegister(receptor)
            );
        }

        @Override
        public void demotionOverride(Receptor<T> receptor) {
            if (onRegisterRemoved(receptor)) executorHolder.deactivate();
        }

        protected PathAbsDispatcher(Holders.ExecutorHolder<T> owner) {
            this.executorHolder = owner;
        }

        abstract boolean isInactive();

    }

    static final class ToManyPathsAbsDispatcher<T> extends PathAbsDispatcher<T> {
        private final SimpleLists.LockFree.Snapshooter<Receptor<T>, Immutable.Values>
                remote;

        ToManyPathsAbsDispatcher(Holders.ExecutorHolder<T> owner) {
            super(owner);
            remote = SimpleLists.getSnapshotting(Receptor.class, this::localSerialValues);
        }

        @Override
        public void dispatch(long delay, Immutable<T> t) {
            //Last local observers on same thread
            scheduleExecution(
                        delay,
                        () -> pathDispatch(false, t)
                );
        }

        @Override
        public void coldDispatch(Immutable<T> t) {
            //From local observers on forked thread (if extended)
            pathDispatch(false, t);
        }

        @Override
        Pair.Immutables.Bool<Immutable.Values> onAddRegister(Receptor<T> core) {
            /*2 registrations may arrive with race conditions, one of them will be eventually removed*/
            return remote.snapshotAdd(
                    core
            );
        }

        @Override
        boolean onRegisterRemoved(Receptor<T> core) {
            return remote.remove(core);
        }


        @Override
        public void pathDispatch(boolean fullyParallel, Immutable<T> t) {
            final Receptor<T>[] subs = remote.copy();
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

        private final ConsumerRegisters.IConsumerRegister.SnapshottingConsumerRegister<Immutable.Values, Immutable<T>>
                remote;

        InjectivePathAbsDispatcher(Holders.ExecutorHolder<T> executorHolder) {
            super(executorHolder);
            remote = ConsumerRegisters.IConsumerRegister.getInstance(this::localSerialValues);
        }

        @Override
        public void dispatch(long delay, Immutable<T> t) {
            pathDispatch(false, t);
        }

        @Override
        public void coldDispatch(Immutable<T> t) {
            pathDispatch(false, t);
        }

        @Override
        public void pathDispatch(boolean fullyParallel, Immutable<T> t) {
            if (remote.isRegistered()) {
                inferColdDispatch(t, remote);
            }
            if (fullyParallel) throw new IllegalStateException("Injective.class dispatch cannot be parallel.");
        }

        @Override
        Pair.Immutables.Bool<Immutable.Values> onAddRegister(Receptor<T> core) {
            return remote.snapshotRegister(
                    core
            );
        }

        @Override
        boolean onRegisterRemoved(Receptor<T> core) {
            return remote.unregister() != null;
        }

        boolean isInactive() {
            return !remote.isRegistered();
        }

    }

    @Override
    public String toString() {
        return super.toString() +
                "\n baseTestDispatcher: " + holder;
    }
}

