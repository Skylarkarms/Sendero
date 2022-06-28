package sendero.event_registers;

import sendero.AtomicBinaryEvent;
import sendero.functions.Functions;
import sendero.pairs.Pair;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConsumerRegisters<T> {

    public static<S, T> SnapshottingConsumerRegister<S, T> getSnapshotting(Supplier<S> stateSupplier) {
        return new SnapshottingConsumerRegisterImpl<>(stateSupplier);
    }

    public static StateAwareBinaryConsumerRegister getStateAware(BooleanSupplier stateSupplier) {
        return new StateAwareBinaryConsumerRegisterImpl(stateSupplier);
    }

    public interface SnapshottingConsumerRegister<S, T> extends Consumer<T> {
        boolean isRegistered();
        RegisterSnapshot<S> snapshotRegister(Consumer<T> newListener);
        Consumer<T> unregister();

        final class RegisterSnapshot<T> extends Pair.Immutables.Bool<T> {
            private RegisterSnapshot(T value, boolean set) {
                super(set, value);
            }
        }
    }

    private static final class SnapshottingConsumerRegisterImpl<S, T> implements SnapshottingConsumerRegister<S, T> {

        private final Supplier<S> snapshotSupplier;

        private final Consumer<T> CLEARED = Functions.emptyConsumer();
        private final AtomicReference<Consumer<T>> ref = new AtomicReference<>(CLEARED);

        private SnapshottingConsumerRegisterImpl(Supplier<S> snapshotSupplier) {
            this.snapshotSupplier = snapshotSupplier;
        }

        @Override
        public boolean isRegistered() {
            return ref.get() != CLEARED;
        }

        @Override
        public RegisterSnapshot<S> snapshotRegister(Consumer<T> newListener) {
            Consumer<T> prev = null;
            S value;
            while (prev != (prev = ref.get())) {
                value = snapshotSupplier.get();
                if (prev != newListener) {
                    if (ref.compareAndSet(prev, newListener)) return new RegisterSnapshot<>(value, true);
                }
            }
            return new RegisterSnapshot<>(snapshotSupplier.get(), false);
        }

        @Override
        public Consumer<T> unregister() {
            Consumer<T> removed = ref.getAndSet(CLEARED);
            return removed == CLEARED ? null : removed;
        }

        @Override
        public void accept(T t) {
            ref.get().accept(t);
        }
    }

    interface AtomicBinaryEventRegister {
        AtomicBinaryEvent unregister();

        /**Keeps spinning until match*/
        BooleanSnapshot register(BooleanSupplier booleanValue, AtomicBinaryEvent toRegister);
        final class BooleanSnapshot {
            public final boolean set;
            public final boolean snapshotValue;
            public final AtomicBinaryEvent prev;
            private BooleanSnapshot(boolean snapshotValue, AtomicBinaryEvent prev, boolean set) {
                this.snapshotValue = snapshotValue;
                this.prev = prev;
                this.set = set;
            }
        }
    }

    public interface StateAwareBinaryConsumerRegister extends Switchers.Switch {
        boolean isRegistered();
        void registerDispatch(AtomicBinaryEvent newConsumer);
        AtomicBinaryEvent unregisterDispatch();
        AtomicBinaryEvent unregister();
        /**@return prev*/
        AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set);
    }
    protected static final class StateAwareBinaryConsumerRegisterImpl implements StateAwareBinaryConsumerRegister {
        private final BooleanSupplier volatileBinaryState;
        private final AtomicReference<AtomicBinaryEvent> ref2 = new AtomicReference<>(AtomicBinaryEvent.DEFAULT);

        public StateAwareBinaryConsumerRegisterImpl(BooleanSupplier volatileBinaryState) {
            this.volatileBinaryState = volatileBinaryState;
        }

        @Override
        public boolean isRegistered() {
            return !ref2.get().isDefault();
        }

        @Override
        public void registerDispatch(AtomicBinaryEvent newConsumer) {
            AtomicBinaryEventRegister.BooleanSnapshot snapshot2 = getSetSnapshot(volatileBinaryState, newConsumer);
            if (snapshot2.set) {
                AtomicBinaryEvent prev = snapshot2.prev;
                if (prev != null) prev.shutoff();
                if (snapshot2.snapshotValue && ref2.get() == newConsumer) {
                    newConsumer.start();
                }
            }
        }

        private AtomicBinaryEventRegister.BooleanSnapshot getSetSnapshot(BooleanSupplier booleanValue, AtomicBinaryEvent toRegister) {
            assert toRegister != null;
            // keep spinning until match
            AtomicBinaryEvent prev;
            boolean currentValue;
            do {
                prev = ref2.get();
                currentValue = booleanValue.getAsBoolean();
                if (prev == toRegister) return new AtomicBinaryEventRegister.BooleanSnapshot(currentValue, prev, false);
            } while (!ref2.compareAndSet(prev, toRegister));
            return new AtomicBinaryEventRegister.BooleanSnapshot(currentValue, prev, true);
        }


        @Override
        public AtomicBinaryEvent unregisterDispatch() {
            final AtomicBinaryEvent prev = getAndSetDefault();
            prev.shutoff();
            return prev;
        }

        private AtomicBinaryEvent getAndSetDefault() {
            return ref2.getAndSet(AtomicBinaryEvent.DEFAULT);
        }

        @Override
        public AtomicBinaryEvent unregister() {
            return getAndSetDefault();
        }

        @Override
        public AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
            AtomicBinaryEventRegister.BooleanSnapshot snapshot = getSwapSnapshot(volatileBinaryState, expect, set);
            AtomicBinaryEvent prev = snapshot.prev;
            if (snapshot.set) {
                prev.shutoff();
                if (snapshot.snapshotValue) set.start();
            }
            return prev;
        }

        private AtomicBinaryEventRegister.BooleanSnapshot getSwapSnapshot(BooleanSupplier booleanValue, AtomicBinaryEvent expect, AtomicBinaryEvent set) {
            AtomicBinaryEvent prev = ref2.get();
            boolean current = booleanValue.getAsBoolean(), same = true, shouldSet = prev == expect;
            for (
                    int tries = 0;
                    tries < 3;
                    tries++
            ) {
                if (!same) {
                    current = booleanValue.getAsBoolean();
                    shouldSet = prev == expect;
                }
                if (shouldSet) {
                    if (ref2.compareAndSet(prev, set)) return new AtomicBinaryEventRegister.BooleanSnapshot(current, prev, true);
                    else return new AtomicBinaryEventRegister.BooleanSnapshot(current, null, false);
                }
                same = prev == (prev = ref2.get());
            }
            return new AtomicBinaryEventRegister.BooleanSnapshot(current, prev, false);
        }


        @Override
        public boolean on() {
            return ref2.get().on();
        }

        @Override
        public boolean off() {
            return ref2.get().off();
        }

        @Override
        public boolean isActive() {
            return ref2.get().isActive();
        }
    }
}
