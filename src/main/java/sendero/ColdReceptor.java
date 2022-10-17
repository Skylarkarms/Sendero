package sendero;


import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**TopData UnaryOperator applies AFTER value comparison test has been validated. see: ColdReceptorManager.class*/
@FunctionalInterface
public interface ColdReceptor<T> extends InputMethod<T> {
    /**Base Cold Stream receptive function<P>
     * If contention misses immediately retries.<P>
     * If Serial values are proved to be lesser than current, then it drops the thread <P>
     * and the spin lock is released.*/
    void filterAccept(Immutable.Values topValues, UnaryOperator<T> topData);

    @Override
    default void accept(Immutable<T> immutable) {
        final T current = immutable.get();
        filterAccept(immutable.local, prev -> current);
    }

    @Override
    default<S> void accept(Immutable<S> immutable, Function<S, T> map) {
        final S current = immutable.get();
        filterAccept(immutable.local, prev -> map.apply(current)
        );
    }

    @Override
    default<S> void accept(Immutable<S> immutable, BiFunction<T, S, T> update) {
        final S current = immutable.get();
        filterAccept(immutable.local, t -> update.apply(t, current));
    }
}