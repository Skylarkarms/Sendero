package sendero;

import sendero.functions.Consumers;

import java.util.function.Consumer;
import java.util.function.Function;

interface UnboundSwitch<T> {
    <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit);
    <S> void switchMap(
            BasePath<S> path,
            Function<S, ? extends BasePath<T>> switchMap
    );
    <S> void switchFun(
            BasePath<S> path,
            Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit
    );
}
