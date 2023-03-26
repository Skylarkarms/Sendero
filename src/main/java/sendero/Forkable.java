package sendero;

import sendero.functions.Functions;
import sendero.interfaces.BinaryPredicate;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

interface Forkable<T> {
    /**Allowed classes are Gate.Out.Single, OR <p>
     * Gate.Out.Many*/
    <S, O extends Gate.Out<S>> O out(Class<? super O> outputType, Function<T, S> map);

    /**Allowed classes are Gate.Out.Single, OR <p>
     * Gate.Out.Many*/
    default <O extends Gate.Out<T>> O out(Class<? super O> outputType) {
        return out(outputType, myIdentity());
    }


    <S> Forkable<S> map(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map);

    default <S> Forkable<S> map(BinaryPredicate<S> excludeIn, Function<T, S> map) {
        return map(
                Builders.excludeIn(excludeIn),
                map
        );
    }
    default <S> Forkable<S> map(Function<T, S> map) {
        return map(myIdentity(), map);
    }

    /**Forks the data with a logic gate ruling option for data insertion exclusion <p>
     * @param excludeIn = logic gate for data insertion exclusion: <p>
     *    true = will NOT set new value <p>
     *    false = will SET new value
     *    */
    default Forkable<T> fork(BinaryPredicate<T> excludeIn) {
        return map(
                Builders.excludeIn(excludeIn),
                Functions.myIdentity()
        );
    }

    /**For when additional rules are required, including an INITIAL value for S*/
    <S> Forkable<S> update(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update);

    default  <S> Forkable<S> update(BinaryPredicate<S> excludeIn, BiFunction<S, T, S> update) {
        return update(
                Builders.excludeIn(excludeIn),
                update
        );
    }

    /**This update function does not require an INITIAL value, since a null check will be required only once anyway,<P>
     * If an INITIAL value is desired,
     * use the forkUpdate(UnaryOperator Builders.HolderBuilder operator, BiFunction update) function instead<P>
     * @param  update: <P>
     * first argument: previous value <P>
     * second argument: new incoming value <P>
     * third argument return new Value*/
    default <S> Forkable<S> update(BiFunction<S, T, S> update) {
        return update(myIdentity(), update);
    }

    <S> Forkable<S> switchMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap);

    default <S> Forkable<S> switchMap(BinaryPredicate<S> excludeIn, Function<T, BasePath<S>> switchMap) {
        return switchMap(
                Builders.excludeIn(excludeIn),
                switchMap
        );
    }

    default <S> Forkable<S> switchMap(Function<T, BasePath<S>> switchMap) {
        return switchMap(myIdentity(), switchMap);
    }
}