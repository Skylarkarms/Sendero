package sendero;

import sendero.functions.Consumers;

import java.util.function.Consumer;
import java.util.function.Function;

interface Forkable<T> {
    <O extends Gates.Out<T>> O out(Class<? super O> outputType);
    <S> Forkable<S> forkMap(Function<T, S> map);
    <S> Forkable<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit);
    <S> Forkable<S> forkSwitch(Function<T, BasePath<S>> switchMap);
    <S> Forkable<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate);
}