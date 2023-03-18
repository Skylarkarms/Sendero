package sendero;

import sendero.functions.Functions;
import sendero.interfaces.In;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

class InImpl<T> implements In<T> {
    private final Updater<T> updater;
    private final AtomicReference<Runnable> defaulter = new AtomicReference<>(Functions.emptyRunnable());

    static<Fun, T> Fun defaulterCoupler(
            AtomicReference<Runnable> runnableFun,
            Updater<T> updater,
            Function<Consumer<T>, Fun> mediatorFun
    ) {
        final Consumer<T> nextConsumer = t -> runnableFun.set(
                () -> updater.update(0, t1 -> t)
        );
        return mediatorFun.apply(nextConsumer);
    }

    static<Fun, T> Pair.Immutables<AtomicReference<Runnable>, Fun> transientSourceFactory(
            Function<AtomicReference<Runnable>, Fun> mediatorSupplier
    ) {
        AtomicReference<Runnable> init = new AtomicReference<>(Functions.emptyRunnable());
        final Fun mediator = mediatorSupplier.apply(init);
        return new Pair.Immutables<>(init, mediator);
    }

    public InImpl(Holders.BaseBroadcaster<T> broadcaster) {
        updater = Inputs.getUpdater(broadcaster);
    }

    @Override
    public void setDefault() {
        defaulter.get().run();
    }

    private void setDefaulter(Runnable newDefaulter) {
        defaulter.set(newDefaulter);
    }

    void onSwapped(SourceType type, T next) {
        if (type == SourceType.stream) setDefaulter(
                () -> updateAndGet(currentPrev -> next)
        );
    }

    void clearDefault() {
        setDefaulter(Functions.emptyRunnable());
    }

    @Override
    public T updateAndGet(UnaryOperator<T> update) {
        return updater.updateAndGet(update);
    }

    @Override
    public T getAndUpdate(UnaryOperator<T> update) {
        return updater.getAndUpdate(update);
    }

    @Override
    public void update(long delay, UnaryOperator<T> update) {
        updater.update(delay, update);
    }
}
