package sendero.executor;

import sendero.AtomicBinaryEventConsumer;
import sendero.atomics.AtomicUtils;

class DelayedDestroyer {
    private final AtomicBinaryEventConsumer consumer;
    private final AtomicUtils.SwapScheduler.Long delayer;

    DelayedDestroyer(long millis, AtomicBinaryEventConsumer consumer) {
        this.delayer = new AtomicUtils.SwapScheduler.Long(millis);
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
}