package sendero;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**A 4 stage event consumer with state memorization, that dispatches a binary event true OR false PLUS a onStart phase.
 * Once a SHUT_DOWN stage has been achieved there is no turning back and a new allocation needs to be assigned.
 * */
public abstract class AtomicBinaryEventConsumer implements AtomicBinaryEvent {

    private static final int NOT_SET = -2, SHUT_DOWN = -1, ON = 1, OFF = 0;
    private final AtomicInteger versionedState = new AtomicInteger(NOT_SET);

    private boolean setOff() {
        int prev = setOnOff(OFF);
        return isOn(prev);
    }

    private int setOnOff(int value) {
        int prev;
        do {
            prev = versionedState.get();
            if (prev == SHUT_DOWN) return prev;
        } while (!versionedState.compareAndSet(prev, value));
        return prev;
    }

    private boolean setOn() {
        int prev = setOnOff(ON);
        if (prev == NOT_SET) onStart();
        return !isOn(prev);
    }

    private boolean isOn(int now) {
        return now == ON;
    }

    protected abstract void onStateChange(boolean isActive);

    @Override
    public boolean on() {
        boolean isOn = setOn();
        if (isOn) onStateChange(true);
        return isOn;
    }

    @Override
    public boolean off() {
        boolean isOff = setOff();
        if (isOff) onStateChange(false);
        return isOff;
    }

    @Override
    public boolean shutoff() {
        int prev = versionedState.getAndSet(SHUT_DOWN);
        boolean wasOn = isOn(prev);
        if (wasOn) onStateChange(false);
        if (prev != SHUT_DOWN) onDestroyed();
        return wasOn;
    }

    @Override
    public boolean isOff() {
        return versionedState.get() == SHUT_DOWN;
    }

    /**If a signal arrives first nothing will happen*/
    public boolean start() {
        boolean started = versionedState.compareAndSet(NOT_SET, ON);
        if (started) setStart();
        return started;
    }

    private void setStart() {
        onStart();
        onStateChange(true);
    }

    void onStart() {

    }

    void onDestroyed() {

    }

    @Override
    public boolean isActive() {
        return isOn(versionedState.get());
    }

    public boolean isDefault() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public static<S, T> AtomicBinaryEventConsumer switchMapEventConsumer(
            Holders.StreamManager<T> target,
            BasePath<S> source,
            Function<S, ? extends BasePath<T>> switchMap
    ) {
        final Appointers.ConcurrentProducerSwapper<T> pathListener = new Appointers.ConcurrentProducerSwapper<>(target);

        final AtomicBinaryEvent booleanConsumerAppointer = Builders.BinaryEventConsumers.producerListener(
                source,
        Holders.StreamManager.baseManager(
                (prev, next, delay) -> {
                    BasePath<T> nextP = next.get();
                    pathListener.setAndStart(nextP);
                    if (nextP == null) throw new IllegalStateException("Path is null.");
                }
        ),
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
