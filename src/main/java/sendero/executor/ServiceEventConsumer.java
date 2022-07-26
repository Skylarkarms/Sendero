package sendero.executor;

import sendero.AtomicBinaryEventConsumer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ServiceEventConsumer<S> extends AtomicBinaryEventConsumer {

    @SuppressWarnings("unchecked")
    private final S NOT_SET = (S) new Object();

    S get() {
        S prev = service.get();
        return prev == NOT_SET ? null : prev;
    }

    private final AtomicReference<S> service = new AtomicReference<>(NOT_SET);
    private final Supplier<S> serviceSupplier;
    private final Consumer<S> serviceDestroyer;

    ServiceEventConsumer(Supplier<S> serviceSupplier, Consumer<S> serviceDestroyer) {
        this.serviceSupplier = serviceSupplier;
        this.serviceDestroyer = serviceDestroyer;
    }

    private final Object lock = new Object();

    @Override
    public boolean on() {
        boolean on;
        synchronized (lock) {
            on = super.on();
        }
        return on;
    }

    @Override
    public boolean off() {
        boolean off;
        synchronized (lock) {
            off = super.off();
        }
        return off;
    }

    public S getService() {
        return get();
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) service.set(serviceSupplier.get());
        else serviceDestroyer.accept(service.getAndSet(NOT_SET));
    }

    @Override
    public String toString() {
        return "ServiceEventConsumer{" +
                "service=" + get() +
                "}" +
                ", \n @" + hashCode();
    }
}