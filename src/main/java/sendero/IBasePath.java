package sendero;

import sendero.pairs.Pair;

import java.util.function.Consumer;

public interface IBasePath<T> {
    void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);
    void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t);
    void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer);
}
