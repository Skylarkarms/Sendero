package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BinaryPredicate;

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
    default <S> Forkable<S> forkMap(BinaryPredicate<S> constraintIn, Function<T, S> map) {
        return forkMap(
                Builders.constraintIn(constraintIn),
                map
        );
    }
    default <S> Forkable<S> forkMap(Function<T, S> map) {
        return forkMap(myIdentity(), map);
    }

    /**For when additional rules are required, including an INITIAL value for S*/
    <S> Forkable<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update);
    default  <S> Forkable<S> forkUpdate(BinaryPredicate<S> constraintIn, BiFunction<S, T, S> update) {
        return forkUpdate(
               Builders.constraintIn(constraintIn),
               update
        );
    }

    /**This update function does not require an INITIAL value, since a null check will be required only once anyway,
     *
     * If an INITIAL value is desired,
     * use the forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> operator, BiFunction<S, T, S> update) function instead*/
    default <S> Forkable<S> forkUpdate(BiFunction<S, T, S> update) {
        return forkUpdate(myIdentity(), update);
    }
    <S> Forkable<S> forkFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit);
    default  <S> Forkable<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return forkFun(myIdentity(), exit);
    }
    <S> Forkable<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap);
    default <S> Forkable<S> forkSwitch(BinaryPredicate<S> constraintIn, Function<T, BasePath<S>> switchMap) {
        return forkSwitch(
                Builders.constraintIn(constraintIn),
                switchMap
        );
    }
    default <S> Forkable<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return forkSwitch(myIdentity(), switchMap);
    }
    <S> Forkable<S> forkSwitchFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate);
    default <S> Forkable<S> forkSwitchFun(BinaryPredicate<S> constraintIn, Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return forkSwitchFun(
                Builders.constraintIn(constraintIn),
                mutate
        );
    }
    default <S> Forkable<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return forkSwitchFun(myIdentity(), mutate);
    }
}