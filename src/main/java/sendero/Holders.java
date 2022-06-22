package sendero;

import sendero.atomics.AtomicScheduler;
import sendero.atomics.AtomicUtils;
import sendero.executor.DelayedServiceExecutor;
import sendero.interfaces.BinaryConsumer;
import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;
import sendero.threshold_listener.ThresholdListeners;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static sendero.Holders.SwapBroadcast.NO_DELAY;
import static sendero.Immutable.getNotSet;
import static sendero.functions.Functions.*;

final class Holders {

    interface HolderIO<T> extends Updater<T>, Consumer<T> {
    }

    @FunctionalInterface
    interface ColdConsumer<T> extends Consumer<Immutable<T>>, SynthEqual {
        default boolean equalTo(InputMethod.Type<?, ?> other) {
            return SynthEqual.super.equalTo(0, other);
        }
    }

    private static Object paramAt(SynthEqual self, int at) {
        try {
            return self.getClass().getDeclaredFields()[at].get(self);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    interface SynthEqual {
        static int hashCodeOf(int... hashCodes) {
            if (hashCodes == null)
                return 0;

            int result = 1;

            for (int hashes : hashCodes)
                result = 31 * result + hashes;

            return result;
        }

        default <S> boolean equalTo(int at, S arg) {
            return paramAt(this, at).equals(arg);
        }

        default<S> boolean equalTo(int at, SynthEqual that) {
            return that != null && paramAt(this, at).equals(paramAt(that, at));
        }

        default int hashAt(int at) {
            return paramAt(this, at).hashCode();
        }
    }

    @FunctionalInterface
    interface SwapBroadcast<T> extends SynthEqual {
        long NO_DELAY = -1, HOT = 0;
        void onSwapped(T prev, Immutable<T> next, long delay);
        default void onSwapped(T prev, Immutable<T> next) {
            onSwapped(prev, next, NO_DELAY);
        }

        static<S> SwapBroadcast<S> fromConsumer(Consumer<? super S> consumer) {
            return (prev, next, delay) -> consumer.accept(next.get());
        }

        static <S> SwapBroadcast<S> fromBinaryConsumer(BinaryConsumer<? super S> binaryConsumer) {
            return (prev, next, delay) -> binaryConsumer.accept(prev, next.get());
        }

        default boolean equalTo(SwapBroadcast<?> other) {
            return equalTo(0, other);
        }
    }
    abstract static class ImmutableRead<T> {
        abstract Immutable<T> getSnapshot();
        protected T get() {return getSnapshot().get();}
        boolean isEqual(Immutable<T> other) {
            return other != null && other == getSnapshot();
        }
        Immutable.Values localSerialValues() {
            return getSnapshot().local;
        }

    }
    abstract static class ImmutableReadWrite<T> extends ImmutableRead<T> implements ImmutableWrite<T> {
    }

    @FunctionalInterface
    interface Invalidator {
        void invalidate();
    }

    interface StreamManager<T> extends ColdReceptor<T>, Invalidator {
        static<S> StreamManager<S> getManagerFor(Holder<S> holder, BinaryPredicate<S> test) {
            return new ColdReceptorManager<>(holder, test);
        }
        static<S> StreamManager<S> baseManager(SwapBroadcast<S> broadcast) {
            return new ColdReceptorManager<>(broadcast);
        }
        static<T, S> StreamManager<S> baseManager(
                BiFunction<T,S, T> updatingFun,
                Updater<T> updater) {
            return new ColdReceptorManager<>(
                    (prev, next, delay) -> updater.updateAndGet(
                            t -> updatingFun.apply(t, next.get())
                    )
            );
        }

        static <S> StreamManager<S> getManagerFor(
                Consumer<Runnable> executor,
                SwapBroadcast<S> consumer
        ) {
            return new StreamManagerExecutor<>(executor, consumer);
        }
    }

    private static final class StreamManagerExecutor<T> implements Holders.StreamManager<T> {
        private final AtomicUtils.OverlapDropExecutor executor;
//        private final Consumer<Runnable> executor;
        private final Holders.StreamManager<T> manager;
        private final BiFunction<Immutable.Values, UnaryOperator<T>, Runnable> runnableFactory;

        private StreamManagerExecutor(
                Consumer<Runnable> executor,
                SwapBroadcast<T> coreConsumer
        ) {
            this.executor = new AtomicUtils.OverlapDropExecutor(executor);
            manager = Holders.StreamManager.baseManager(
                    coreConsumer
            );
            /**Manager captured by the Thread instance,
             * fields bound to Thread */
            runnableFactory = (topValues, tInt) -> () -> manager.filterAccept(topValues, tInt);
        }

        @Override
        /**Consumes on Executor thread*/
        public void filterAccept(Immutable.Values topValues, UnaryOperator<T> topData) {
            executor.swap(
//            executor.accept(
                    runnableFactory.apply(topValues, topData)
            );
        }

        @Override
        /**Invalidates atomically*/
        public void invalidate() {
            manager.invalidate();
        }
    }


    static final class ColdReceptorManager<T> implements StreamManager<T> {

        private final ImmutableReadWrite<T> immutableRead;
        final ColdReceptor<T> receptor;

        ColdReceptorManager(SwapBroadcast<T> swapBroadcast) {
            this(new Holder<>(swapBroadcast), BinaryPredicate.always(true));
        }
        ColdReceptorManager(Holder<T> holder, BinaryPredicate<T> test) {
            this.immutableRead = holder;
            receptor = test != binaryAlwaysTrue ?
                    (topValues, topData) -> {
                        Immutable<T> prev;
                        Immutable.Values.LesserThan res;
                        while (
                            /*Back pressure drop*/
                                (res = (prev = getSnapshot()).test(topValues)).isLesser()
                        ) {
                            T prevData = prev.get(), nextData = topData.apply(prevData);
                            if (nextData != prevData && test.test(nextData, prevData)) {
                                Immutable<T> next = res.getNext(prev, topValues, nextData);
                                if (compareAndSet(prev, next)) {
                                    break;
                                }
                            } else break;
                        }
                    }
                    :
                    (topValues, topData) -> {
                        Immutable<T> prev;
                        Immutable.Values.LesserThan res;
                        while (
                            /*Back pressure drop*/
                                (res = (prev = getSnapshot()).test(topValues)).isLesser()
                        ) {
                            T prevData = prev.get(), nextData = topData.apply(prevData);
                            if (nextData != prevData) {
                                Immutable<T> next = res.getNext(prev, topValues, nextData);
                                if (compareAndSet(prev, next)) {
                                    break;
                                }
                            } else break;
                        }
                    };
        }

        @Override
        public void filterAccept(Immutable.Values topValues, UnaryOperator<T> topData) {
            receptor.filterAccept(topValues, topData);
        }

        private Immutable<T> getSnapshot() {
            return immutableRead.getSnapshot();
        }

        private boolean compareAndSet(Immutable<T> prev, Immutable<T> next) {
            return immutableRead.compareAndSet(prev, next);
        }

        @Override
        public void invalidate() {
            immutableRead.invalidate();
        }

        @Override
        public String toString() {
            return "ColdReceptorImpl{" +
                    "\n immutableRead=" + immutableRead +
                    '}';
        }
    }

    static final class Holder<T> extends ImmutableReadWrite<T> implements Supplier<T> {
        private final AtomicReference<Immutable<T>> reference;
        private final SwapBroadcast<T> broadcaster;

        Holder(SwapBroadcast<T> broadcaster) {
            this(broadcaster, getNotSet());
        }

        Holder(
                SwapBroadcast<T> broadcaster,
                Immutable<T> firstValue
        ) {
            this.broadcaster = broadcaster;
            this.reference = new AtomicReference<>(firstValue);
        }

        Holder(
                SwapBroadcast<T> broadcaster,
                AtomicReference<Immutable<T>> reference
        ) {
            this.broadcaster = broadcaster;
            this.reference = reference;
        }

        @Override
        public T get() {
            return reference.get().get();
        }


        @Override
        public void invalidate() {
            reference.updateAndGet(Immutable::invalidate);
        }

        @Override
        public Immutable<T> getSnapshot() {
            return reference.get();
        }

        @Override
        public boolean compareAndSet(Immutable<T> prev, Immutable<T> next, long delay) {
            boolean set = reference.compareAndSet(prev, next);
            if (set) broadcaster.onSwapped(prev.get(), next, delay);
            return set;
        }

        @Override
        public String toString() {
            return "BaseColdHolder{" +
                    "\n reference=" + reference.get().toString() +
                    "\n}: This is: " + getClass().getSimpleName() + "@" + hashCode();
        }
    }

    /**
     * These methods when called from a background thread:...
     * consecutive "losing" threads that got pass this check might get a boost, so we should prevent the override of lesser versions on the other end.
     And the safety measure will end with subscriber's own version of dispatch();*/
    abstract static class DispatcherReader<T> extends ImmutableRead<T> {
        boolean inferColdDispatch(Immutable<T> t, Consumer<Immutable<T>> acceptor) {
            boolean dispatch = isEqual(t);
            if (dispatch) acceptor.accept(t);
            return dispatch;
        }

        boolean inferDispatch(Immutable<T> t, Consumer<? super T> acceptor) {
            boolean dispatch = isEqual(t);
            if (dispatch) acceptor.accept(t.get());
            return dispatch;
        }
    }

    abstract static class BaseBroadcaster<T> extends DispatcherReader<T> {
        public final SwapBroadcast<T> core;

        final Holder<T> holder;
        final BinaryPredicate<T> expectInput;
        final StreamManager<T> streamManager;

        @Override
        Immutable<T> getSnapshot() {
            return holder.getSnapshot();
        }

        protected T getValue() {
            return getSnapshot().get();
        }

        private SwapBroadcast<T> build(Predicate<T> expectOut) {
            return expectOut == alwaysTrue ?
                    (prev, next, delay) -> {
                        onSwapped(prev, next.get());
                        broadcast(prev, next, delay);
                    }
                    :
                    (prevVal, next, delay) -> {
                        T nextData = next.get();
                        onSwapped(prevVal, nextData);
                        if (expectOut.test(nextData)) broadcast( prevVal, next, delay);
                    };
        }

        private final SnapConsumer<T> consumingFunction;
        @FunctionalInterface
        private interface SnapConsumer<T> {
            void accept(Immutable<T> current, Immutable.Values values, ColdConsumer<T> consumer);
        }

        protected BaseBroadcaster(
                UnaryOperator<Builders.HolderBuilder<T>> holderBuilder
        ) {
            Builders.HolderBuilder<T> builder = holderBuilder.apply(Builders.getHolderBuild2());
            Predicate<T> expectOut = builder.expectOut;
            this.consumingFunction = builder(expectOut);
            this.expectInput = builder.expectInput;
            this.core = build(expectOut);
            this.holder = builder.buildHolder(core);
            streamManager = StreamManager.getManagerFor(holder, expectInput);
        }

        private SnapConsumer<T> builder(Predicate<T> expectOut) {
            return expectOut == alwaysTrue ?
                    (currentSnap, values, consumer) -> {
                        if (currentSnap.isSet() && currentSnap.match(values)) {
                            consumer.accept(currentSnap);
                        }
                    }
                    :
                    (currentSnap, values, consumer) -> {
                        if (currentSnap.isSet() && expectOut.test(currentSnap.get()) && currentSnap.match(values)) {
                            consumer.accept(currentSnap);
                        }
            };
        }

        void backGroundPeek(
                Immutable.Values localValues,
                ColdConsumer<T> consumer,
                Consumer<Runnable> executionMethod
        ) {
            Runnable runnable = () -> {
                final Immutable<T> currentSnap = holder.getSnapshot();
                consumingFunction.accept(currentSnap, localValues, consumer);
            };
            executionMethod.accept(runnable);
        }

        /**coldDispatch is triggered by Sendero's inner communication*/
        void coldDispatch(Immutable<T> t) {}

        /**dispatch is triggered by client input*/
        void dispatch(long delay, Immutable<T> t) {}

        @SuppressWarnings("EmptyMethod")
        protected void onSwapped(T prev, T next) {}

        private void broadcast(
                T prevVal, Immutable<T> next, long delay
        ) {
            onSwapped(prevVal, next.get());
            if (delay == NO_DELAY) coldDispatch(next);
            else dispatch(delay, next);
        }
    }

    static class ActivationHolder<T> extends BaseBroadcaster<T> {

        final ActivationManager manager;

        boolean deactivationRequirements() {
            return true;
        }

        protected void setOnStateChangeListener(AtomicBinaryEvent listener) {
            manager.setActivationListener(listener);
        }

        protected boolean clearOnStateChangeListener() {
            return manager.clearActivationListener();
        }

        /**For LinkHolder*/
        boolean activationListenerIsSet() {
            return manager.activationListenerIsSet();
        }

        private ActivationManager buildManager(StreamManager<T> baseTestDispatcher, UnaryOperator<Builders.ManagerBuilder> operator) {
            return isIdentity(operator) ? buildManager() : operator.apply(Builders.getManagerBuild()).build(baseTestDispatcher, this::deactivationRequirements);
        }

        private ActivationManager buildManager() {
            return new ActivationManager(){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }


        ActivationHolder(
                UnaryOperator<Builders.HolderBuilder<T>> holderBuilder,
                UnaryOperator<Builders.ManagerBuilder> mngrBuilderOperator
        ) {
            super(holderBuilder);
            this.manager = buildManager(streamManager, mngrBuilderOperator);
        }

        void onRegistered(
                ColdConsumer<T> consumer,
                Supplier<Pair.Immutables.Bool<Immutable.Values>> snapshotFun,
                Consumer<Runnable> executionMethod
        ) {
            Pair.Immutables.Bool<Immutable.Values> res = snapshotFun.get();
            if (res.aBoolean) tryActivate();
            Immutable.Values snapshot = res.value;
            backGroundPeek(
                    snapshot,
                    consumer,
                    executionMethod
            );
        }

        boolean tryActivate() {
            boolean active = manager.on();
            if (active) onStateChange(true);
            return active;
        }

        /**WARNING: Calls bound to race conditions*/
        protected void onStateChange(boolean isActive) {}

        protected boolean isActive() {
            return manager.isActive();
        }

        boolean tryDeactivate() {
            boolean deactive = manager.off();
            if (deactive) onStateChange(false);
            return deactive;
        }
    }

    abstract static class ExecutorHolder<T> extends ActivationHolder<T> {
        private final EService eService = EService.INSTANCE;

        //Only if isColdHolder == true;
        private final AtomicScheduler scheduler = new AtomicScheduler(eService::getScheduledService, TimeUnit.MILLISECONDS);

        ExecutorHolder(
                UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
                UnaryOperator<Builders.ManagerBuilder> mngrBuilderOperator
        ) {
            super(builderOperator, mngrBuilderOperator);
        }

        ExecutorHolder(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
            this(builderOperator, myIdentity());
        }

        @Override
        boolean tryActivate() {
            eService.increment();
            return super.tryActivate();
        }

        void fastExecute(Runnable action) {
            eService.fastExecute(action);
        }

        void scheduleExecution(long delay, Runnable action) {
            if (delay > 0) scheduler.scheduleOrReplace(delay, action);
            else action.run();
        }

        <S> void parallelDispatch(int beginAt, Consumer<? super S>[] subs, Immutable<T> t, Function<Immutable<T>, S> map) {
            fastExecute(
                    () -> {
                        int length = subs.length;
                        for (int i = beginAt; i < length; i++) {
                            inferColdDispatch(t, t1 -> subs[1].accept(map.apply(t1)));
                        }
                    }
            );
        }

        void onAdd(
                ColdConsumer<T> subscriber,
                Supplier<Pair.Immutables.Bool<Immutable.Values>> snapshotFun
        ) {
            onRegistered(
                    subscriber,
                    snapshotFun,
                    this::fastExecute
            );
        }

        void deactivate() {
            tryDeactivate();
            eService.decrement();
        }

        private static final class LifeCycledThresholdExecutor<S extends ExecutorService> {

            private final ThresholdListeners.ThresholdListener thresholdSwitch = ThresholdListeners.getAtomicOf(
                    0, 0
            );
            private final DelayedServiceExecutor<S> delayedServiceExecutor;

            private LifeCycledThresholdExecutor(long millis, Supplier<S> serviceSupplier, Consumer<S> serviceDestroyer) {
                delayedServiceExecutor = new DelayedServiceExecutor<>(millis, serviceSupplier, serviceDestroyer);
            }

            public void tryActivate() {
                if (thresholdSwitch.increment()) {
                    delayedServiceExecutor.create();
                }
            }

            public void tryDeactivate() {
                if (thresholdSwitch.decrement()) delayedServiceExecutor.destroy();
            }

            public S getService() {
                return delayedServiceExecutor.getService();
            }
        }

        public enum EService {
            INSTANCE;
            private final LifeCycledThresholdExecutor<ScheduledExecutorService> lifeCycledSchedulerExecutor = new LifeCycledThresholdExecutor<>(
                    100,
                    () -> Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors()),
                    ExecutorService::shutdown
            );

            private void create() {
                lifeCycledSchedulerExecutor.tryActivate();
            }

            private void destroy() {
                lifeCycledSchedulerExecutor.tryDeactivate();
            }

            public void increment() {
                create();
            }
            public void decrement() {
                destroy();
            }

            public ScheduledExecutorService getScheduledService() {
                return lifeCycledSchedulerExecutor.getService();
            }

            void fastExecute(Runnable command) {
                lifeCycledSchedulerExecutor.getService().schedule(command, 0, TimeUnit.NANOSECONDS);
            }
        }
    }
}

