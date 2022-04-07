package sendero;

import sendero.atomics.AtomicUtils;
import sendero.functions.Functions;
import sendero.interfaces.BooleanConsumer;
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
        StatefulHolder<T> expectOut(Predicate<T> expect);
    }



    interface ColdHolder<T> {
        void acceptVersionValue(Pair.Immutables.Int<T> versionValue);

        /**@return: the last value*/
        T getAndInvalidate();
    }

    public static class SingleColdHolder<T> implements ColdHolder<T> {
        @SuppressWarnings("unchecked")
        protected final T INVALID = (T) new Object();
        private final Pair.Immutables.Int<T> FIRST = new Pair.Immutables.Int<>(0, INVALID);
        private final Consumer<Pair.Immutables.Int<T>> dispatcher;
        private final Predicate<T> expect;
        private final AtomicReference<Pair.Immutables.Int<T>> reference;

        SingleColdHolder(
                Consumer<Pair.Immutables.Int<T>> dispatcher,
                Predicate<T> expect
        ) {
            this.dispatcher = dispatcher;
            this.expect = expect;
            reference = new AtomicReference<>(FIRST);
        }

        private T process(T t) {
            return expect.test(t) ? t : INVALID;
        }
        @Override
        public void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
            versionValueCAS(versionValue.getInt(), process(versionValue.getValue()));
        }
        private void versionValueCAS(int newVersion, T processed) {
            if (processed == INVALID) return;
            Pair.Immutables.Int<T> prev = reference.get(), next = prev;
            for (boolean lesser;;) {
                lesser = prev.compareTo(newVersion) < 0;
                if (lesser) {
                    next = new Pair.Immutables.Int<>(newVersion, processed);
                }
                if (lesser && reference.compareAndSet(prev, next)) {
                    dispatcher.accept(next);
                    break;
                }
                if (prev.compareTo(prev = reference.get()) >= 0) break;
            }
        }

        @Override
        public T getAndInvalidate() {
            Pair.Immutables.Int<T> pair = reference.getAndSet(FIRST);
            return pair == FIRST ? null : pair.getValue();
        }
    }

    static class TestDispatcher<T> extends Dispatcher<T> {
        private final Predicate<T> CLEARED_PREDICATE = Functions.always(true);
        private volatile Predicate<T> expectOutput = CLEARED_PREDICATE;

        public TestDispatcher(Predicate<T> expectOutput) {
            this.expectOutput = expectOutput == null ? CLEARED_PREDICATE : expectOutput;
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

        DispatcherHolder(AtomicReference<Pair.Immutables.Int<T>> reference, UnaryOperator<T> map, Predicate<T> expectInput, Predicate<T> expectOut) {
            super(expectOut);
            this.reference = reference == null ?  new AtomicReference<>(FIRST) : reference;
            this.map = map == null ? CLEARED_MAP : map;
            this.expectInput = expectInput == null ? CLEARED_PREDICATE : expectInput;
        }

        private final UnaryOperator<T> CLEARED_MAP = UnaryOperator.identity();
        private volatile UnaryOperator<T> map = CLEARED_MAP;
        @Override
        public DispatcherHolder<T> setMap(UnaryOperator<T> map) {
            this.map = map;
            return this;
        }

        private final Predicate<T> CLEARED_PREDICATE = Functions.always(true);
        private volatile Predicate<T> expectInput = CLEARED_PREDICATE;
        @Override
        public DispatcherHolder<T> expectIn(Predicate<T> expect) {
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
                    T updated, mapped;
                    updated = update.apply(currentValue);
                    if (updated == currentValue) {
                        return INVALID;
                    }
                    mapped = map.apply(updated);
                    return expectInput.test(mapped) ? mapped : INVALID;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            };
        }

        private void lazyCASAccept(long delay, UnaryOperator<T> t) {
            Pair.Immutables.Int<T> prev = reference.get();
            T newValue = t.apply(prev.getValue());
            Pair.Immutables.Int<T> next = new Pair.Immutables.Int<>(prev.getInt() + 1, newValue);
            boolean wasValid = newValue != INVALID;
            T prevValue = prev.getValue();
            for (boolean changed = false;;) {
                if (changed) {
                    prevValue = prev.getValue();
                    newValue = t.apply(prevValue);
                    next = new Pair.Immutables.Int<>(prev.getInt() + 1, newValue);
                    wasValid = newValue != INVALID;
                }
                if (wasValid && reference.compareAndSet(prev, next)) {
                    inferDispatch(prevValue, next, delay);
                    break;
                }
                if (!(changed = (prev != (prev = reference.get()))) && !wasValid) {
                    break;
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

        private T process(T t) {
            T mapped = map.apply(t);
            return expectInput.test(mapped) ? mapped : INVALID;
        }

        private void CASAccept(T t) {
            if (t == INVALID) return;
            for (Pair.Immutables.Int<T> current;;) {
                current = reference.get();
                final Pair.Immutables.Int<T> newPair = new Pair.Immutables.Int<>(current.getInt() + 1, t);
                if (reference.compareAndSet(current, newPair)) {
                    inferDispatch(current.getValue(), newPair, TestDispatcher.HOT);
                    break;
                }
            }
        }

        @Override
        public void accept(T t) {
            CASAccept(process(t));
        }

        @Override
        public void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
            versionValueCAS(versionValue.getInt(), process(versionValue.getValue()));
        }

        private void versionValueCAS(int newVersion, T processed) {
            if (processed == INVALID) return;
            Pair.Immutables.Int<T> prev = reference.get(), next = prev;
            for (boolean lesser;;) {
                lesser = prev.compareTo(newVersion) < 0;
                if (lesser) {
                    next = new Pair.Immutables.Int<>(newVersion, processed);
                }
                if (lesser && reference.compareAndSet(prev, next)) {
                    inferDispatch(prev.getValue(), next, TestDispatcher.COLD);
                    break;
                }
                if (prev.compareTo(prev = reference.get()) >= 0) break;
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

        protected Pair.Immutables.Int<T> getSnapshot() {
            final Pair.Immutables.Int<T> res = reference.get();
            return res != FIRST ? res : null;
        }

    }
    static class ActivationHolder<T> extends Dispatcher<T> {

        boolean deactivationRequirements() {
            return true;
        }

        final ActivationManager manager;

        protected void setOnStateChangeListener(BooleanConsumer listener) {
            manager.setActivationListener(listener);
        }

        protected boolean clearOnStateChangeListener(BooleanConsumer listener) {
            return manager.expectClearActivationListener(listener);
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

        ActivationHolder(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            holder = buildHolder();
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

//        void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
//            holder.acceptVersionValue(versionValue);
//        }

        protected ActivationHolder<T> setMap(UnaryOperator<T> map) {
            holder.setMap(map);
            return this;
        }

        protected ActivationHolder<T> setExpectInput(Predicate<T> expect) {
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

//        @Override
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

        public ExecutorHolder(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
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

        private void onInactive() {
            tryDeactivate();
            eService.decrement();
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
                            Consumer<? super S> curr = subs[i];
                            if (curr != null) curr.accept(map.apply(t));
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

        protected void deactivate() {
            onInactive();
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

