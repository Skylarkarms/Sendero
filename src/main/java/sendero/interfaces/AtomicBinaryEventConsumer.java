package sendero.interfaces;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AtomicBinaryEventConsumer {
    private static final int SHUT_DOWN = -1, ON = 1, OFF = 0;
    private final AtomicInteger versionedState = new AtomicInteger(SHUT_DOWN);

    private boolean expectAndSetOpposite(boolean expectIsOn) {
        int next = expectIsOn ? OFF : ON;
        int prev = versionedState.getAndSet(next);
        return isOn(prev) == expectIsOn;
    }

    private boolean isOn(int now) {
        return now == ON;
    }

    public static final AtomicBinaryEventConsumer CLEARED = new AtomicBinaryEventConsumer() {
        @Override
        protected void onStateChange(boolean isActive) {

        }

        @Override
        public boolean on() {
            return false;
        }

        @Override
        public boolean off() {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean shutDown() {
            return false;
        }

        @Override
        public boolean isCleared() {
            return true;
        }

        @Override
        public String toString() {
            return  getClass() + " is CLEARED";
        }
    };

    protected abstract void onStateChange(boolean isActive);

    public boolean on() {
        boolean isOn = expectAndSetOpposite(false);
        if (isOn) onStateChange(true);
        return isOn;
    }

    public boolean off() {
        boolean isOff = expectAndSetOpposite(true);
        if (isOff) onStateChange(false);
        return isOff;
    }

    public boolean shutDown() {
        int prev = versionedState.getAndSet(SHUT_DOWN);
        boolean wasOn = isOn(prev);
        if (wasOn) onStateChange(false);
        return wasOn;
    }

    /**If a signal arrives first nothing will happen*/
    public boolean start() {
        boolean started = versionedState.compareAndSet(SHUT_DOWN, ON);
        if (started) onStateChange(true);
        return started;
    }

    public boolean isActive() {
        return isOn(versionedState.get());
    }

    public boolean isShutDown() {
        return versionedState.get() == SHUT_DOWN;
    }

    public boolean isCleared() {
        return false;
    }
}
