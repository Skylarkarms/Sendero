package sendero.switchers;

import java.util.concurrent.atomic.AtomicBoolean;

public class Switchers {
    public static Switch getAtomic() {
        return new Atomic();
    }
    public static Switch getSequential() {
        return new Sequential();
    }
    public interface Switch {
        boolean on();
        boolean off();
        boolean isActive();
    }

    private static class Sequential implements Switch {

        private boolean state;
        @Override
        public boolean on() {
            boolean wasOff = !state;
            if (wasOff) {
                state = true;
            }
            return wasOff;
        }

        @Override
        public boolean off() {
            boolean wasOn = state;
            if (wasOn) {
                state = false;
            }
            return wasOn;
        }

        @Override
        public boolean isActive() {
            return state;
        }

    }

    private static class Atomic implements Switch {
        private final AtomicBoolean state = new AtomicBoolean();
        @Override
        public boolean on() {
            return state.compareAndSet(false, true);
        }

        @Override
        public boolean off() {
            return state.compareAndSet(true, false);
        }

        @Override
        public boolean isActive() {
            return state.get();
        }

    }
}
