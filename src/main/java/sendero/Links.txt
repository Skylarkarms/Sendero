package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import static sendero.Links.Bound.appointAcceptCreator;
import static sendero.Links.Bound.appointUpdateCreator;

public class Links<T> extends Path<T> implements BaseLink {

    private Links(boolean activationListener) {
        super(activationListener);
    }

    private Links(Builders.HolderBuilder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    <S> Links(Builders.HolderBuilder<T> holderBuilder, Supplier<BasePath<S>> basePathSupplier, Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
        super(holderBuilder, basePathSupplier, toAppointFun);
    }

//    public<S> Links(Supplier<BasePath<S>> basePathSupplier, Function<Consumer<Pair.Immutables.Int<T>>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
//        super(basePathSupplier, toAppointFun);
//    }

    @Override
    public boolean isBound() {
        return activationListenerIsSet();
    }

    @Override
    public boolean unbound() {
        return clearActivationListener();
    }

    public static class UnBound<T> extends Links<T> implements BaseLinks.Link<T> {
        private final BaseLinks.AbsLink<T> absConnector = new BaseLinks.AbsLink<T>() {
            @Override
            protected Holders.ActivationHolder<T> holderSupplier() {
                return UnBound.this;
            }
        };

        public UnBound() {
            super(true);
        }

        @Override
        public boolean unbound() {
            return absConnector.unBind();
        }

        public UnBound(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder, ActivationManager.getBuilder().withMutable(true));
        }

        @Override
        public void bind(BasePath<T> path) {
            absConnector.bind(path);
        }

        @Override
        public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
            absConnector.bindFun(path, exit);
        }

        @Override
        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
            absConnector.switchMap(path, switchMap);
        }

        @Override
        public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
            absConnector.switchFun(path, exit);
        }

        //We need an AbsConnector able to sustain update functions only.
//        public static class In<T> extends UnBound<T> {
//            public In(
//                    T initialValue,
//                    Predicate<T> expectOut
//            ) {
//                super(
//                        Holders.DispatcherHolder.get(builder -> builder.withInitial(initialValue).expectOut(expectOut)),
//                        ActivationManager.getBuilder().withMutable(true)
//                );
//            }
//
//            @Override
//            public void update(UnaryOperator<T> update) {
//                super.update(update);
//            }
//        }
    }

    public static class Bound<T> extends Links<T> {

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
                    Builders.get(sBuilder -> sBuilder.withInitial(initialValue).expectOut(expectOut)),
                    () -> fixedPath,
                    new Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>>() {
                        private final AtomicInteger version = new AtomicInteger();
                        @Override
                        public Consumer<Pair.Immutables.Int<S>> apply(Holders.DispatcherHolder<T> tDispatcherHolder) {
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
                    Builders.get(dispatcherBuilder),
                    () -> fixedPath,
                    tDispatcherHolder -> appointAcceptCreator(tDispatcherHolder::acceptVersionValue, map)
            );
        }

        public<S> Bound(
                BasePath<S> fixedPath,
                Function<S, T> map
        ) {
            super(
                    Builders.get(UnaryOperator.identity()),
                    () -> fixedPath,
                    tDispatcherHolder -> appointAcceptCreator(tDispatcherHolder::acceptVersionValue, map)
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
                        Builders.get(sBuilder -> sBuilder.withInitial(initialValue).expectOut(expectOut)),
                        () -> fixedPath,
                        new Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>>() {
                            private final AtomicInteger version = new AtomicInteger();
                            @Override
                            public Consumer<Pair.Immutables.Int<S>> apply(Holders.DispatcherHolder<T> tDispatcherHolder) {
                                return appointUpdateCreator(version, tDispatcherHolder, update);
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
                        tDispatcherHolder -> appointAcceptCreator(tDispatcherHolder, map)
                );
            }


        }
    }
}
