package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BinaryPredicate;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

interface Forkable<T> {
    <O extends Gate.Out<T>> O out(Class<? super O> outputType);
    <S, O extends Gate.Out<S>> O out(Class<? super O> outputType, Function<T, S> map);
    <S> Forkable<S> forkMap(Function<T, S> map);
    /**This update function does not require an INITIAL value, since a null check will be required only once anyway,
     *
     * If an INITIAL value is desired,
     * use the forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> operator, BiFunction<S, T, S> update) function instead*/
    <S> Forkable<S> forkUpdate(BiFunction<S, T, S> update);

    /**For when additional rules are required, including an INITIAL value for S*/
    <S> Forkable<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> operator, BiFunction<S, T, S> update);
    <S> Forkable<S> forkMap(BinaryPredicate<S> expectIn, Function<T, S> map);
    <S> Forkable<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit);
    <S> Forkable<S> forkSwitch(Function<T, BasePath<S>> switchMap);
    <S> Forkable<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate);
}