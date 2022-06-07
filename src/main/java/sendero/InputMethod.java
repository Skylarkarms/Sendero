package sendero;


import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface InputMethod<T> extends Holders.ColdConsumer<T> {
    <S> void accept(Immutable<S> immutable, Function<S, T> map);
    <S> void accept(Immutable<S> immutable, BiFunction<T, S, T> update);

    @FunctionalInterface
    interface Type<M, T> {
        void acceptorMethod(InputMethod<M> method, Immutable<T> param);
        Type<?, ?> identity = Consumer::accept;
        default boolean isIdentity() {
            return this == identity;
        }
        @SuppressWarnings("unchecked")
        static <S> Type<S, S> identity() {
            return (Type<S, S>) identity;
        }
        static<S, R> Type<S, R> map(Function<R, S> map) {
            return (method, param) -> method.accept(param, map);
        }
        static <S, R> Type<S, R> update(BiFunction<S, R, S> update) {
            return (method, param) -> method.accept(param, update);
        }
    }
}
