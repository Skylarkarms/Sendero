package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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

        private <S> Unbound(UnaryOperator<Builders.HolderBuilder<T>> builderOperator, BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
            super(builderOperator, basePath, updateFun);
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

        public static class In<T> extends Unbound<T> implements sendero.interfaces.In<T> {

            private final InImpl<T> impl;

            @Override
            public void setDefault() {
                impl.setDefault();
            }

            public static <S, T> SingleLink.Unbound.In<T> get(
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                return get(myIdentity(), fixedPath, update);
            }

            public static <S, T> SingleLink.Unbound.In<T> get(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                return (SingleLink.Unbound.In<T>) InImpl.factory(
                        update,
                        (BiFunction<BiFunction<T, S, T>, Supplier<Runnable>, sendero.interfaces.In<T>>)
                                (tstBiFunction, runnableSupplier) ->
                                        new SingleLink.Unbound.In<>(operator, fixedPath, tstBiFunction, runnableSupplier)
                );
            }

            private <S> In(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update,
                    Supplier<Runnable> defaulter
            ) {
                super(operator, fixedPath, update);
                impl = new InImpl<>(this, defaulter);
            }

            @Override
            public T updateAndGet(UnaryOperator<T> update) {
                return impl.updateAndGet(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                impl.update(delay, update);
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

        public Bound(
                BasePath<T> fixedPath
        ) {
            this(
                    myIdentity(),
                    fixedPath,
                    myIdentity()
            );
        }

        public Bound(
                UnaryOperator<Builders.HolderBuilder<T>> builder,
                BasePath<T> fixedPath
        ) {
            this(
                    builder,
                    fixedPath,
                    myIdentity()
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

        public static class In<T> extends Bound<T> implements sendero.interfaces.In<T> {

            private final InImpl<T> impl;

            @Override
            public void setDefault() {
                impl.setDefault();
            }

            public static <S, T> In<T> get(
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                return get(myIdentity(), fixedPath, update);
            }

            public static <S, T> In<T> get(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                return (In<T>) InImpl.factory(
                        update,
                        (BiFunction<BiFunction<T, S, T>, Supplier<Runnable>, sendero.interfaces.In<T>>)
                                (tstBiFunction, runnableSupplier) ->
                                        new In<>(operator, fixedPath, tstBiFunction, runnableSupplier)
                );
            }

            private <S> In(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update,
                    Supplier<Runnable> defaulter
            ) {
                super(operator, fixedPath, update);
                impl = new InImpl<>(this, defaulter);
            }

            @Override
            public T updateAndGet(UnaryOperator<T> update) {
                return impl.updateAndGet(update);
            }

            @Override
            public void update(long delay, UnaryOperator<T> update) {
                impl.update(delay, update);
            }
        }
    }
}

