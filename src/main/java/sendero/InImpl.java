package sendero;

import sendero.functions.Functions;
import sendero.interfaces.In;
import sendero.interfaces.Updater;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

class InImpl<T> implements In<T> {
    private final Updater<T> updater;
    private final Supplier<Runnable> defaulter;

    /**Prepares the initialization of the defaulter to overcome "THIS"*/
    @SuppressWarnings("unchecked")
    static<S, T, Ext extends In<T>> Ext factory(
            BiFunction<T, S, T> update,
            BiFunction<BiFunction<T, S, T>,Supplier<Runnable>, Ext> constructorFun
    ) {
        final Runnable[] defaulter = new Runnable[]{
                Functions.emptyRunnable()
        };
        final Updater<T>[] res = (Updater<T>[]) new Updater[1];
        final BiFunction<T, S, T> mediator = (t, s) -> {
            T next = update.apply(t, s);
            defaulter[0] = () -> res[0].update(0, t1 -> next);
            return next;
        };
        final Ext reif = constructorFun.apply(mediator, () -> defaulter[0]);
        res[0] = reif;
        return reif;
    }

    public InImpl(Holders.BaseBroadcaster<T> broadcaster, Supplier<Runnable> defaulter) {
        updater = Inputs.getUpdater(broadcaster);
        this.defaulter = defaulter;
    }

    @Override
    public void setDefault() {
        defaulter.get().run();
    }

    @Override
    public T updateAndGet(UnaryOperator<T> update) {
        return updater.updateAndGet(update);
    }

    @Override
    public void update(long delay, UnaryOperator<T> update) {
        updater.update(delay, update);
    }
}
