package sendero;

import sendero.functions.Consumers;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

interface Forkable<T> {
    <S, O extends Gate.Out<S>> O out(Class<? super O> outputType, Function<T, S> map);

    default <O extends Gate.Out<T>> O out(Class<? super O> outputType) {
        return out(outputType, myIdentity());
    }
    <S> Forkable<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map);
    default <S> Forkable<S> forkMap(Function<T, S> map) {
        return forkMap(myIdentity(), map);
//        return forkMap(UnaryOperator.identity(), map);
    }

    /**For when additional rules are required, including an INITIAL value for S*/
    <S> Forkable<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update);

    /**This update function does not require an INITIAL value, since a null check will be required only once anyway,
     *
     * If an INITIAL value is desired,
     * use the forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> operator, BiFunction<S, T, S> update) function instead*/
    default <S> Forkable<S> forkUpdate(BiFunction<S, T, S> update) {
        return forkUpdate(myIdentity(), update);
//        return forkUpdate(UnaryOperator.identity(), update);
    }
    <S> Forkable<S> forkFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit);
    default  <S> Forkable<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return forkFun(myIdentity(), exit);
//        return forkFun(UnaryOperator.identity(), exit);
    }
    <S> Forkable<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap);
    default <S> Forkable<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return forkSwitch(myIdentity(), switchMap);
//        return forkSwitch(UnaryOperator.identity(), switchMap);
    }
    <S> Forkable<S> forkSwitchFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate);
    default <S> Forkable<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return forkSwitchFun(myIdentity(), mutate);
//        return forkSwitchFun(UnaryOperator.identity(), mutate);
    }
}