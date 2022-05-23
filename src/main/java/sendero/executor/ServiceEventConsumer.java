package sendero.executor;

import sendero.AtomicBinaryEventConsumer;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ServiceEventConsumer<S extends ExecutorService> extends AtomicBinaryEventConsumer {

    private volatile S service;
    private final Supplier<S> serviceSupplier;
    private final Consumer<S> serviceDestroyer;

    ServiceEventConsumer(Supplier<S> serviceSupplier, Consumer<S> serviceDestroyer) {
        this.serviceSupplier = serviceSupplier;
        this.serviceDestroyer = serviceDestroyer;
    }

    public S getService() {
        return service;
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) service = serviceSupplier.get();
        else serviceDestroyer.accept(service);
    }
}
