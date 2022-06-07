package sendero;

import java.util.function.BiFunction;

interface BaseMerge<T> {
    <S> Merge<T> from(
            BasePath<S> path,
            BiFunction<T, S, T> update
    );
    <S> boolean drop(
            BasePath<S> path
    );
}
