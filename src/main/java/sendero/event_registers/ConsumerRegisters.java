package sendero.event_registers;

import sendero.functions.Functions;
import sendero.interfaces.AtomicBinaryEventConsumer;
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
            AtomicBinaryEventConsumer unregister();

            interface Atomic extends BinaryConsumerRegister {
                /**Keeps spinning until match*/
                BooleanSnapshot register(BooleanSupplier booleanValue, AtomicBinaryEventConsumer toRegister);
                BooleanSnapshot swap(BooleanSupplier booleanValue, AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set);
                boolean isEqualTo(AtomicBinaryEventConsumer other);
                final class ConsumerContainer implements Switchers.Switch {
                    public static ConsumerContainer CLEARED = new ConsumerContainer(AtomicBinaryEventConsumer.CLEARED);
                    final AtomicBinaryEventConsumer consumer;

                    public ConsumerContainer(AtomicBinaryEventConsumer consumer) {
                        this.consumer = consumer;
                    }

                    public boolean isEqualTo(AtomicBinaryEventConsumer other) {
                        return this.consumer.isCleared() && other.isCleared() || this.consumer == other;
                    }

                    public boolean isCleared() {
                        return consumer.isCleared();
                    }

                    @Override
                    public boolean on() {
                        return consumer.on();
                    }

                    @Override
                    public boolean off() {
                        return consumer.off();
                    }

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
                    public final AtomicBinaryEventConsumer prev;
                    private BooleanSnapshot(boolean snapshotValue, AtomicBinaryEventConsumer prev, boolean set) {
                        this.snapshotValue = snapshotValue;
                        this.prev = prev;
                        this.set = set;
                    }
                }
            }

        }

        public interface StateAwareBinaryConsumerRegister extends Switchers.Switch {
            boolean isRegistered();
            void registerDispatch(AtomicBinaryEventConsumer newConsumer);
            AtomicBinaryEventConsumer unregisterDispatch();
            AtomicBinaryEventConsumer unregister();
            /**@return prev*/
            AtomicBinaryEventConsumer swapRegister(AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set);
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
            public void registerDispatch(AtomicBinaryEventConsumer newConsumer) {
                BinaryConsumerRegister.Atomic.BooleanSnapshot snapshot2 = binaryConsumerRegister.register(volatileBinaryState, newConsumer);
                if (snapshot2.set) {
                    AtomicBinaryEventConsumer prev = snapshot2.prev;
                    if (prev != null) prev.shutDown();
                    if (snapshot2.snapshotValue && binaryConsumerRegister.isEqualTo(newConsumer)) {
                        newConsumer.start();
                    }
                }
            }

            @Override
            public AtomicBinaryEventConsumer unregisterDispatch() {
                final AtomicBinaryEventConsumer prev = binaryConsumerRegister.unregister();
                prev.shutDown();
                return prev;
            }

            @Override
            public AtomicBinaryEventConsumer unregister() {
                return binaryConsumerRegister.unregister();
            }

            @Override
            public AtomicBinaryEventConsumer swapRegister(AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set) {
                BinaryConsumerRegister.Atomic.BooleanSnapshot snapshot = binaryConsumerRegister.swap(volatileBinaryState, expect, set);
                AtomicBinaryEventConsumer prev = snapshot.prev;
                if (snapshot.set) {
                    prev.shutDown();
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
            public BooleanSnapshot register(BooleanSupplier booleanValue, AtomicBinaryEventConsumer toRegister) {
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
            public BooleanSnapshot swap(BooleanSupplier booleanValue, AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set) {
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
            public boolean isEqualTo(AtomicBinaryEventConsumer other) {
                return ref2.get().isEqualTo(other);
            }

            public boolean on() {
                return ref2.get().on();
            }

            public boolean off() {
                return ref2.get().off();
            }

            @Override
            public boolean isActive() {
                return ref2.get().isActive();
            }

            @Override
            public AtomicBinaryEventConsumer unregister() {
                ConsumerContainer prev = ref2.getAndSet(ConsumerContainer.CLEARED);
                return prev.consumer;
            }

            @Override
            public boolean isRegistered() {
                return !ref2.get().isCleared();
            }
        }

    }
}
