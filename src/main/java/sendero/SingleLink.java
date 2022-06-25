package sendero;

import sendero.interfaces.Updater;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public class SingleLink<T> extends SinglePath<T> implements BaseLink{

    private SingleLink() {
        this(myIdentity(), true);
    }

    public SingleLink(UnaryOperator<Builders.HolderBuilder<T>> builderOperator, boolean mutableManager) {
        super(builderOperator,
                Builders.ManagerBuilder.isMutable(mutableManager)
        );
    }

    private <S> SingleLink(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map) {
        super(builderOperator, basePath, map);
    }

    private <S> SingleLink(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> update) {
        super(builderOperator, basePath, update);
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
    protected void setOnStateChangeListener(AtomicBinaryEvent listener) {
        throwIllegalAccess("setOnStateChangeListener");
    }

    @Override
    protected boolean clearOnStateChangeListener() {
        throwIllegalAccess("clearOnStateChangeListener");
        return false;
    }

    public static class Unbound<T> extends SingleLink<T> implements UnboundLink<T> {

        final BaseUnbound<T> baseUnbound = new BaseUnbound<>(this);

        public Unbound() {
            super();
        }

        public Unbound(UnaryOperator<Builders.HolderBuilder<T>> operator) {
            super(operator, true);
        }

        @Override
        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
            baseUnbound.switchMap(path, switchMap);
        }

        @Override
        public boolean unbound() {
            return baseUnbound.unbound();
        }

        @Override
        public <S, P extends BasePath<S>> Void bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
            return baseUnbound.bind(basePath, inputMethod);
        }

        public static class In<T> extends Unbound<T> implements Updater<T> {
            private final Updater<T> updater = Inputs.getUpdater(this);

            public In() {
                super();
            }

            public In(UnaryOperator<Builders.HolderBuilder<T>> operator) {
                super(operator);
            }

            @Override
            public T updateAndGet(UnaryOperator<T> update) {
                return updater.updateAndGet(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                updater.update(delay, update);
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
                BasePath<S> fixedPath,
                BiFunction<T, S, T> update
        ) {
            this(
                    myIdentity(),
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

        public <S> Bound(
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            this(
                    myIdentity(),
                    fixedPath,
                    map
            );
        }

        public <S> Bound(
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

        public static class In<T> extends Bound<T> implements Updater<T> {

            private final Updater<T> updater = Inputs.getUpdater(this);

            public  <S> In(
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                this(
                        myIdentity(),
                        fixedPath,
                        update
                );
            }

            public  <S> In(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                super(operator, fixedPath, update);
            }

            public <S> In(BasePath<S> fixedPath, Function<S, T> map) {
                this(myIdentity(), fixedPath, map);
            }

            public <S> In(UnaryOperator<Builders.HolderBuilder<T>> operator, BasePath<S> fixedPath, Function<S, T> map) {
                super(operator, fixedPath, map);
            }

            @Override
            public T updateAndGet(UnaryOperator<T> update) {
                return updater.updateAndGet(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                updater.update(delay, update);
            }
        }
    }
}

