package sendero;


import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@FunctionalInterface
interface ColdReceptor<T> extends InputMethod<T> {

    /**Base Cold Stream receptive function<P>
     * If contention misses immediately retries.<P>
     * If Serial values are proved to be lesser than current, then it drops the thread <P>
     * and the spin lock is released.*/
    void filterAccept(Immutable.Values topValues, UnaryOperator<T> topData);

    @Override
    default void accept(Immutable<T> immutable) {
        filterAccept(immutable.local, prev -> immutable.get());
    }

    @Override
    default<S> void accept(Immutable<S> immutable, Function<S, T> map) {
        T mapped = map.apply(immutable.get());
        filterAccept(immutable.local, prev -> mapped);
    }

    @Override
    default<S> void accept(Immutable<S> immutable, BiFunction<T, S, T> update) {
        filterAccept(immutable.local, t -> update.apply(t, immutable.get()));
    }
}
