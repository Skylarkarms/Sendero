package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

interface UnboundLink<T> extends InputMethodBinder<T>{
    default  <P extends BasePath<T>> void bind(P basePath) {
        bind(basePath, Builders.InputMethods.identity());
    }
    default  <S, P extends BasePath<S>> void bind(P basePath, Function<S, T> map) {
        bind(basePath, Builders.InputMethods.map(map));
    }
    default  <S, P extends BasePath<S>> void bind(P basePath, BiFunction<T, S, T> update) {
        bind(basePath, Builders.InputMethods.update(update));
    }
    <S> void switchMap(
            BasePath<S> path,
            Function<S, ? extends BasePath<T>> switchMap
    );
}
