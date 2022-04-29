package sendero;

import sendero.atomics.AtomicUtils;
import sendero.functions.Functions;
import sendero.interfaces.AtomicBinaryEventConsumer;
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

final class Holders {

    interface HolderIO<T> extends Updater<T>, Consumer<T>, Supplier<T> {
    }

    interface StatefulHolder<T> extends HolderIO<T> {
        /**The map function happens BEFORE the "expect" predicate test*/
        StatefulHolder<T> setMap(UnaryOperator<T> map);
        StatefulHolder<T> expectIn(Predicate<T> expect);
        StatefulHolder<T> expectIn(BinaryPredicate<T> expect);
        StatefulHolder<T> expectOut(Predicate<T> expect);
    }



    interface ColdHolder<T> extends Supplier<T> {
        void acceptVersionValue(Pair.Immutables.Int<T> versionValue);

        /**@return: the last value*/
        T getAndInvalidate();
    }

    public static class SingleColdHolder<T> implements ColdHolder<T> {
        @SuppressWarnings("unchecked")
        protected final T INVALID = (T) new Object();
        private final Pair.Immutables.Int<T> FIRST = new Pair.Immutables.Int<>(0, INVALID);
        private final Consumer<Pair.Immutables.Int<T>> dispatcher;
        private final BinaryPredicate<T> expect;
        private final AtomicReference<Pair.Immutables.Int<T>> reference;

        SingleColdHolder(
                Consumer<Pair.Immutables.Int<T>> dispatcher,
                BinaryPredicate<T> expect
        ) {
            this.dispatcher = dispatcher;
            this.expect = expect;
            reference = new AtomicReference<>(FIRST);
        }

        private boolean test(T next, T prev) {
            return expect.test(next, prev == INVALID ? null : prev);
        }

        @Override
        public void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
            versionValueCAS(versionValue.getInt(), versionValue.getValue());
        }
        private void versionValueCAS(int newVersion, T nextT) {
            Pair.Immutables.Int<T> prev, next;
            while ((prev = reference.get()).compareTo(newVersion) < 0) {
                if (test(nextT, prev.getValue())) {
                    next = new Pair.Immutables.Int<>(newVersion, nextT);
                    if (reference.compareAndSet(prev, next)) {
                        dispatcher.accept(next);
                        break;
                    }
                } else return;
            }
        }

        @Override
        public T getAndInvalidate() {
            Pair.Immutables.Int<T> pair = reference.getAndSet(FIRST);
            return pair == FIRST ? null : pair.getValue();
        }

        @Override
        public T get() {
            Pair.Immutables.Int<T> pair = reference.get();
            return pair == FIRST ? null : pair.getValue();
        }
    }

    static class TestDispatcher<T> extends Dispatcher<T> {
//        private final Predicate<T> CLEARED_PREDICATE = Functions.always(true);
        private volatile Predicate<T> expectOutput = Functions.always(true);
//        private volatile Predicate<T> expectOutput = CLEARED_PREDICATE;

        public TestDispatcher(Predicate<T> expectOutput) {
            this.expectOutput = expectOutput == null ? Functions.always(true) : expectOutput;
//            this.expectOutput = expectOutput == null ? CLEARED_PREDICATE : expectOutput;
        }

        public TestDispatcher() {
        }

        static final long COLD = -1, HOT = 0;

//        @Override
        protected void setExpectOutput(Predicate<T> expectOutput) {
            this.expectOutput = expectOutput;
        }

        protected void inferDispatch(T prev, Pair.Immutables.Int<T> t, long delay) {
            onSwapped(prev, t.getValue());
            if (expectOutput.test(t.getValue())) {
                if (delay >= HOT) dispatch(delay, t);
                else coldDispatch(t);
            }
            if (delay < COLD) throw new IllegalStateException("Illegal delay value");
        }

        protected boolean outPutTest(T value) {
            return expectOutput.test(value);
        }

    }

    static class DispatcherHolder<T> extends TestDispatcher<T> implements StatefulHolder<T>, ColdHolder<T> {
        @SuppressWarnings("unchecked")
        protected final T INVALID = (T) new Object();
        private final Pair.Immutables.Int<T> FIRST = new Pair.Immutables.Int<>(0, INVALID);
        private final AtomicReference<Pair.Immutables.Int<T>> reference;

        public DispatcherHolder() {
            reference = new AtomicReference<>(FIRST);
        }

        DispatcherHolder(AtomicReference<Pair.Immutables.Int<T>> reference, UnaryOperator<T> map, BinaryPredicate<T> expectInput, Predicate<T> expectOut) {
            super(expectOut);
            this.reference = reference == null ?  new AtomicReference<>(FIRST) : reference;
            this.map = map == null ? CLEARED_MAP : map;
            this.expectInput = expectInput == null ? Functions.binaryAlways(true) : expectInput;
//            this.expectInput = expectInput == null ? CLEARED_PREDICATE : expectInput;
        }

        private final UnaryOperator<T> CLEARED_MAP = UnaryOperator.identity();
        private volatile UnaryOperator<T> map = CLEARED_MAP;
        @Override
        public DispatcherHolder<T> setMap(UnaryOperator<T> map) {
            this.map = map;
            return this;
        }

//        private final BinaryPredicate<T> CLEARED_PREDICATE = Functions.binaryAlways(true);
        private volatile BinaryPredicate<T> expectInput = Functions.binaryAlways(true);
//        private volatile BinaryPredicate<T> expectInput = CLEARED_PREDICATE;
        @Override
        public DispatcherHolder<T> expectIn(Predicate<T> expect) {
            this.expectInput = (next, prev) -> expect.test(next);
//            this.expectInput = expect;
            return this;
        }

        @Override
        public StatefulHolder<T> expectIn(BinaryPredicate<T> expect) {
            this.expectInput = expect;
            return this;
        }

        @Override
        public StatefulHolder<T> expectOut(Predicate<T> expect) {
            setExpectOutput(expect);
            return this;
        }

        private UnaryOperator<T> lazyProcess(UnaryOperator<T> update) {
            return currentValue -> {
                //Not valid if same instance
                try {
                    T nulledInvalid = currentValue == INVALID ? null : currentValue;
                    T updated, mapped;
                    updated = update.apply(nulledInvalid);
                    if (updated == nulledInvalid) {
                        return INVALID;
                    }
                    mapped = map.apply(updated);
                    return expectInput.test(mapped, nulledInvalid) ? mapped : INVALID;
//                    return expectInput.test(mapped) ? mapped : INVALID;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            };
        }

        private void lazyCASAccept(long delay, UnaryOperator<T> t) {
            Pair.Immutables.Int<T> prev = null, next;
            T prevVal, newVal;
            while (prev != (prev = reference.get())) {
                prevVal = prev.getValue();
                newVal = t.apply(prevVal);
                if (newVal != INVALID) {
                    next = new Pair.Immutables.Int<>(prev.getInt() + 1, newVal);
                    if (reference.compareAndSet(prev, next)) {
                        inferDispatch(prevVal, next, delay);
                        break;
                    }
                }
            }
        }
        /**The
         * function should be side-effect-free, since it may be re-applied
         * when attempted updates fail due to contention among threads.*/
        @Override
        public void update(UnaryOperator<T> update) {
            lazyCASProcess(TestDispatcher.HOT ,update);
        }

        @Override
        public void update(long delay, UnaryOperator<T> update) {
            lazyCASProcess(delay, update);
        }

        private void lazyCASProcess(long delay, UnaryOperator<T> update) {
            lazyCASAccept(delay, lazyProcess(update));
        }

//        private T process(T t) {
//            T mapped = map.apply(t);
//            return expectInput.test(mapped, t) ? mapped : INVALID;
////            return expectInput.test(mapped) ? mapped : INVALID;
//        }
        private T process(T next, T prev) {
            T mapped = map.apply(next);
            return expectInput.test(mapped, prev == INVALID ? null : prev) ? mapped : INVALID;
        }

//        private void CASAccept(T t) {
//            if (t == INVALID) return;
//            Pair.Immutables.Int<T> prev = null, next;
//            while (prev != (prev = reference.get())) {
//                next = new Pair.Immutables.Int<>(prev.getInt() + 1, t);
//                if (reference.compareAndSet(prev, next)) {
//                    inferDispatch(prev.getValue(), next, HOT);
//                    break;
//                }
//            }
//        }

        private void CASAccept(T nextT) {
            Pair.Immutables.Int<T> prev = null, next;
            while (prev != (prev = reference.get())) {
                T processed = process(nextT, prev.getValue());
                if (processed != INVALID) {
                    next = new Pair.Immutables.Int<>(prev.getInt() + 1, processed);
                    if (reference.compareAndSet(prev, next)) {
                        inferDispatch(prev.getValue(), next, HOT);
                        break;
                    }
                } else return;
            }
        }

        @Override
        public void accept(T t) {
            CASAccept(t);
//            CASAccept(process(t));
        }

        @Override
        public void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
            versionValueCAS(versionValue.getInt(), versionValue.getValue());
//            versionValueCAS(versionValue.getInt(), process(versionValue.getValue()));
        }

//        private void versionValueCAS(int newVersion, T processed) {
//            if (processed == INVALID) return;
//            Pair.Immutables.Int<T> prev, next;
//            while ((prev = reference.get()).compareTo(newVersion) < 0) {
//                next = new Pair.Immutables.Int<>(newVersion, processed);
//                if (reference.compareAndSet(prev, next)) {
//                    inferDispatch(prev.getValue(), next, TestDispatcher.COLD);
//                    break;
//                }
//            }
//        }

        private void versionValueCAS(int newVersion, T nextT) {
            Pair.Immutables.Int<T> prev, next;
            while ((prev = reference.get()).compareTo(newVersion) < 0) {
                T processed = process(nextT, prev.getValue());
                if (processed != INVALID) {
                    next = new Pair.Immutables.Int<>(newVersion, processed);
                    if (reference.compareAndSet(prev, next)) {
                        inferDispatch(prev.getValue(), next, TestDispatcher.COLD);
                        break;
                    }
                } else return;
            }
        }

        @Override
        public T get() {
            Pair.Immutables.Int<T> pair = reference.get();
            return pair == FIRST ? null : pair.getValue();
        }

        @Override
        public T getAndInvalidate() {
            Pair.Immutables.Int<T> pair = reference.getAndSet(FIRST);
            return pair == FIRST ? null : pair.getValue();
        }

        protected int getVersion() {
            return reference.get().getInt();
        }

        Pair.Immutables.Int<T> getSnapshot() {
            final Pair.Immutables.Int<T> res = reference.get();
            return res != FIRST ? res : null;
        }

    }
    static class ActivationHolder<T> extends Dispatcher<T> {

        boolean deactivationRequirements() {
            return true;
        }

        final ActivationManager manager;

        protected void setOnStateChangeListener(AtomicBinaryEventConsumer listener) {
            manager.setActivationListener(listener);
        }

        protected boolean clearOnStateChangeListener() {
            return manager.clearActivationListener();
        }


        /**For LinkHolder*/
        boolean activationListenerIsSet() {
            return manager.activationListenerIsSet();
        }

        private DispatcherHolder<T> buildHolder() {
            return new DispatcherHolder<T>() {
                @Override
                void dispatch(long delay, Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.dispatch(delay,t);
                }

                @Override
                void coldDispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.coldDispatch(t);
                }

                @Override
                protected void onSwapped(T prev, T next) {
                    ActivationHolder.this.onSwapped(prev, next);
                }
            };
        }

        private ActivationManager buildManager() {
            return new ActivationManager(){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }

        ActivationHolder() {
            holder = buildHolder();
            manager = buildManager();
        }

        ActivationHolder(UnaryOperator<Builders.HolderBuilder<T>> operator, Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
//        ActivationHolder(Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
            holder = Builders.getHolderBuild(operator).build(this);
            manager = new ActivationManager(selfMap.apply(holder::acceptVersionValue)){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }

        ActivationHolder(Builders.HolderBuilder<T> holderBuilder, Function<DispatcherHolder<T>, Builders.ManagerBuilder> actMgmtBuilder) {
            this.holder = holderBuilder.build(this);
            this.manager = actMgmtBuilder.apply(holder).build(this::deactivationRequirements);
        }

        ActivationHolder(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
            this.holder = holderBuilder.build(this);
            this.manager = actMgmtBuilder.build(this::deactivationRequirements);
        }

        ActivationHolder(Builders.HolderBuilder<T> holderBuilder) {
            this.holder = holderBuilder.build(this);
            this.manager = buildManager();
        }

        ActivationHolder(boolean activationListener) {
            holder = buildHolder();
            manager = new ActivationManager(activationListener){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }

        final DispatcherHolder<T> holder;

        <S> void onRegistered(
                Consumer<? super S> subscriber,
                Function<Consumer<? super S>,
                        Pair.Immutables.Bool<Integer>> snapshotFun,
                Function<Pair.Immutables.Int<T>, S> map,
                Consumer<Runnable> executionMethod
        ) {
            Pair.Immutables.Bool<Integer> res = snapshotFun.apply(subscriber);
            if (res.aBoolean) tryActivate();
            int snapshot = res.value;
            Runnable runnable = () -> {
                final Pair.Immutables.Int<T> holderSnap  = holder.getSnapshot();//this method could be passed downStream along
                // with Manager in the executor class, Holder needs to be made package private.
                if (holderSnap != null && snapshot == holderSnap.getInt() && holder.outPutTest(holderSnap.getValue())) {
                    subscriber.accept(map.apply(holderSnap));
                }
            };
            executionMethod.accept(runnable);
        }

        void tryActivate() {
            if (manager.tryActivate()) onStateChange(true);
        }

        /**WARNING: Calls bound to race conditions*/
        protected void onStateChange(boolean isActive) {}

        protected boolean isIdle() {
            return manager.isIdle();
        }

        void tryDeactivate() {
            if (manager.tryDeactivate()) onStateChange(false);
        }

        protected void accept(T t) {
            holder.accept(t);
        }

        protected ActivationHolder<T> setMap(UnaryOperator<T> map) {
            holder.setMap(map);
            return this;
        }

        protected ActivationHolder<T> setExpectInput(Predicate<T> expect) {
            holder.expectIn(expect);
            return this;
        }

        protected ActivationHolder<T> setExpectInput(BinaryPredicate<T> expect) {
            holder.expectIn(expect);
            return this;
        }

        protected void update(UnaryOperator<T> update) {
            holder.update(update);
        }

        protected void update(long delay, UnaryOperator<T> update) {
            holder.update(delay, update);
        }

        protected T get() {
            return holder.get();
        }

        protected int getVersion() {
            return holder.getVersion();
        }

        protected void setExpectOutput(Predicate<T> expectOutput) {
            holder.setExpectOutput(expectOutput);
        }

    }

    abstract static class ExecutorHolder<T> extends ActivationHolder<T> {
        private final EService eService = EService.INSTANCE;

        //Only if isColdHolder == true;
        private final AtomicScheduler scheduler = new AtomicScheduler(eService::getScheduledService, TimeUnit.MILLISECONDS);

        ExecutorHolder(boolean activationListener) {
            super(activationListener);
        }

        ExecutorHolder() {
        }

        public ExecutorHolder(UnaryOperator<Builders.HolderBuilder<T>> operator, Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
            super(operator, selfMap);
        }

        ExecutorHolder(
                Builders.HolderBuilder<T> holderBuilder,
                Function<DispatcherHolder<T>,
                        Builders.ManagerBuilder> actMgmtBuilder
        ) {
            super(holderBuilder, actMgmtBuilder);
        }

        ExecutorHolder(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        ExecutorHolder(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder);
        }

        @Override
        void tryActivate() {
            eService.increment();
            super.tryActivate();
        }

        void fastExecute(Runnable action) {
            eService.fastExecute(action);
        }

        void scheduleExecution(long delay, Runnable action) {
            if (delay > TestDispatcher.HOT) scheduler.scheduleOrReplace(delay, action);
            else action.run();
        }

        <S> void parallelDispatch(int beginAt, Consumer<? super S>[] subs, Pair.Immutables.Int<T> t, Function<Pair.Immutables.Int<T>, S> map) {
            fastExecute(
                    () -> {
                        int length = subs.length;
                        for (int i = beginAt; i < length; i++) {
                            if (t.compareTo(getVersion()) != 0) return;
                            subs[i].accept(map.apply(t));
                        }
                    }
            );
        }

        <S> void onAdd(Consumer<? super S> subscriber, Function<Consumer<? super S>, Pair.Immutables.Bool<Integer>> snapshotFun, Function<Pair.Immutables.Int<T>, S> map) {
            onRegistered(
                    subscriber,
                    snapshotFun,
                    map,
                    this::fastExecute
            );
        }

        void deactivate() {
            tryDeactivate();
            eService.decrement();
        }

        private static abstract class LifeCycledThreshold {
            private final AtomicUtils.SwapScheduler.Long delayer = new AtomicUtils.SwapScheduler.Long(100);
            private final ThresholdListeners.ThresholdListener thresholdSwitch = ThresholdListeners.getAtomicOf(
                    0, 0
            );

            protected abstract void onCreate();
            protected abstract void onDestroy();

            public void tryActivate() {if (thresholdSwitch.increment()) if (!delayer.interrupt()) onCreate();}
            public void tryDeactivate() {if (thresholdSwitch.decrement()) delayer.scheduleOrSwap(this::onDestroy);}
        }

        private static final class LifeCycledExecutor<S extends ExecutorService> extends LifeCycledThreshold {

            private volatile S service;
            private final Supplier<S> serviceSupplier;
            private final Consumer<S> serviceDestroyer;

            private LifeCycledExecutor(Supplier<S> serviceSupplier, Consumer<S> serviceDestroyer) {
                this.serviceSupplier = serviceSupplier;
                this.serviceDestroyer = serviceDestroyer;
            }

            @Override
            protected void onCreate() {
                service = serviceSupplier.get();
            }

            @Override
            protected void onDestroy() {
                serviceDestroyer.accept(service);
            }

            public S getService() {
                return service;
            }
        }

        public enum EService {
            INSTANCE;
            private final LifeCycledExecutor<ScheduledExecutorService> lifeCycledSchedulerExecutor = new LifeCycledExecutor<>(
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

