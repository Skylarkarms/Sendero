package sendero.switchers;

import java.util.concurrent.atomic.AtomicBoolean;

public class Switchers {
    public static Switch getAtomic() {
        return new Atomic();
    }

    public interface Switch {
        boolean on();
        boolean off();
        boolean isActive();
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
