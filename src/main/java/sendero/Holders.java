package sendero;

import sendero.functions.Functions;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;
import sendero.threshold_listener.ThresholdListeners;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

final class Holders {

    interface HolderIO<T> extends Updater<T>, Consumer<T>, Supplier<T> {
    }

//    interface StatefulHolder<T> extends BaseHolder<T> {
    interface StatefulHolder<T> extends HolderIO<T> {
        /**The map function happens BEFORE the expect predicate test*/
        StatefulHolder<T> setMap(UnaryOperator<T> map);
        StatefulHolder<T> expectIn(Predicate<T> expect);
        StatefulHolder<T> expectOut(Predicate<T> expect);
    }

    static class DispatcherHolder<T> extends DispatcherImpl<T> implements StatefulHolder<T> {
        @SuppressWarnings("unchecked")
        protected final T INVALID = (T) new Object();
        private final Pair.Immutables.Int<T> FIRST = new Pair.Immutables.Int<>(0, INVALID);
        private final AtomicReference<Pair.Immutables.Int<T>> reference;

//        static<S> Builders.HolderBuilder<S> get(UnaryOperator<Builders.HolderBuilder<S>> op) {
//            return op.apply(Builders.get());
//        }

        public DispatcherHolder() {
            reference = new AtomicReference<>(FIRST);
        }

        DispatcherHolder(AtomicReference<Pair.Immutables.Int<T>> reference, UnaryOperator<T> map, Predicate<T> expectInput) {
            this.reference = reference == null ?  new AtomicReference<>(FIRST) : reference;
            this.map = map == null ? CLEARED_MAP : map;
            this.expectInput = expectInput == null ? CLEARED_PREDICATE : expectInput;
        }

//        DispatcherHolder(T initialValue) {
//            reference = new AtomicReference<>(new Pair.Immutables.Int<>(1, initialValue));
//        }
//
//        DispatcherHolder(T initialValue, Predicate<T> expectOutput) {
//            setExpectOutput(expectOutput);
//            reference = new AtomicReference<>(new Pair.Immutables.Int<>(1, initialValue));
//        }

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
                T updated = update.apply(currentValue);
                if (updated == currentValue) return INVALID;
                T mapped = map.apply(updated);
                return expectInput.test(mapped) ? mapped : INVALID;
            };
        }

        private void lazyCASAccept(UnaryOperator<T> t) {
            Pair.Immutables.Int<T> prev = reference.get();
            T newValue = t.apply(prev.getValue());
            Pair.Immutables.Int<T> next = new Pair.Immutables.Int<>(prev.getInt() + 1, newValue);
            boolean wasValid = newValue != INVALID;
            for (boolean changed = false;;) {
                if (changed) {
                    newValue = t.apply(prev.getValue());
                    next = new Pair.Immutables.Int<>(prev.getInt() + 1, newValue);
                    wasValid = newValue != INVALID;
                }
                if (wasValid && reference.compareAndSet(prev, next)) {
                    inferDispatch(next, false);
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
            lazyCASAccept(lazyProcess(update));
        }
        private T process(T t) {
            T mapped = map.apply(t);
            return expectInput.test(mapped) ? mapped : INVALID;
        }

        protected Pair.Immutables.Int<T> getVolatileValue() {
            return reference.get();
        }
        private void CASAccept(T t) {
            if (t == INVALID) return;
            for (Pair.Immutables.Int<T> current;;) {
                current = reference.get();
                final Pair.Immutables.Int<T> newPair = new Pair.Immutables.Int<>(current.getInt() + 1, t);
                if (reference.compareAndSet(current, newPair)) {
                    inferDispatch(newPair, false);
//                    dispatch(newPair);
                    break;
                }
            }
        }

        @Override
        public void accept(T t) {
            CASAccept(process(t));
        }

        protected void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
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
                    inferDispatch(next, true);
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

        protected Pair.Immutables.Int<T> invalidate() {
            return reference.getAndSet(FIRST);
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

        protected boolean deactivationRequirements() {
            return true;
        }

        /*private*/ final ActivationManager manager;

        /**For LinkHolder*/
        /*protected*/ boolean activationListenerIsSet() {
            return manager.activationListenerIsSet();
        }

        protected boolean clearActivationListener() {
            return manager.clearActivationListener();
        }

        protected BooleanConsumer getAndClearActivationListener() {
            return manager.getAndClearActivationListener();
        }

        protected ActivationHolder() {
            holder = new DispatcherHolder<T>() {
                @Override
                protected void dispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.dispatch(t);
                }

                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.coldDispatch(t);
                }
            };
            manager = new ActivationManager(){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }

        protected ActivationHolder(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            holder = new DispatcherHolder<T>() {
                @Override
                protected void dispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.dispatch(t);
                }

                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.coldDispatch(t);
                }
            };
            manager = new ActivationManager(selfMap.apply(holder::acceptVersionValue)){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }

        protected ActivationHolder(Builders.HolderBuilder<T> holderBuilder, Function<DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
            this.holder = holderBuilder.build(this);
            this.manager = actMgmtBuilder.apply(holder).build(this::deactivationRequirements);
        }

        protected ActivationHolder(Builders.HolderBuilder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
            this.holder = holderBuilder.build(this);
            this.manager = actMgmtBuilder.build(this::deactivationRequirements);
        }

        protected ActivationHolder(boolean activationListener) {
            holder = new DispatcherHolder<T>() {
                @Override
                protected void dispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.dispatch(t);
                }

                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    ActivationHolder.this.coldDispatch(t);
                }
            };
            manager = new ActivationManager(activationListener){
                @Override
                protected boolean deactivationRequirements() {
                    return ActivationHolder.this.deactivationRequirements();
                }
            };
        }

        private final DispatcherHolder<T> holder;

        protected <S> void onRegistered(
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
                // with Manager in the executor class, Holder need sto be made package private.
                if (holderSnap != null && snapshot == holderSnap.getInt() && holder.outPutTest(holderSnap.getValue())) {
                    subscriber.accept(map.apply(holderSnap));
                }
            };
            executionMethod.accept(runnable);
        }

        protected void tryActivate() {
            if (manager.tryActivate()) onStateChange(true);
        }

        /**WARNING: Calls bound to race conditions*/
        protected void onStateChange(boolean isActive) {}

        protected boolean isIdle() {
            return manager.isIdle();
        }

        protected void tryDeactivate() {
            if (manager.tryDeactivate()) onStateChange(false);
        }

        /*protected*/ void setActivationListener(BooleanConsumer activationListener) {
            manager.setActivationListener(activationListener);
        }

        protected Pair.Immutables.Int<T> getSnapshot() {
            return holder.getSnapshot();
        }

        protected void accept(T t) {
            holder.accept(t);
        }

        protected void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
            holder.acceptVersionValue(versionValue);
        }

        protected void superSetMap(UnaryOperator<T> map) {
            holder.setMap(map);
        }

        protected void superSetExpectInput(Predicate<T> expect) {
            holder.expectIn(expect);
        }

        protected void superSetExpectOutput(Predicate<T> expect) {
            holder.expectOut(expect);
        }

        protected void update(UnaryOperator<T> update) {
            holder.update(update);
        }

        protected T get() {
            return holder.get();
        }

        protected int getVersion() {
            return holder.getVersion();
        }

        @Override
        protected void setExpectOutput(Predicate<T> expectOutput) {
            holder.setExpectOutput(expectOutput);
        }

        protected void invalidate() {
            holder.invalidate();
        }

    }

    abstract static class ExecutorHolder<T> extends ActivationHolder<T> {
        private final EService eService = EService.INSTANCE;

        public ExecutorHolder(boolean activationListener) {
            super(activationListener);
        }

        public ExecutorHolder() {
        }

        public ExecutorHolder(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        protected ExecutorHolder(
                Builders.HolderBuilder<T> holderBuilder,
                Function<DispatcherHolder<T>,
                        ActivationManager.Builder> actMgmtBuilder
        ) {
            super(holderBuilder, actMgmtBuilder);
        }

        protected ExecutorHolder(Builders.HolderBuilder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        @Override
        protected void tryActivate() {
            eService.increment();
            super.tryActivate();
        }

        private void onInactive() {
            tryDeactivate();
            eService.decrement();
        }

        protected void execute(Runnable action) {
            eService.execute(action);
        }



        protected <S> void parallelDispatch(int beginAt, Consumer<? super S>[] subs, Pair.Immutables.Int<T> t, Function<Pair.Immutables.Int<T>, S> map) {
            execute(
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

        protected <S> void onAdd(Consumer<? super S> subscriber, Function<Consumer<? super S>, Pair.Immutables.Bool<Integer>> snapshotFun, Function<Pair.Immutables.Int<T>, S> map) {
            onRegistered(
                    subscriber,
                    snapshotFun,
                    map,
                    this::execute
            );
        }

        protected void deactivate() {
            onInactive();
        }

        public enum EService {
            INSTANCE;
            private ExecutorService service;
            private boolean active;
            private final ThresholdListeners.ThresholdListener thresholdSwitch = ThresholdListeners.getAtomicOf(
                    0, 0,
                    isActive -> {
                        if (isActive) create();
                        else destroy();
                    }
            );
            public void create() {
                if (thresholdSwitch.thresholdCrossed() && !active) {
                    active = true;
                    service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                }
            }
            public void destroy() {
                active = false;
                service.shutdown();
            }
            public void increment() {thresholdSwitch.increment();}
            public void decrement() {thresholdSwitch.decrement();}
            public void execute(Runnable runnable) {
                if (thresholdSwitch.thresholdCrossed()
                        && service != null
                        && !service.isShutdown()) {
                    service.execute(runnable);
                }
            }
        }


    }
}

