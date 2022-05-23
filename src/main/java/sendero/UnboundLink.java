package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

import static sendero.functions.Functions.myIdentity;

interface UnboundLink<T> {
    <S, P extends BasePath<S>>void bindMap(P basePath, Function<S, T> map);
    default <P extends BasePath<T>> void bind(P basePath) {
        bindMap(basePath, myIdentity());
    }
    <S, P extends BasePath<S>>void bindUpdate(P basePath, BiFunction<T, S, T> update);
    <S> void switchMap(
            BasePath<S> path,
            Function<S, ? extends BasePath<T>> switchMap
    );
}
