package sendero.event_registers;

import sendero.functions.Functions;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Register;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

public class ConsumerRegister<T> implements Consumer<T>, Register<T> {
    private final Consumer<T> NOT_SET = Functions.emptyConsumer();
    private Consumer<T> core = NOT_SET;

    public ConsumerRegister(T t) {
        applyRun(t);
    }

    public ConsumerRegister() {
    }

    @Override
    public void register(Consumer<T> consumer) {
        this.core = consumer;
        if (!isNotAccepted()) {
            makeAccept.run();
        }
    }

    private final Function<T, Runnable> acceptor = t -> () -> core.accept(t);

    private static final Runnable NOT_ACCEPTED = Functions.emptyRunnable();
    private Runnable makeAccept = NOT_ACCEPTED;

    private boolean isNotAccepted() {
        return makeAccept == NOT_ACCEPTED;
    }

    @Override
    public void accept(T t) {
        applyRun(t);
    }

    private void applyRun(T t) {
        makeAccept = acceptor.apply(t);
        makeAccept.run();
    }

    public void unregister() {
        core = NOT_SET;
    }

    public void clearScope() {
        makeAccept = NOT_ACCEPTED;
    }

    public void kill() {
        unregister();
        clearScope();
    }

    public interface IConsumerRegister<T> extends Consumer<T>{
        boolean isRegistered();
        /**locks on to Consumer to maintain versions consistency
         * @return expect.getAsBoolean()*/
        boolean ifAccept(BooleanSupplier expect, T value);
        static<S, T> Atomic<S, T> getAtomic() {
            return new AtomicConsumerRegister<>();
        }
        static<S, T> SnapshottingConsumerRegister<S, T> getInstance(Supplier<S> stateSupplier) {
            return new SnapshottingConsumerRegisterImpl<>(stateSupplier);
        }
        default <Source, Snapshot> Snapshot register(
                Supplier<Source> sourceSupplier,
                Function<Source, Snapshot> onCancel,
                BiFunction<Source, Consumer<T>, Snapshot> onSet,
                Consumer<T> toRegister
        ) {
            return null;
        }

        boolean contains(Consumer<T> valueConsumer);

        interface Atomic<S, T> extends IConsumerRegister<T> {
            RegisterSnapshot<S> register(Supplier<S> currentValue, Consumer<T> toRegister);
            RegisterSnapshot<S> unregister(Supplier<S> currentValue);

            default Consumer<T> unregister() {
                return null;
            }

        }

        interface SnapshottingConsumerRegister<S, T> extends Consumer<T> {
            boolean isRegistered();
            RegisterSnapshot<S> snapshotRegister(Consumer<T> newListener);
            Consumer<T> unregister();
        }

        final class RegisterSnapshot<T> extends Pair.Immutables.Bool<T> {

            private RegisterSnapshot(T value, boolean set) {
                super(set, value);
            }
        }

    }
    private static final class SnapshottingConsumerRegisterImpl<S, T> implements IConsumerRegister.SnapshottingConsumerRegister<S, T> {

        private final Supplier<S> snapshotSupplier;

        private final IConsumerRegister.Atomic<S, T> coreRegister = IConsumerRegister.getAtomic();

        private SnapshottingConsumerRegisterImpl(Supplier<S> snapshotSupplier) {
            this.snapshotSupplier = snapshotSupplier;
        }

        @Override
        public boolean isRegistered() {
            return coreRegister.isRegistered();
        }

        @Override
        public IConsumerRegister.RegisterSnapshot<S> snapshotRegister(Consumer<T> newListener) {
            return coreRegister.register(snapshotSupplier, newListener);
        }

        @Override
        public Consumer<T> unregister() {
            return coreRegister.unregister();
        }

        @Override
        public void accept(T t) {
            coreRegister.accept(t);
        }
    }

    private static class AtomicConsumerRegister<S, T> implements IConsumerRegister.Atomic<S, T> {

        private final Consumer<T> CLEARED = Functions.emptyConsumer();
        private final AtomicReference<Consumer<T>> ref = new AtomicReference<>(CLEARED);

        @Override
        public boolean isRegistered() {
            return ref.get() != CLEARED;
        }

        @Override
        public boolean ifAccept(BooleanSupplier expect, T value) {
            boolean shouldDeliver = expect.getAsBoolean();
            if (shouldDeliver) ref.get().accept(value);
            return shouldDeliver;
        }

        @Override
        public RegisterSnapshot<S> register(Supplier<S> currentValue, Consumer<T> toRegister) {
            Consumer<T> prev = ref.get();
            boolean shouldReplace = prev != toRegister;
            S current = currentValue.get();
            RegisterSnapshot<S> snapshot = !shouldReplace ?
                    new RegisterSnapshot<>(current, false) :
                    new RegisterSnapshot<>(current, true);
            for (boolean same = true;;) {
                if (!same) {
                    current = currentValue.get();
                    shouldReplace = prev != toRegister;
                    snapshot = !shouldReplace ?
                            new RegisterSnapshot<>(current, false) :
                            new RegisterSnapshot<>(current, true);
                }
                if (shouldReplace && ref.compareAndSet(prev, toRegister)) return snapshot;
                if (same == (same = (prev == (prev = ref.get())))) return snapshot;
            }

        }

        @Override
        public <Source, Snapshot> Snapshot register(
                Supplier<Source> sourceSupplier,
                Function<Source, Snapshot> onCancel,
                BiFunction<Source, Consumer<T>, Snapshot> onSet,
                Consumer<T> toRegister
        ) {

            Consumer<T> prev = ref.get();
            boolean shouldReplace = prev != toRegister;
            Source current = sourceSupplier.get();
            Snapshot snapshot = !shouldReplace ?
                    onCancel.apply(current) :
                    onSet.apply(current, prev);
            for (boolean same = true;;) {
                if (!same) {
                    current = sourceSupplier.get();
                    shouldReplace = prev != toRegister;
                    snapshot = !shouldReplace ?
                            onCancel.apply(current) :
                            onSet.apply(current, prev);
                }
                if (shouldReplace && ref.compareAndSet(prev, toRegister)) return snapshot;
                if (same == (same = (prev == (prev = ref.get())))) return snapshot;
            }
        }

        @Override
        public RegisterSnapshot<S> unregister(Supplier<S> currentValue) {
            Consumer<T> prev;
            S curVal;
            do {
                prev = ref.get();
                curVal = currentValue.get();
            } while (!ref.compareAndSet(prev, CLEARED));
            return new RegisterSnapshot<>(curVal, false);
        }

        @Override
        public Consumer<T> unregister() {
            Consumer<T> removed = ref.getAndSet(CLEARED);
            return removed == CLEARED ? null : removed;
        }

    @Override
        public boolean contains(Consumer<T> valueConsumer) {
            return ref.get() == valueConsumer;
        }

        @Override
        public void accept(T t) {
            ref.get().accept(t);
        }
    }

    public static class BinaryRegisters {
        public static BinaryConsumerRegister.Sequential getSequential() {
            return new Sequential();
        }

        public static StateAwareBinaryConsumerRegister getStateAware(BooleanSupplier stateSupplier) {
            return new StateAwareBinaryConsumerRegisterImpl(stateSupplier);
        }

        public interface BinaryConsumerRegister extends BooleanConsumer {
            boolean isRegistered();
            /**returns expect.getAsBoolean()*/
            boolean ifAccept(BooleanSupplier expect, boolean value);

            BooleanConsumer unregister();

            interface Sequential extends BinaryConsumerRegister {
                BooleanConsumer register(BooleanConsumer newConsumer);
            }
            interface Atomic extends BinaryConsumerRegister {
                BooleanSnapshot register(BooleanSupplier booleanValue, BooleanConsumer toRegister);
                BooleanSnapshot unregister(BooleanSupplier booleanValue);
                /**Retries until there is no more contention*/
                BooleanSnapshot unregister(BooleanConsumer expect, BooleanSupplier booleanValue);
                final class BooleanSnapshot {
                    public final boolean set;
                    public final boolean snapshotValue;
                    public final BooleanConsumer prev;
                    private BooleanSnapshot(boolean snapshotValue, BooleanConsumer prev, boolean set) {
                        this.snapshotValue = snapshotValue;
                        this.prev = prev;
                        this.set = set;
                    }
                }
            }

        }
        private static class Sequential implements BinaryConsumerRegister.Sequential {
            private final BooleanConsumer CLEARED = BooleanConsumer.cleared();
            private BooleanConsumer consumer = CLEARED;

            @Override
            public void accept(boolean aBoolean) {
                consumer.accept(aBoolean);
            }

            @Override
            public BooleanConsumer register(BooleanConsumer newConsumer) {
                final BooleanConsumer last = consumer;
                consumer = newConsumer;
                return last;
            }

            @Override
            public BooleanConsumer unregister() {
                final BooleanConsumer last = consumer;
                consumer = CLEARED;
                return last;
            }

            @Override
            public boolean isRegistered() {
                return consumer != CLEARED;
            }

            @Override
            public boolean ifAccept(BooleanSupplier expect, boolean value) {
                boolean success = expect.getAsBoolean();
                if (success) {
                    consumer.accept(value);
                }
                return success;
            }
        }
        public interface StateAwareBinaryConsumerRegister extends BooleanConsumer {
            boolean isRegistered();
            boolean ifAccept(BooleanSupplier expect, boolean aBoolean);
            void registerDispatch(BooleanConsumer newConsumer);
            /**Dispatches false if the state was true at thte moment of unregister
             * @return*/
            BooleanConsumer unregisterDispatch();
            BooleanConsumer unregisterDispatch(BooleanConsumer expect);
            BooleanConsumer unregister();
        }
        protected static final class StateAwareBinaryConsumerRegisterImpl implements StateAwareBinaryConsumerRegister {
            private final BooleanSupplier volatileBinaryState;
            private final BinaryConsumerRegister.Atomic binaryConsumerRegister = new AtomicBinaryConsumerRegisterImpl();

            public StateAwareBinaryConsumerRegisterImpl(BooleanSupplier volatileBinaryState) {
                this.volatileBinaryState = volatileBinaryState;
            }

            @Override
            public void accept(boolean aBoolean) {
                binaryConsumerRegister.accept(aBoolean);
            }

            @Override
            public boolean isRegistered() {
                return binaryConsumerRegister.isRegistered();
            }

            @Override
            public boolean ifAccept(BooleanSupplier expect, boolean aBoolean) {
                return binaryConsumerRegister.ifAccept(expect, aBoolean);
            }

            private BinaryConsumerRegister.Atomic.BooleanSnapshot register(BooleanConsumer newConsumer) {
                return binaryConsumerRegister.register(volatileBinaryState, newConsumer);
            }

            /**Attaching a lambda BooleanConsumer, whose captured content may have changed will:
             * reconnect an observer that is already present in the Collection
             * disconnect one of the two repeating observers.
             *
             * Everything happens in the same thread.
             * */
            @Override
            public void registerDispatch(BooleanConsumer newConsumer) {
                boolean wasActive = volatileBinaryState.getAsBoolean();
                //If a false arrives immediately AFTER the process of registration, a false boolean may escape and get consumed by booleanConsumer
                //Thankfully we check isActive a second time to prevent us from overriding it's delivery *A.
                final BinaryConsumerRegister.Atomic.BooleanSnapshot booleanSnapshot = register(
                        newConsumer
                );
                //Not set, same consumer as before.
                if (!booleanSnapshot.set) return;

                final BooleanConsumer prev = booleanSnapshot.prev;
                // *A) We should check if state is still active... as something may have changed.
                if (booleanSnapshot.snapshotValue && volatileBinaryState.getAsBoolean()) {

                    //Delivery here is faster than an off() so there is no danger on this call arriving AFTER actual calls to off(); (accept(false))
                    // if an off() is in the process of delivering a false, state.isActive() would have been false.
                    // if an off() happened AFTER a read to state.isActive(), then booleanConsumer.accept(true); is already happening BEFORE the off() and will surely be overridden by it's eventual false.
                    newConsumer.accept(true);
                    if (prev != null) prev.accept(false);

                    //-------------------------

                    /*A false value always arrives to state, before entering the accept method.
                     * If a registration is performing a CAS in the middle of this process,
                     * The snapshot will reflect the volatile value (false) and will assume the Consumer has already received this value.
                     * If the CAS happens BEFORE the dispatch actually takes place, the replaced consumer will NEVER receive the false, when it should have, as it was true.
                     * this is a bug and will prevent the newly changed false to be dispatched to the prev consumer.
                     * For this to happen, a wasActive == true MUST have been present, BEFORE register CAS.
                     * */

                    // If wasActive == false, BEFORE the registration, it should be assumed that the value was always false, hence already consumed by consumer.
                    // If wasActive == true, then we assume something went wrong during the process of registration and deliver a false **just in case**.

                    //The time spent between wasActive and booleanSnapshot creation may vary depending on contention.
                    //The more contention there is the more obsolete this method will be, as the atomic value of state
                    // may change multiple times during the process of spinLock queue.
                } else if (!booleanSnapshot.snapshotValue && wasActive) {
                    if (prev != null) prev.accept(false);
                    //The way of mitigating bugs under contention is that, these faults will be limited to the scope
                    // of the visiting thread, instead of the atomic value.

                    //Again the worst case scenario would be a repeated false.
                    //But if reusage of the same thread is abused by the register method in an intermittent manner.
                    //Then the atomic pipeline provided by the CAS will offer a "happens before" and "after" which
                    // will provide a sense of sequentialism, with a slim margin of error.
                    //And the signal will be: true (on register XConsumer), a false dispatched to XConsumer (when registering YConsumer), true (on register XConsumer again), false, true, false, true, etc...
                }
            }

            @Override
            public BooleanConsumer unregisterDispatch() {
                final BinaryConsumerRegister.Atomic.BooleanSnapshot removed = snapshottingUnregister();
                final BooleanConsumer prev = removed.prev;
                if (removed.snapshotValue) {
                    if (prev != null) prev.accept(false);
                }
                return prev;
            }

            @Override
            public BooleanConsumer unregisterDispatch(BooleanConsumer expect) {
                final BinaryConsumerRegister.Atomic.BooleanSnapshot removed = snapshottingUnregister(expect);
                final BooleanConsumer prev = removed.prev;
                if (removed.snapshotValue) {
                    if (prev != null) prev.accept(false);
                }
                return prev;
            }

            @Override
            public BooleanConsumer unregister() {
                return binaryConsumerRegister.unregister();
            }

            private BinaryConsumerRegister.Atomic.BooleanSnapshot snapshottingUnregister() {
                return binaryConsumerRegister.unregister(volatileBinaryState);
            }
            private BinaryConsumerRegister.Atomic.BooleanSnapshot snapshottingUnregister(BooleanConsumer expect) {
                return binaryConsumerRegister.unregister(expect, volatileBinaryState);
            }
        }

        private static class AtomicBinaryConsumerRegisterImpl implements BinaryConsumerRegister.Atomic {
            private final BooleanConsumer CLEARED = BooleanConsumer.cleared();
            private final AtomicReference<BooleanConsumer> ref = new AtomicReference<>(CLEARED);

            @Override
            public BooleanSnapshot unregister(BooleanSupplier booleanValue) {
                boolean current;
                BooleanConsumer prev;
                do {
                    prev = ref.get();
                    current = booleanValue.getAsBoolean();
                } while (!ref.compareAndSet(prev, CLEARED));
                return new BooleanSnapshot(current, prev == CLEARED ? null : prev, true);
            }

            @Override
            public BooleanSnapshot unregister(BooleanConsumer expect, BooleanSupplier booleanValue) {
                BooleanConsumer prev = ref.get();
                boolean current = booleanValue.getAsBoolean(), set = prev == expect;
                for (boolean same = true;;) {
                    if (!same) {
                        current = booleanValue.getAsBoolean();
                        set = prev == expect;
                    }
                    if (set && ref.compareAndSet(prev, CLEARED)) break;
                    if (same == (prev == (prev = ref.get()))) break; //If same then !set
                }
                return new BooleanSnapshot(current, prev == CLEARED ? null : prev, set);
            }

            @Override
            public BooleanSnapshot register(BooleanSupplier booleanValue, BooleanConsumer toRegister) {
                boolean current = booleanValue.getAsBoolean();
                BooleanConsumer prev = ref.get();
                boolean shouldReplace = prev != toRegister;
                BooleanSnapshot snapshot = !shouldReplace ?
                        new BooleanSnapshot(current, toRegister, false) :
                        new BooleanSnapshot(current, prev == CLEARED ? null : prev, true);

                for (boolean same = true;;) {
                    if (!same) {
                        current = booleanValue.getAsBoolean();
                        shouldReplace = prev != toRegister;
                        snapshot = !shouldReplace ?
                                new BooleanSnapshot(current, toRegister, false) :
                                new BooleanSnapshot(current, prev == CLEARED ? null : prev, true);
                    }
                    if (shouldReplace && ref.compareAndSet(prev, toRegister)) return snapshot;
                    if (same == (same = (prev == (prev = ref.get())))) return snapshot;
                }

            }

            @Override
            public void accept(boolean aBoolean) {
                ref.get().accept(aBoolean);
            }

            @Override
            public BooleanConsumer unregister() {
                BooleanConsumer prev = ref.getAndSet(CLEARED);
                return prev == CLEARED ? null : prev;
            }

            @Override
            public boolean isRegistered() {
                return ref.get() != CLEARED;
            }

            @Override
            public boolean ifAccept(BooleanSupplier expect, boolean value) {
                boolean success = expect.getAsBoolean();
                if (success) ref.get().accept(value);
                return success;
            }
        }

    }
}
