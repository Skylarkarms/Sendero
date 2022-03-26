package sendero.threshold_listener;

import sendero.event_registers.ConsumerRegister;
import sendero.interfaces.BooleanConsumer;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public final class ThresholdListeners {
    public static ThresholdListener getAtomicOf(int fixedThreshold, int initialValue, BooleanConsumer fixed) {
        return new Atomics.OfFixedThreshold.OfFixedListener(fixedThreshold, initialValue, fixed);
    }
    public static ThresholdListener getAtomicOf(int fixedThreshold, int initialValue) {
        return new Atomics.OfFixedThreshold(fixedThreshold, initialValue);
    }
    public static ThresholdListener getAtomicOfMutable(int initialThreshold, int initialValue/*, BooleanConsumer fixed*/) {
        return new Atomics.OfMutableThreshold(initialThreshold, initialValue/*, fixed*/);
    }
    public static ThresholdListener getAtomicOfMutableListener(int fixedThreshold, int initialValue) {
        return new Atomics.OfFixedThreshold.OfMutableListener(fixedThreshold, initialValue);
    }
    public interface ThresholdListener {
        /**returns true greater (>) than threshold*/
        boolean increment();
//        Witness witnessIncrement();
        /**returns true if less or equal (<=) than threshold*/
        boolean decrement();
//        Witness witnessDecrement();

        /**True if greater than (>) threshold, false if less or equal (<=)*/
        boolean thresholdCrossed();

        /**True if greater, false if less or equal*/
        boolean setCount(int count);

        /**True if greater, false if less or equal*/
        boolean resetCount();

        int getCount();

        default void setListener(BooleanConsumer listener){
            throw new IllegalStateException("Not implemented!!, use getAtomicOfMutableListener(int initialThreshold, int initialValue) version");
        }

        @FunctionalInterface
        interface MutableThreshold {
            boolean setThreshold(int threshold);
        }

    }

    private static final class Atomics {
        private static abstract class AbsThresholdListener implements ThresholdListener {
            private final AtomicInteger count;
            private final Switchers.Switch aSwitch = Switchers.getAtomic();
            private final int initialCount;



            protected AbsThresholdListener(int initialCount) {
                this.initialCount = initialCount;
                count = new AtomicInteger(initialCount);
            }
            protected abstract int threshold();
            private boolean inferTrespass(IntSupplier value) {
                if (value.getAsInt() > threshold()) {
                    return aSwitch.on();
                } else return aSwitch.off();
            }

            protected boolean remapState(IntSupplier thresholdSupplier) {
                if (count.get() > thresholdSupplier.getAsInt()) {
                    return aSwitch.on();
                } else return aSwitch.off();
            }

            @Override
            public boolean increment() {
                return inferTrespass(count::incrementAndGet);
            }

            @Override
            public boolean decrement() {
                return inferTrespass(count::decrementAndGet);
            }

            @Override
            public boolean thresholdCrossed() {
                return aSwitch.isActive();
            }

            @Override
            public boolean setCount(int count) {
                return inferTrespass(() -> {
                    this.count.set(count);
                    return count;
                });
            }

            @Override
            public boolean resetCount() {
                return inferTrespass(() -> {
                    count.set(initialCount);
                    return initialCount;
                });
            }

            @Override
            public int getCount() {
                return count.get();
            }
        }
        private static class OfFixedThreshold extends AbsThresholdListener {
            private final int threshold;
            private OfFixedThreshold(int fixedThreshold, int initialValue) {
                super(initialValue);
                this.threshold = fixedThreshold;
            }

            @Override
            protected int threshold() {
                return threshold;
            }

            private static class OfFixedListener extends OfFixedThreshold {

                private final BooleanConsumer fixedConsumer;
                private OfFixedListener(int threshold, int initialValue, BooleanConsumer fixedConsumer) {
                    super(threshold, initialValue);
                    this.fixedConsumer = fixedConsumer;
                }

                @Override
                public boolean increment() {
                    boolean on = super.increment();
                    if (on) fixedConsumer.accept(true);
                    return on;
                }

                @Override
                public boolean decrement() {
                    boolean off = super.decrement();
                    if (off) fixedConsumer.accept(false);
                    return off;
                }
            }

            private static class OfMutableListener extends OfFixedThreshold implements ThresholdListener {

                private final ConsumerRegister.BinaryRegisters.StateAwareBinaryConsumerRegister register = ConsumerRegister.BinaryRegisters.getStateAware(this::thresholdCrossed);

                private OfMutableListener(int fixedThreshold, int initialValue) {
                    super(fixedThreshold, initialValue);
                }

                @Override
                public boolean increment() {
                    return register.ifAccept(super::increment, true);
                }

                @Override
                public boolean decrement() {
                    return register.ifAccept(super::decrement, false);
                }

                @Override
                public void setListener(BooleanConsumer listener) {
                    register.registerDispatch(listener);
                }
            }
        }

        private static final class OfMutableThreshold extends AbsThresholdListener implements ThresholdListener.MutableThreshold {
            private final AtomicInteger threshold;

            private OfMutableThreshold(int initialThreshold, int initialCount) {
                super(initialCount);
                this.threshold = new AtomicInteger(initialThreshold);
            }

            @Override
            protected int threshold() {
                return threshold.get();
            }

            @Override
            public boolean setThreshold(int threshold) {
                return remapState(
                        () -> {
                            this.threshold.set(threshold);
                            return threshold;
                        }
                );
            }
        }
    }

}
