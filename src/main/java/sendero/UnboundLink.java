package sendero;

import java.util.function.Function;

interface UnboundLink<T> extends InputMethodBinder<T>{
    <S> void switchMap(
            BasePath<S> path,
            Function<S, ? extends BasePath<T>> switchMap
    );
}
