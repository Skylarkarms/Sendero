package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class Link<T> extends Path<T> implements BaseLink {

    private <S> Link(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    private Link(Builders.HolderBuilder<T> holderBuilder, boolean mutableManager) {
        super(holderBuilder, Builders.getManagerBuild().withMutable(mutableManager));
    }

    private <S> Link(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
        super(holderBuilder, basePath, map);
    }

    private Link(boolean activationListener) {
        super(activationListener);
    }

    @Override
    public boolean isBound() {
        return activationListenerIsSet();
    }

    @Override
    public boolean unbound() {
        throw new IllegalStateException("Must be overridden by its children");
    }

    public static class Unbound<T> extends Link<T> implements UnboundLink<T> {

        final ActivePathListener<T> activePathListener;

        public Unbound() {
            super(true);
            activePathListener = new ActivePathListener<>(manager, holderAppointer);

        }

        public Unbound(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder, true);
            activePathListener = new ActivePathListener<>(manager, holderAppointer);
        }

        private final Supplier<IllegalAccessException> getExc = () -> new IllegalAccessException(
                "Link.Unbound.class is unable to listen paths. \n " +
                "Attempting to integrate both listen and bind would greatly diminish performance on both ends.");

        @Override
        protected <P extends BasePath<T>> T setAndStart(P basePath) {
            throwException();
            return null;
        }

        private void throwException() {
            try {
                throw getExc.get();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
            throwException();
            return null;
        }

        @Override
        public <P extends BasePath<T>> void bind(P basePath) {
            activePathListener.bind(basePath);
        }

        @Override
        public <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
            activePathListener.bindMap(basePath, map);
        }

        @Override
        public boolean unbound() {
            return activePathListener.unbound();
        }

        interface UnboundSwitch<T> {
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

            private final AbsLink<T> absLink = new AbsLink<T>(activePathListener) {
                @Override
                protected void onResult(Pair.Immutables.Int<T> tPair) {
                    Switch.this.acceptVersionValue(tPair);
                }
            };

            public Switch() {
            }

            public Switch(Builders.HolderBuilder<T> holderBuilder) {
                super(holderBuilder);
            }

            @Override
            public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
                absLink.bindFun(path, exit);
            }

            @Override
            public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
                absLink.switchMap(path, switchMap);
            }

            @Override
            public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
                absLink.switchFun(path, exit);
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
            super(
                    Builders.getHolderBuild(UnaryOperator.identity()),
                    fixedPath,
                    map
            );
        }

        public static class In<T> extends Bound<T> {
            public<S> In(T initialValue, BasePath<S> fixedPath, BiFunction<T, S, T> update, Predicate<T> expectOut) {
                super(initialValue, fixedPath, update, expectOut);
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
