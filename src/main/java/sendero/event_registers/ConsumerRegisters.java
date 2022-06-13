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
    public interface IConsumerRegister<T> extends Consumer<T>{
        boolean isRegistered();
        static<S, T> SnapshottingConsumerRegister<S, T> getInstance(Supplier<S> stateSupplier) {
            return new SnapshottingConsumerRegisterImpl<>(stateSupplier);
        }
        interface Atomic<S, T> extends IConsumerRegister<T> {
            RegisterSnapshot<S> register(Supplier<S> currentValue, Consumer<T> toRegister);
            RegisterSnapshot<S> unregister(Supplier<S> currentValue);

            Consumer<T> unregister();

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

        private final IConsumerRegister.Atomic<S, T> coreRegister = new AtomicConsumerRegister<>();

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
        public RegisterSnapshot<S> register(Supplier<S> currentValue, Consumer<T> toRegister) {
            Consumer<T> prev = null;
            S value;
            while (prev != (prev = ref.get())) {
                value = currentValue.get();
                if (prev != toRegister) {
                    if (ref.compareAndSet(prev, toRegister)) return new RegisterSnapshot<>(value, true);
                }
            }
            return new RegisterSnapshot<>(currentValue.get(), false);

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
        public void accept(T t) {
            ref.get().accept(t);
        }
    }

    public static class BinaryRegisters {

        public static StateAwareBinaryConsumerRegister getStateAware(BooleanSupplier stateSupplier) {
            return new StateAwareBinaryConsumerRegisterImpl(stateSupplier);
        }

        public interface BinaryConsumerRegister extends Switchers.Switch {
            boolean isRegistered();
            AtomicBinaryEvent unregister();

            interface Atomic extends BinaryConsumerRegister {
                /**Keeps spinning until match*/
                BooleanSnapshot register(BooleanSupplier booleanValue, AtomicBinaryEvent toRegister);
                BooleanSnapshot swap(BooleanSupplier booleanValue, AtomicBinaryEvent expect, AtomicBinaryEvent set);
                boolean isEqualTo(AtomicBinaryEvent other);
                final class ConsumerContainer implements Switchers.Switch {
                    public static final ConsumerContainer CLEARED = new ConsumerContainer(AtomicBinaryEvent.DEFAULT);
                    final AtomicBinaryEvent consumer;

                    public ConsumerContainer(AtomicBinaryEvent consumer) {
                        this.consumer = consumer;
                    }

                    public boolean isEqualTo(AtomicBinaryEvent other) {
                        return this.consumer.isDefault() && other.isDefault() || this.consumer == other;
                    }

                    public boolean isDefault() {
                        return consumer.isDefault();
                    }

                    @Override
                    public boolean on() {
                        return consumer.on();
                    }

                    @Override
                    public boolean off() {
                        return consumer.off();
                    }

                    @Override
                    public boolean isActive() {
                        return consumer.isActive();
                    }

                    @Override
                    public String toString() {
                        return "ConsumerContainer{" +
                                "consumer=" + consumer +
                                '}';
                    }
                }
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
            private final BinaryConsumerRegister.Atomic binaryConsumerRegister = new AtomicBinaryConsumerRegisterImpl();

            public StateAwareBinaryConsumerRegisterImpl(BooleanSupplier volatileBinaryState) {
                this.volatileBinaryState = volatileBinaryState;
            }

            @Override
            public boolean isRegistered() {
                return binaryConsumerRegister.isRegistered();
            }

            @Override
            public void registerDispatch(AtomicBinaryEvent newConsumer) {
                BinaryConsumerRegister.Atomic.BooleanSnapshot snapshot2 = binaryConsumerRegister.register(volatileBinaryState, newConsumer);
                if (snapshot2.set) {
                    AtomicBinaryEvent prev = snapshot2.prev;
                    if (prev != null) prev.shutoff();
                    if (snapshot2.snapshotValue && binaryConsumerRegister.isEqualTo(newConsumer)) {
                        newConsumer.start();
                    }
                }
            }

            @Override
            public AtomicBinaryEvent unregisterDispatch() {
                final AtomicBinaryEvent prev = binaryConsumerRegister.unregister();
                prev.shutoff();
                return prev;
            }

            @Override
            public AtomicBinaryEvent unregister() {
                return binaryConsumerRegister.unregister();
            }

            @Override
            public AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
                BinaryConsumerRegister.Atomic.BooleanSnapshot snapshot = binaryConsumerRegister.swap(volatileBinaryState, expect, set);
                AtomicBinaryEvent prev = snapshot.prev;
                if (snapshot.set) {
                    prev.shutoff();
                    if (snapshot.snapshotValue) set.start();
                }
                return prev;
            }

            @Override
            public boolean on() {
                return binaryConsumerRegister.on();
            }

            @Override
            public boolean off() {
                return binaryConsumerRegister.off();
            }

            @Override
            public boolean isActive() {
                return binaryConsumerRegister.isActive();
            }
        }

        private static class AtomicBinaryConsumerRegisterImpl implements BinaryConsumerRegister.Atomic {
            private final AtomicReference<ConsumerContainer> ref2 = new AtomicReference<>(ConsumerContainer.CLEARED);

            @Override
            public BooleanSnapshot register(BooleanSupplier booleanValue, AtomicBinaryEvent toRegister) {
                // keep spinning until match
                ConsumerContainer prev;
                boolean currentValue;
                do {
                    prev = ref2.get();
                    currentValue = booleanValue.getAsBoolean();
                    if (toRegister == null || prev.isEqualTo(toRegister)) return new BooleanSnapshot(currentValue, prev.consumer, false);
                } while (!ref2.compareAndSet(prev, new ConsumerContainer(toRegister)));
                return new BooleanSnapshot(currentValue, prev.consumer, true);
            }

            @Override
            public BooleanSnapshot swap(BooleanSupplier booleanValue, AtomicBinaryEvent expect, AtomicBinaryEvent set) {
                ConsumerContainer prev = ref2.get(), next;
                boolean current = booleanValue.getAsBoolean(), same = true, shouldSet = prev.isEqualTo(expect);
                for (
                        int tries = 0;
                        tries < 3;
                        tries++
                ) {
                    if (!same) {
                        current = booleanValue.getAsBoolean();
                        shouldSet = prev.isEqualTo(expect);
                    }
                    if (shouldSet) {
                        next = new ConsumerContainer(set);
                        if (ref2.compareAndSet(prev, next)) return new BooleanSnapshot(current, prev.consumer, true);
                        else return new BooleanSnapshot(current, null, false);
                    }
                    same = prev == (prev = ref2.get());
                }
                return new BooleanSnapshot(current, prev.consumer, false);
            }

            @Override
            public boolean isEqualTo(AtomicBinaryEvent other) {
                return ref2.get().isEqualTo(other);
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

            @Override
            public AtomicBinaryEvent unregister() {
                ConsumerContainer prev = ref2.getAndSet(ConsumerContainer.CLEARED);
                return prev.consumer;
            }

            @Override
            public boolean isRegistered() {
                return !ref2.get().isDefault();
            }
        }

    }
}
