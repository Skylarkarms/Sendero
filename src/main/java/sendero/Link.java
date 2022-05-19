package sendero;

import sendero.interfaces.Updater;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class Link<T> extends Path<T> implements BaseLink {

    private <S> Link(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map) {
        super(builderOperator, basePath, map);
    }

    private Link(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        super(builderOperator,
                Builders.mutabilityAllowed()
        );
    }

    private <S> Link(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(builderOperator, basePath, updateFun);
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

        public Unbound(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
            super(builderOperator);
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
        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
            baseUnbound.switchMap(path, switchMap);
        }

        @Override
        public boolean unbound() {
            return baseUnbound.unbound();
        }

        public static final class In<T> extends Unbound<T> implements Updater<T> {
            private final HolderInput.Updater<T> updater = new HolderInput.Updater<>(baseTestDispatcher);

            public In() {
                super();
            }

            public In(UnaryOperator<Builders.HolderBuilder<T>> operator) {
                super(operator);
            }

            @Override
            public void update(UnaryOperator<T> update) {
                updater.update(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                updater.update(delay, update);
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
                UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
                BasePath<S> fixedPath,
                BiFunction<T, S, T> update
        ) {
            super(
                    builderOperator,
                    fixedPath,
                    update
            );
        }

        public<S> Bound(
                UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            super(
                    builderOperator,
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

        public static final class In<T> extends Bound<T> implements Updater<T> {
            private final HolderInput.Updater<T> updater = new HolderInput.Updater<>(baseTestDispatcher);
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
                updater.update(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                updater.update(delay, update);
            }
        }
    }
}
