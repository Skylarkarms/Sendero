package sendero;


import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface ColdReceptor<T> extends InputMethod<T> {

    /**Base Cold Stream receptive function*/
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
