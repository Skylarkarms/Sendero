package sendero;

import sendero.atomics.LazyHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public class ProactiveSuppliers<T> implements ProactiveSupplier<T> {

    final Holders.ActivationHolder<T> activationHolder;

    public static<T> Unbound<T> unbound(UnaryOperator<Builders.HolderBuilder<T>> operator) {
        return new Unbound<>(operator);
    }

    public static<T> Unbound<T> unbound() {
        return unbound(myIdentity());
    }

    public static<S, T> Bound<T> bound(UnaryOperator<Builders.HolderBuilder<T>> operator, BasePath<S> source, Function<S, T> map) {
        return new Bound<>(operator, source, map);
    }

    public static <S, T> Bound<T> bound(BasePath<S> source, Function<S, T> map) {
        return bound(myIdentity(), source, map);
    }

    public static <T> Bound<T> bound(UnaryOperator<Builders.HolderBuilder<T>> operator, BasePath<T> source) {
        return bound(operator, source, myIdentity());
    }

    public static <T> Bound<T> bound(BasePath<T> source) {
        return bound(myIdentity(), source, myIdentity());
    }

    private static final Builders.ManagerBuilder mutabilityAllowedOperator = Builders.ManagerBuilder.mutable();

    public static class Bound<T> extends ProactiveSuppliers<T> {

        <S>Bound(UnaryOperator<Builders.HolderBuilder<T>> holderBuilder, BasePath<S> source, Function<S, T> map) {
            super(holderBuilder,
                    streamManager -> Builders.BinaryEventConsumers.producerListener(source, streamManager, map)

            );
        }
    }

    public static class Unbound<T> extends ProactiveSuppliers<T> implements UnboundLink<T> {

        private final BaseUnbound<T> baseUnbound;

        Unbound(
                UnaryOperator<Builders.HolderBuilder<T>> holderBuilder
        ) {
            super(holderBuilder, null);
            baseUnbound = new BaseUnbound<>(activationHolder);
        }

        @Override
        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
            baseUnbound.switchMap(path, switchMap);
        }

        @Override
        public <S, P extends BasePath<S>> Void bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
            return baseUnbound.bind(basePath, inputMethod);
        }
    }


    private void NOT_ACTIVE_WARNING() {
        if (!isActive())
            System.err.println("This Supplier has not been activated yet!!");
    }
    
    ProactiveSuppliers(
            UnaryOperator<Builders.HolderBuilder<T>> holderBuilder,
            Function<Holders.StreamManager<T>, AtomicBinaryEvent> function
    ) {
        final Builders.ManagerBuilder finalOp = function == null ? mutabilityAllowedOperator : Builders.ManagerBuilder.withFixed(function);
        activationHolder = new Holders.ActivationHolder<>(holderBuilder, finalOp);
        on();
    }

    private final Immutable<T> NOT_SET = Immutable.getNotSet();

    @Override
    public T get() {
        NOT_ACTIVE_WARNING();
        Immutable<T> imm = activationHolder.getSnapshot();
        assert imm != NOT_SET;
        return imm.get();
    }
    private static final int max_tries = 3;

    private final LazyHolder<ExecutorService> lazyExecutor = new LazyHolder<>(
            () -> Executors.newFixedThreadPool(2),
            100,
            ExecutorService::shutdown
    );

    @SuppressWarnings("BusyWait")
    private void deliver(long delay, Consumer<? super T> consumer) {
        if (delay == 0) {
            zeroDelay(consumer);
        } else {
            lazyExecutor.get().execute(
                    () -> {
                        int tries = 0;
                        Immutable<T> snapshot;
                        do {
                            Immutable<T> nullable = activationHolder.getSnapshot();
                            snapshot = nullable == null ? NOT_SET : nullable;
                            try {
                                Thread.sleep(delay);
                                tries++;
                            } catch (InterruptedException ignored) {
                            }
                        } while (tries < max_tries && !snapshot.match(activationHolder.localSerialValues()));
                        makeAccept(snapshot, consumer);
                    }
            );
        }
    }

    private void zeroDelay(Consumer<? super T> consumer) {
        Immutable<T> snapshot = NOT_SET;
        int tries = 0;
        while (tries < max_tries && !snapshot.match(activationHolder.localSerialValues())) {
            Immutable<T> nullable = activationHolder.getSnapshot();
            snapshot = nullable == null ? NOT_SET : nullable;
            tries++;
        }
        makeAccept(snapshot, consumer);
    }

    private void makeAccept(Immutable<T> snapshot, Consumer<? super T> consumer) {
        final T finalT = snapshot.isSet() ? snapshot.get() : null;
        NOT_ACTIVE_WARNING();
        consumer.accept(finalT);
    }

    @Override
    public void get(Consumer<? super T> tConsumer) {
        zeroDelay(tConsumer);
    }

    @Override
    public void get(long delay, Consumer<? super T> tConsumer) {
        deliver(delay, tConsumer);
    }

    @Override
    public boolean on() {
        return activationHolder.tryActivate();
    }

    @Override
    public boolean off() {
        boolean deactive = activationHolder.tryDeactivate();
        if (deactive) lazyExecutor.destroy();
        return deactive;
    }

    @Override
    public boolean isActive() {
        return activationHolder.isActive();
    }

    @Override
    public String toString() {
        return "ProactiveSuppliers{" +
                ",\n activationHolder=" + activationHolder +
                '}';
    }
}
