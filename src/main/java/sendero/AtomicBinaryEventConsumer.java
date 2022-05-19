package sendero;

import sendero.pairs.Pair;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public abstract class AtomicBinaryEventConsumer implements Switchers.Switch {
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

    @Override
    public boolean on() {
        boolean isOn = expectAndSetOpposite(false);
        if (isOn) onStateChange(true);
        return isOn;
    }

    @Override
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

    @Override
    public boolean isActive() {
        return isOn(versionedState.get());
    }

    public boolean isShutDown() {
        return versionedState.get() == SHUT_DOWN;
    }

    public boolean isCleared() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public static<S, T> AtomicBinaryEventConsumer switchMapEventConsumer(
            Holders.ColdHolder<T> target,
            BasePath<S> source,
            Function<S, ? extends BasePath<T>> switchMap
    ) {
        final Appointers.BasePathListener<T> pathListener = new Appointers.BasePathListenerImpl<>(target);

        final AtomicBinaryEventConsumer booleanConsumerAppointer = BinaryEventConsumers.producerHolderConnector(
                source,
                new Holders.BaseColdHolder<BasePath<T>>(
                        (next, prev) -> next != null && next != prev
                ) {
                    @Override
                    void coldSwapped(BasePath<T> prev, Pair.Immutables.Int<BasePath<T>> next) {
                        pathListener.setAndStart(next.getValue());
                    }
                },
                (Function<S, BasePath<T>>) switchMap
        );

        return new AtomicBinaryEventConsumer() {
            @Override
            protected void onStateChange(boolean isActive) {
                if (isActive) {
                    pathListener.on();
                    booleanConsumerAppointer.on();
                }
                else {
                    pathListener.off();
                    booleanConsumerAppointer.off();
                }
            }
        };
    }
}
