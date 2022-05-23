package sendero.executor;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DelayedServiceExecutor<S extends ExecutorService> {
    private final DelayedDestroyer delayedDestroyer;
    private final ServiceEventConsumer<S> sServiceEventConsumer;

    public DelayedServiceExecutor(long millis, Supplier<S> serviceSupplier, Consumer<S> serviceDestroyer) {
        sServiceEventConsumer = new ServiceEventConsumer<>(serviceSupplier, serviceDestroyer);
        delayedDestroyer = new DelayedDestroyer(millis, sServiceEventConsumer);
    }

    public boolean create() {
        return delayedDestroyer.create();
    }

    public boolean destroy() {
        return delayedDestroyer.destroy();
    }

    public S getService() {
        return sServiceEventConsumer.getService();
    }
}
