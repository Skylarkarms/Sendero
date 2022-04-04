package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class SingleLink<T> extends SinglePath<T> implements BaseLink{

    private <S> SingleLink(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    private <S> SingleLink(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
        super(holderBuilder, basePath, map);
    }

    @Override
    public boolean isBound() {
        return activationListenerIsSet();
    }

    @Override
    public boolean unbound() {
        throw new IllegalStateException("Must be overridden by its children");
    }

    public static class Bound<T> extends SingleLink<T> {

        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean unbound() {
            throw new IllegalStateException("Not allowed!!");
        }

        public <S> Bound(
                T initialValue,
                BasePath<S> fixedPath,
                BiFunction<T, S, T> update,
                Predicate<T> expectOut
        ) {
            super(
                    Builders.getHolderBuild(sBuilder -> sBuilder.withInitial(initialValue).expectOut(expectOut)),
                    fixedPath,
                    update
            );
        }

        public <S> Bound(
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            super(
                    Builders.getHolderBuild(UnaryOperator.identity()),
                    fixedPath,
                    map
            );
        }

        public static class In<T> extends Bound<T> {

            public  <S> In(T initialValue, BasePath<S> fixedPath, BiFunction<T, S, T> update, Predicate<T> expectOut) {
                super(initialValue, fixedPath, update, expectOut);
            }

            public <S> In(BasePath<S> fixedPath, Function<S, T> map) {
                super(fixedPath, map);
            }

            @Override
            public void update(UnaryOperator<T> update) {
                super.update(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                super.update(delay, update);
            }
        }


    }


}

