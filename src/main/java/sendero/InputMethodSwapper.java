package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
interface InputMethodSwapper<T, Next> {
    /**@return next AtomicBinaryEventConsumer
     *    null if:
     *    same parameters as before
     *    contention failed*/
    <S, P extends BasePath<S>> Next bind(P basePath, Builders.InputMethods<T, S> inputMethod);

    default <P extends BasePath<T>> Next bind(P basePath) {
        return bind(basePath, Builders.InputMethods.identity());
    }

    default <S, P extends BasePath<S>> Next bind(P basePath, Function<S, T> map) {
        return bind(basePath, Builders.InputMethods.map(map));
    }

    default <S, P extends BasePath<S>> Next bind(P basePath, BiFunction<T, S, T> update) {
        return bind(basePath, Builders.InputMethods.update(update));
    }
}
