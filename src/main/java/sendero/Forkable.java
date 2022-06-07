package sendero;

import sendero.interfaces.BinaryPredicate;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

interface Forkable<T> {
    <S, O extends Gate.Out<S>> O out(Class<? super O> outputType, Function<T, S> map);

    default <O extends Gate.Out<T>> O out(Class<? super O> outputType) {
        return out(outputType, myIdentity());
    }


    <S> Forkable<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map);
//    <S> Forkable<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map);
    default <S> Forkable<S> forkMap(BinaryPredicate<S> excludeIn, Function<T, S> map) {
        return forkMap(
                Builders.excludeIn(excludeIn),
                map
        );
    }
    default <S> Forkable<S> forkMap(Function<T, S> map) {
        return forkMap(myIdentity(), map);
    }

    /**For when additional rules are required, including an INITIAL value for S*/
    <S> Forkable<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update);
//    <S> Forkable<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update);
    default  <S> Forkable<S> forkUpdate(BinaryPredicate<S> excludeIn, BiFunction<S, T, S> update) {
        return forkUpdate(
               Builders.excludeIn(excludeIn),
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

    <S> Forkable<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap);
//    <S> Forkable<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap);
    default <S> Forkable<S> forkSwitch(BinaryPredicate<S> excludeIn, Function<T, BasePath<S>> switchMap) {
        return forkSwitch(
                Builders.excludeIn(excludeIn),
                switchMap
        );
    }
    default <S> Forkable<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return forkSwitch(myIdentity(), switchMap);
    }
}