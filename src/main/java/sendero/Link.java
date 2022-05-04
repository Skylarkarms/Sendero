package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.Updater;

import java.util.function.*;

public class Link<T> extends Path<T> implements BaseLink {

    private <S> Link(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    private Link(Builders.HolderBuilder<T> holderBuilder) {
        super(holderBuilder, Builders.getManagerBuild().withMutable(true));
    }

    private <S> Link(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(holderBuilder, basePath, updateFun);
    }

    private Link() {
        super(true);
    }

    @Override
    public boolean isBound() {
        return activationListenerIsSet();
    }

    @Override
    public boolean unbound() {
        throw new IllegalStateException("Must be overridden by its children");
    }

    private void throwIllegalAccess(String method) {
        try {
            throw new IllegalAccessException(method + " access not allowed from " + this.getClass());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void setOnStateChangeListener(AtomicBinaryEventConsumer listener) {
        throwIllegalAccess("setOnStateChangeListener");
    }

    @Override
    protected boolean clearOnStateChangeListener() {
        throwIllegalAccess("clearOnStateChangeListener");
        return false;
    }

    public static class Unbound<T> extends Link<T> implements UnboundLink<T> {

        final BaseUnbound<T> baseUnbound = new BaseUnbound<>(this);

        public Unbound() {
            super();
        }

        public Unbound(UnaryOperator<Builders.HolderBuilder<T>> operator) {
            super(Builders.getHolderBuild(operator));
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
        public <S, P extends BasePath<S>> void bindUpdate(P basePath, BiFunction<T, S, T> update) {
            baseUnbound.bindUpdate(basePath, update);
        }

        @Override
        public boolean unbound() {
            return baseUnbound.unbound();
        }

        public static class Switch<T> extends Unbound<T> implements UnboundSwitch<T> {

            private final BaseUnboundSwitch<T> baseSwitch = new BaseUnboundSwitch<>(baseUnbound.activePathListener);

            public Switch() {
            }

            public Switch(UnaryOperator<Builders.HolderBuilder<T>> operator) {
                super(operator);
            }

            @Override
            public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
                baseSwitch.bindFun(path, exit);
            }

            @Override
            public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
                baseSwitch.switchMap(path, switchMap);
            }

            @Override
            public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
                baseSwitch.switchFun(path, exit);
            }
        }

        public static class In<T> extends Unbound<T> implements Updater<T> {
            public In() {
            }

            public In(UnaryOperator<Builders.HolderBuilder<T>> operator) {
                super(operator);
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

    public static class Bound<T> extends Link<T> {

        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean unbound() {
            throw new IllegalStateException("Not allowed!!");
        }

        public <S> Bound(
                BasePath<S> fixedPath,
                BiFunction<T, S, T> update
        ) {
            this(
                    UnaryOperator.identity(),
                    fixedPath,
                    update
            );
        }

        public <S> Bound(
                UnaryOperator<Builders.HolderBuilder<T>> operator,
                BasePath<S> fixedPath,
                BiFunction<T, S, T> update
        ) {
            super(
                    Builders.getHolderBuild(operator),
                    fixedPath,
                    update
            );
        }

        public<S> Bound(
                UnaryOperator<Builders.HolderBuilder<T>> dispatcherBuilder,
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            super(
                    Builders.getHolderBuild(dispatcherBuilder),
                    fixedPath,
                    map
            );
        }

        public<S> Bound(
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            this(
                    UnaryOperator.identity(),
                    fixedPath,
                    map
            );
        }

        public static class In<T> extends Bound<T> implements Updater<T> {
            public<S> In(
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                this(UnaryOperator.identity(), fixedPath, update);
            }

            public<S> In(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                super(operator, fixedPath, update);
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
