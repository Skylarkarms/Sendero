package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class Link<T> extends Path<T> implements BaseLink {

    private <S> Link(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<Updater<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
        super(holderBuilder, basePath, toAppointFun);
    }

    private Link(Builders.HolderBuilder<T> holderBuilder, boolean mutableManager) {
        super(holderBuilder, Builders.getManagerBuild().withMutable(mutableManager));
    }

    private<S> Link(Builders.HolderBuilder<T> holderBuilder, Function<Holders.ColdHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun, BasePath<S> basePath) {
        super(holderBuilder, toAppointFun, basePath);
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
            activePathListener = new ActivePathListener<T>(manager, holderAppointer);

        }

        public Unbound(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder, true);
            activePathListener = new ActivePathListener<T>(manager, holderAppointer);
        }

        private final Supplier<IllegalAccessException> getExc = () -> new IllegalAccessException(
                "Link.Unbound.class is unable to listen paths. \n " +
                "Attempting to integrate both listen and bind would greatly diminish performance on both ends.");

        @Override
        protected <P extends BasePath<T>> void listen(P basePath) {
            throwException();
        }

        private void throwException() {
            try {
                throw getExc.get();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected <S, P extends BasePath<S>> void listen(P basePath, Function<S, T> map) {
            throwException();
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

        static <T, S> Consumer<Pair.Immutables.Int<S>> appointUpdateCreator(
                AtomicInteger version,
                Updater<T> intConsumer,
                BiFunction<S, T, T> update
        ) {
            return sInt -> {
                final int nextVer = sInt.getInt();
                final S s = sInt.getValue();
                int prevVer = version.get();
                for (boolean lesser;;) {
                    lesser = prevVer < nextVer;
                    if (lesser && version.compareAndSet(prevVer, nextVer)) {
                        intConsumer.update(
                                t -> update.apply(s, t)
                        );
                        break;
                    }
                    if (prevVer >= (prevVer = version.get())) break;
                }
            };
        }

        protected static <T, S> Consumer<Pair.Immutables.Int<S>> appointAcceptCreator(
                Consumer<Pair.Immutables.Int<T>> intConsumer,
                Function<S, T> map
        ) {
            return sInt -> {
                final int nextVer = sInt.getInt();
                final S s = sInt.getValue();
                intConsumer.accept(
                        new Pair.Immutables.Int<>(nextVer, map.apply(s))
                );
            };
        }

        protected  <S> Bound(
                T initialValue,
                BasePath<S> fixedPath,
                BiFunction<S, T, T> update,
                Predicate<T> expectOut
        ) {
            super(
                    Builders.getHolderBuild(sBuilder -> sBuilder.withInitial(initialValue).expectOut(expectOut)),
                    fixedPath,
                    new Function<Updater<T>, Consumer<Pair.Immutables.Int<S>>>() {
//                    new Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>>() {
                        private final AtomicInteger version = new AtomicInteger();
                        @Override
                        public Consumer<Pair.Immutables.Int<S>> apply(Updater<T> tDispatcherHolder) {
//                        public Consumer<Pair.Immutables.Int<S>> apply(Holders.DispatcherHolder<T> tDispatcherHolder) {
                            return appointUpdateCreator(version, tDispatcherHolder, update);
                        }
                    }
            );
        }

        public<S> Bound(
                UnaryOperator<Builders.HolderBuilder<T>> dispatcherBuilder,
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            super(
                    Builders.getHolderBuild(dispatcherBuilder),
                    tDispatcherHolder -> appointAcceptCreator(tDispatcherHolder::acceptVersionValue, map),
                    fixedPath
            );
        }

        public<S> Bound(
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            super(
                    Builders.getHolderBuild(UnaryOperator.identity()),
                    tDispatcherHolder -> appointAcceptCreator(tDispatcherHolder::acceptVersionValue, map),
                    fixedPath
            );
        }

        public static class In<T> extends Bound<T> {
            public<S> In(T initialValue, BasePath<S> fixedPath, BiFunction<S, T, T> update, Predicate<T> expectOut) {
                super(initialValue, fixedPath, update, expectOut);
            }

            @Override
            public void update(UnaryOperator<T> update) {
                super.update(update);
            }
        }
    }

    public static class SingleLink<T> extends BasePath.Injective<T> {


        protected <S> SingleLink(Supplier<BasePath<S>> basePathSupplier, Function<Consumer<Pair.Immutables.Int<T>>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
            super(basePathSupplier, toAppointFun);
        }

        <S> SingleLink(Builders.HolderBuilder<T> holderBuilder, Supplier<BasePath<S>> basePathSupplier, Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
            super(holderBuilder, basePathSupplier, toAppointFun);
        }

        static class Bound<T> extends SingleLink<T> {

            protected  <S> Bound(
                    T initialValue,
                    BasePath<S> fixedPath,
                    BiFunction<S, T, T> update,
                    Predicate<T> expectOut
            ) {
                super(
                        Builders.getHolderBuild(sBuilder -> sBuilder.withInitial(initialValue).expectOut(expectOut)),
                        () -> fixedPath,
                        new Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>>() {
                            private final AtomicInteger version = new AtomicInteger();
                            @Override
                            public Consumer<Pair.Immutables.Int<S>> apply(Holders.DispatcherHolder<T> tDispatcherHolder) {
                                return Link.Bound.appointUpdateCreator(version, tDispatcherHolder, update);
                            }
                        }
                );
            }

            <S> Bound(
                    BasePath<S> fixedPath,
                    Function<S, T> map
            ) {
                super(
                        () -> fixedPath,
                        tDispatcherHolder -> Link.Bound.appointAcceptCreator(tDispatcherHolder, map)
                );
            }


        }
    }
}
