package sendero;


import sendero.functions.Functions;
import sendero.interfaces.SynthEqual;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

interface InputMethod<T> extends Holders.ColdConsumer<T> {
    <S> void accept(Immutable<S> immutable, Function<S, T> map);
    <S> void accept(Immutable<S> immutable, BiFunction<T, S, T> update);

    /**Where S is the parameter from incoming source
     * since a single parameter defines the function, equality can be checked directly ==
     * as the synthetic factory will reuse the instance when the same function is passed.*/
    @FunctionalInterface
    interface Type<M, S> extends SynthEqual {
        void acceptorMethod(InputMethod<M> method, Immutable<S> param);
        Type<?, ?> identity = Consumer::accept;
        default boolean isIdentity() {
            return this == identity;
        }
        @SuppressWarnings("unchecked")
        static <S> Type<S, S> identity() {
            return (Type<S, S>) identity;
        }
        @SuppressWarnings("unchecked")
        static<S, R> Type<S, R> map(Function<R, S> map) {
            if (Functions.isIdentity(map)) return (Type<S, R>) identity;
            return (method, param) -> method.accept(param, map);
        }
        static <S, R> Type<S, R> update(BiFunction<S, R, S> update) {
            return (method, param) -> method.accept(param, update);
        }
    }
}
