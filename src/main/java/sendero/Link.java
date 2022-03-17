package sendero;

import sendero.functions.Consumers;
import sendero.switchers.Switchers;

import java.util.function.Consumer;
import java.util.function.Function;

class Link<T> extends Path<T> implements BaseLink {
    private Link(Builders.HolderBuilder<T> holderBuilder, boolean mutableManager) {
        super(holderBuilder, ActivationManager.getBuilder().withMutable(mutableManager));
    }

    private Link(boolean activationListener) {
        super(activationListener);
    }

    @Override
    public boolean isBound() {
        return super.isBound();
    }

    @Override
    public boolean unbound() {
        return super.unbond();
    }

    public static class Unbound<T> extends Link<T> implements UnboundLink<T> {

        public Unbound() {
            super(true);
        }

        public Unbound(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder, true);
        }

        @Override
        public <P extends BasePath<T>> void bind(P basePath) {
            super.bind(basePath);
        }

        @Override
        public <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
            super.bindMap(basePath, map);
        }

        interface UnboundSwitch<T> {
            //        void bind(BasePath<T> path);
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

        public static class Switch<T> extends Unbound<T> implements UnboundSwitch<T> {

            public Switch() {
            }

            public Switch(Builders.HolderBuilder<T> holderBuilder) {
                super(holderBuilder);
            }

            @Override
            public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {

            }

            @Override
            public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {

            }

            @Override
            public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {

            }
        }
    }
}
