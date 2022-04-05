package sendero;

import sendero.functions.Consumers;

import java.util.function.*;

public class SingleLink<T> extends SinglePath<T> implements BaseLink{

    private SingleLink(boolean mutableActivationListener) {
        super(mutableActivationListener);
    }

    public SingleLink(Builders.HolderBuilder<T> holderBuilder, boolean mutableManager) {
        super(holderBuilder, Builders.getManagerBuild().withMutable(mutableManager));
    }

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

    public static class Unbound<T> extends SingleLink<T> implements UnboundLink<T> {

        final BaseUnbound<T> baseUnbound = new BaseUnbound<>(this);


        public Unbound() {
            super(true);
        }

        public Unbound(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder, true);
        }

        @Override
        public <P extends BasePath<T>> void bind(P basePath) {
            baseUnbound.bind(basePath);
        }

        @Override
        public <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
            baseUnbound.bindMap(basePath, map);
        }

        @Override
        public boolean unbound() {
            return baseUnbound.unbound();
        }

        public static class Switch<T> extends Unbound<T> implements UnboundSwitch<T> {

            private final BaseUnboundSwitch<T> baseUnboundSwitch = new BaseUnboundSwitch<T>(baseUnbound.activePathListener);

            public Switch() {
            }

            public Switch(Builders.HolderBuilder<T> holderBuilder) {
                super(holderBuilder);
            }

            @Override
            public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
                baseUnboundSwitch.bindFun(path, exit);
            }

            @Override
            public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
                baseUnboundSwitch.switchMap(path, switchMap);
            }

            @Override
            public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
                baseUnboundSwitch.switchFun(path, exit);
            }
        }
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

    private final BaseUnbound.LinkIllegalAccessException exception = new BaseUnbound.LinkIllegalAccessException(getClass());

    @Override
    protected <S, P extends BasePath<S>> void setPath(P basePath, Function<S, T> map) {
        exception.throwE();
    }

    @Override
    protected <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
        exception.throwE();
        return null;
    }

    @Override
    protected <P extends BasePath<T>> T setAndStart(P basePath) {
        exception.throwE();
        return null;
    }

    @Override
    protected void stopListeningPathAndUnregister() {
        exception.throwE();
    }

    @Override
    protected boolean startListeningPath() {
        exception.throwE();
        return false;
    }

    @Override
    protected boolean stopListeningPath() {
        exception.throwE();
        return false;
    }

    @Override
    protected boolean pathIsActive() {
        exception.throwE();
        return false;
    }

    @Override
    protected boolean pathIsSet() {
        exception.throwE();
        return false;
    }

}
