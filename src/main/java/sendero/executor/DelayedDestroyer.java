package sendero.executor;

import sendero.AtomicBinaryEvent;
import sendero.atomics.AtomicUtils;

class DelayedDestroyer {
    private final AtomicBinaryEvent consumer;
    private final AtomicUtils.OverlapDropExecutor.Long delayer;

    DelayedDestroyer(long millis, AtomicBinaryEvent consumer) {
        this.delayer = new AtomicUtils.OverlapDropExecutor.Long(millis);
        this.consumer = consumer;
    }

    private boolean interrupt() {
        return delayer.interrupt();
    }

    public boolean create() {
        if (!interrupt()) return consumer.on();
        return false;
    }

    public boolean destroy() {
        if (consumer.isActive()) {
            delayer.scheduleOrSwap(
                    consumer::off
            );
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "DelayedDestroyer{" +
                "consumer=" + consumer +
                '}';
    }
}
