package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

interface UnboundLink<T> {
    <P extends BasePath<T>> void bind(P basePath);
    <S, P extends BasePath<S>>void bindMap(P basePath, Function<S, T> map);
    <S, P extends BasePath<S>>void bindUpdate(P basePath, BiFunction<T, S, T> update);
}
