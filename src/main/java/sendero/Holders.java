package sendero;

import sendero.executor.DelayedServiceExecutor;
import sendero.functions.Functions;
import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.ConsumerUpdater;
import sendero.pairs.Pair;
import sendero.threshold_listener.ThresholdListeners;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static sendero.functions.Functions.isIdentity;
import static sendero.functions.Functions.myIdentity;

final class Holders {

    interface HolderIO<T> extends ConsumerUpdater<T>, Supplier<T> {
    }

    interface ColdHolder<T> extends Supplier<T>, Consumer<Pair.Immutables.Int<T>> {
        /**@return: the last value*/
        T getAndInvalidate();
    }

    public static abstract class BaseColdHolder<T> implements ColdHolder<T> {
        @SuppressWarnings("unchecked")
        protected final T INVALID = (T) new Object();
        final Pair.Immutables.Int<T> FIRST = new Pair.Immutables.Int<>(0, INVALID);
        final AtomicReference<Pair.Immutables.Int<T>> reference;
        private final BinaryPredicate<T> expectInput;

        private final Consumer<Pair.Immutables.Int<T>> coreAcceptor;

        BaseColdHolder() {
            reference = new AtomicReference<>(FIRST);
            expectInput = BinaryPredicate.always(true);
            coreAcceptor = nonTestBuilder();
        }


        BaseColdHolder(
                BinaryPredicate<T> expectInput
        ) {
            this.expectInput = expectInput == null ? BinaryPredicate.always(true) : expectInput;
            coreAcceptor = inferBuild(expectInput);
            this.reference = new AtomicReference<>(FIRST);
        }

        BaseColdHolder(
                AtomicReference<Pair.Immutables.Int<T>> reference,
                BinaryPredicate<T> expectInput
        ) {
            this.expectInput = expectInput == null ? BinaryPredicate.always(true) : expectInput;
            coreAcceptor = inferBuild(expectInput);
            this.reference = reference == null ? new AtomicReference<>(FIRST) : reference;
        }

        @Override
        public void accept(Pair.Immutables.Int<T> tInt) {
            coreAcceptor.accept(tInt);
        }

        protected boolean testIn(T next, T prev) {
            return expectInput.test(next, prev == INVALID ? null : prev);
        }

        private Consumer<Pair.Immutables.Int<T>> inferBuild(BinaryPredicate<T> expectInput) {
            return expectInput == Functions.binaryAlwaysTrue ? nonTestBuilder() : testBuilder();
        }

        private Consumer<Pair.Immutables.Int<T>> testBuilder() {
            return tInt -> {
                Pair.Immutables.Int<T> prev, next;
                int newVersion = tInt.getInt();
                T nextT = tInt.getValue();
                while ((prev = reference.get()).compareTo(newVersion) < 0) {
                    if (testIn(nextT, prev.getValue())) {
                        next = new Pair.Immutables.Int<>(newVersion, nextT);
                        if (reference.compareAndSet(prev, next)) {
                            T prevVal = prev.getValue();
                            coldSwapped(prevVal == INVALID ? null : prevVal, next);
                            break;
                        }
                    } else return;
                }
            };
        }

        private Consumer<Pair.Immutables.Int<T>> nonTestBuilder() {
            return tInt -> {
                Pair.Immutables.Int<T> prev, next;
                int newVersion = tInt.getInt();
                T nextT = tInt.getValue();
                while ((prev = reference.get()).compareTo(newVersion) < 0) {
                    next = new Pair.Immutables.Int<>(newVersion, nextT);
                    if (reference.compareAndSet(prev, next)) {
                        T prevVal = prev.getValue();
                        coldSwapped(prevVal == INVALID ? null : prevVal, next);
                        break;
                    }
                }
            };
        }

        abstract void coldSwapped(T prev, Pair.Immutables.Int<T> next);

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

    static class BaseTestDispatcher<T> extends BaseColdHolder<T>  {
        private final Predicate<T> expectOutput;
        private final Consumer<Pair.Immutables.Int<T>> coldSwapped;
        private final DelayedConsumer<T> hotSwapped;

        private final AbsDispatcher<T> dispatcher;

        private DelayedConsumer<T> noTestDelayed() {
            return dispatcher::dispatch;
        }

        private DelayedConsumer<T> testDelayed() {
            return (delay, t) -> {
                if (expectOutput.test(t.getValue())) {
                    dispatcher.dispatch(delay, t);
                }
            };
        }

        private DelayedConsumer<T> inferDelayerBuild(Predicate<T> expectOut) {
            return expectOut == Functions.alwaysTrue ? noTestDelayed() : testDelayed();
        }

        @FunctionalInterface
        private interface DelayedConsumer<T> {
            void accept(long delay, Pair.Immutables.Int<T> t);
        }

        public BaseTestDispatcher(AbsDispatcher<T> owner) {
            this.expectOutput = Functions.always(true);
            this.dispatcher = owner;
            this.coldSwapped = noTest();
            this.hotSwapped = noTestDelayed();
        }

        public BaseTestDispatcher(
                AtomicReference<Pair.Immutables.Int<T>> reference,
                BinaryPredicate<T> expectIn,
                Predicate<T> expectOutput,
                AbsDispatcher<T> dispatcher
        ) {
            super(reference, expectIn);
            this.expectOutput = expectOutput == null ? Functions.always(true) : expectOutput;
            this.dispatcher = dispatcher;
            coldSwapped = inferBuild(this.expectOutput);
            hotSwapped = inferDelayerBuild(this.expectOutput);
        }

        protected void hotSwapped(T prev, Pair.Immutables.Int<T> t, long delay) {
            T next = t.getValue();
            dispatcher.onSwapped(prev, next);
            hotSwapped.accept(delay, t);
        }

        protected boolean outPutTest(T value) {
            return expectOutput.test(value);
        }

        private Consumer<Pair.Immutables.Int<T>> inferBuild(Predicate<T> expectOut) {
            return expectOut == Functions.alwaysTrue ? noTest() : withTest();
        }
        private Consumer<Pair.Immutables.Int<T>> noTest() {
            return dispatcher::coldDispatch;
        }

        private Consumer<Pair.Immutables.Int<T>> withTest() {
            return next -> {
                if (expectOutput.test(next.getValue())) {
                    dispatcher.coldDispatch(next);
                }
            };
        }

        @Override
        void coldSwapped(T prev, Pair.Immutables.Int<T> next) {
            dispatcher.onSwapped(prev, next.getValue());
            coldSwapped.accept(next);
        }

        Pair.Immutables.Int<T> getSnapshot() {
            final Pair.Immutables.Int<T> res = reference.get();
            return res != FIRST ? res : null;
        }

        protected int getVersion() {
            return reference.get().getInt();
        }

    }

    static class ActivationHolder<T> extends AbsDispatcher<T> {

        final ActivationManager manager;

        boolean deactivationRequirements() {
            return true;
        }

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

        private ActivationManager buildManager(Holders.BaseTestDispatcher<T> baseTestDispatcher, UnaryOperator<Builders.ManagerBuilder> operator) {
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
            this.manager = buildManager(baseTestDispatcher, mngrBuilderOperator);
        }

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
                final Pair.Immutables.Int<T> holderSnap  = getSnapshot();
                if (holderSnap != null && snapshot == holderSnap.getInt() && outPutTest(holderSnap.getValue())) {
                    subscriber.accept(map.apply(holderSnap));
                }
            };
            executionMethod.accept(runnable);
        }

        boolean tryActivate() {
            boolean active = manager.tryActivate();
            if (active) onStateChange(true);
            return active;
        }

        /**WARNING: Calls bound to race conditions*/
        protected void onStateChange(boolean isActive) {}

        protected boolean isIdle() {
            return manager.isIdle();
        }

        boolean tryDeactivate() {
            boolean deactive = manager.tryDeactivate();
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
            super(builderOperator, myIdentity());
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
            if (delay > HOT) scheduler.scheduleOrReplace(delay, action);
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

