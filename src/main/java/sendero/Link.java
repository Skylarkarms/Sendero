package sendero;

import sendero.functions.Functions;
import sendero.interfaces.Updater;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public class Link<T> extends Path<T> implements BaseLink {

    private <S> Link(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map) {
        super(builderOperator, basePath, map);
    }

    private Link(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        super(builderOperator,
                Builders.ManagerBuilder.mutable()
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
    protected void setOnStateChangeListener(AtomicBinaryEvent listener) {
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

        public static final class In<T> extends Unbound<T> implements Updater<T> {
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

        public static final class In<T> extends Bound<T> implements Updater<T> {
            private final Updater<T> updater = Inputs.getUpdater(this);
            private final Runnable defaulter;

            public void setDefault() {
                defaulter.run();
            }
            public static <S, T> In<T> get(
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                return get(myIdentity(), fixedPath, update);
            }

            @SuppressWarnings("unchecked")
            public static <S, T> In<T> get(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update
            ) {
                final Runnable[] defaulter = new Runnable[]{
                    Functions.emptyRunnable()
                };
                final Updater<T>[] res = (Updater<T>[]) new Updater[1];
                final BiFunction<T, S, T> mediator = (t, s) -> {
                    T next = update.apply(t, s);
                    defaulter[0] = () -> res[0].update(0, t1 -> next);
                    return next;
                };
                final In<T> reif = new In<T>(operator, fixedPath, mediator, defaulter[0]);
                res[0] = reif;
                return reif;
            }

            private <S> In(
                    UnaryOperator<Builders.HolderBuilder<T>> operator,
                    BasePath<S> fixedPath,
                    BiFunction<T, S, T> update,
                    Runnable defaulter
            ) {
                super(operator, fixedPath, update);
                this.defaulter = defaulter;
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
