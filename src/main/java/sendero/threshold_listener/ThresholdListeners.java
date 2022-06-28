package sendero.threshold_listener;

import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public final class ThresholdListeners {
    public static ThresholdListener getAtomicOf(int fixedThreshold, int initialValue) {
        return new Atomics.OfFixedThreshold(fixedThreshold, initialValue);
    }
    public interface ThresholdListener {
        /**returns true greater (>) than threshold*/
        boolean increment();
        /**returns true if less or equal (<=) than threshold*/
        boolean decrement();

        /**True if greater than (>) threshold, false if less or equal (<=)*/
        boolean thresholdCrossed();

        /**True if greater, false if less or equal*/
        boolean setCount(int count);

        /**True if greater, false if less or equal*/
        boolean resetCount();

        int getCount();
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
        }
    }

}
