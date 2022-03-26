package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;

final class BaseLinks {
    private interface LinkActions<T> {
        void bind(BasePath<T> path);
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

    interface Link<T> extends BaseLink, LinkActions<T> {
    }


    static abstract class AbsLink2<T> implements sendero.Link.Unbound.UnboundSwitch<T> {

        private final ActivePathListener<T> activePathListener;

        protected AbsLink2(ActivePathListener<T> activePathListener) {
            this.activePathListener = activePathListener;
        }


        protected abstract void onResult(Pair.Immutables.Int<T> tPair);

        private <S> void baseConnect(
                BasePath<S> observable,
                Consumer<Pair.Immutables.Int<S>> exit
        ) {
            activePathListener.forcedSet(
                    BasePath.activationListenerCreator(
                            () -> observable, exit
                    ));
        }

        @Override
        public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
            baseConnect(path, sInt -> {
                final int sInt1 = sInt.getInt();
                final S s = sInt.getValue();
                exit.apply(t -> onResult(new Pair.Immutables.Int<>(sInt1, t))
                ).accept(s);
            });
        }

        @Override
        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
            final Appointers.SimpleAppointer<T> appointer = new Appointers.SimpleAppointer<>(this::onResult,t -> true);
//            final LinkHolder<T> domainSubscriber = new LinkHolder<T>() {
//                @Override
//                protected void coldDispatch(Pair.Immutables.Int<T> t) {
//                    onResult(t);
//                }
//            };

            final sendero.Link.SingleLink<BasePath<T>> basePathSingleLink = new sendero.Link.SingleLink.Bound<>(path, switchMap::apply);

            final BooleanConsumer booleanConsumer = BasePath.activationListenerCreator(
                    () -> basePathSingleLink,
                    new Consumer<Pair.Immutables.Int<BasePath<T>>>() {
                        final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
                            @Override
                            protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
//                                domainSubscriber.bind(t.getValue());
                                appointer.setAndStart(t.getValue());
                            }
                        };
                        {
                            domainHolder.expectIn(
                                    tDomain -> tDomain != null && tDomain != domainHolder.get()
                            );
                        }@Override
                        public void accept(Pair.Immutables.Int<BasePath<T>> basePathInt) {
                            domainHolder.acceptVersionValue(basePathInt.deepCopy());
                        }
                    }
            );
            final BooleanConsumer finalConsumer = isActive -> {
                if (isActive) appointer.start();
//                if (isActive) domainSubscriber.tryActivate();
                else appointer.stop();
//                else appointer.stopAndClearPath();
//                else appointer.stop();
//                else domainSubscriber.tryDeactivate();

                booleanConsumer.accept(isActive);
            };
//            ownerCache.set(finalConsumer);
//            thisHolder.setActivationListener(finalConsumer);
            activePathListener.forcedSet(finalConsumer);
        }

        @Override
        public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
            final Appointers.SimpleAppointer<T> appointer = new Appointers.SimpleAppointer<>(this::onResult,t -> true);
//            final LinkHolder<T> domainSubscriber = new LinkHolder<T>() {
//                @Override
//                protected void coldDispatch(Pair.Immutables.Int<T> t) {
//                    onResult(t);
//                }
//            };

            final BooleanConsumer booleanConsumer = BasePath.activationListenerCreator(
                    () -> path,
                    new Consumer<Pair.Immutables.Int<S>>() {
                        final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
                            @Override
                            protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
                                appointer.setAndStart(t.getValue());
//                                domainSubscriber.bind(t.getValue());
                            }
                        };

                        {
                            domainHolder.expectIn(
                                    tDomain -> tDomain != null && tDomain != domainHolder.get()
                            );
                        }
                        @Override
                        public void accept(Pair.Immutables.Int<S> sInt) {
                            S s = sInt.getValue();
                            int intS = sInt.getInt();
                            Consumer<S> computedExit = exit.apply(
                                    tDomain -> domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(intS, tDomain))
                            );
                            computedExit.accept(s);
                        }
                    }
            );
            final BooleanConsumer finalActivationListener = isActive -> {
                if (isActive) appointer.start();
//                if (isActive) domainSubscriber.tryActivate();
                else appointer.stop();
//                else domainSubscriber.tryDeactivate();

                booleanConsumer.accept(isActive);
            };
//            ownerCache.set(finalActivationListener);
//            thisHolder.setActivationListener(finalActivationListener);
            activePathListener.forcedSet(finalActivationListener);
        }
    }


//    static abstract class AbsLink<T> implements LinkActions<T> {
//
//        private final AtomicUtils.TaggedAtomicReference<BasePath<?>, BooleanConsumer> ownerCache = new AtomicUtils.TaggedAtomicReference<>();
//
//        protected abstract Holders.ActivationHolder<T> holderSupplier();
//
//        private <S> void baseConnect(
//                BasePath<S> observable,
//                Consumer<Pair.Immutables.Int<S>> exit
//        ) {
//            holderSupplier().setActivationListener(
//                    ownerCache.diffSetAndGet(observable, () -> BasePath.activationListenerCreator(
//                    () -> observable, exit
//            )));
//        }
//        @Override
//        public void bind(BasePath<T> path) {
//            baseConnect(path, holderSupplier()::acceptVersionValue);
//        }
//
//        protected boolean unBind() {
//            //If contention changed the ownerCache nothing happens while holder is still cleared.
//            return ownerCache.expectAndClear(holderSupplier().getAndClearActivationListener());
//        }
//
//        @Override
//        public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
//            baseConnect(path, sInt -> {
//                final int sInt1 = sInt.getInt();
//                final S s = sInt.getValue();
//                exit.apply(t -> AbsLink.this.holderSupplier().acceptVersionValue(new Pair.Immutables.Int<>(sInt1, t))
//                ).accept(s);
//            });
//        }
//
//        @Override
//        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
//            final Holders.ActivationHolder<T> thisHolder = holderSupplier();
//            final LinkHolder<T> domainSubscriber = new LinkHolder<T>() {
//                @Override
//                protected void coldDispatch(Pair.Immutables.Int<T> t) {
//                    thisHolder.acceptVersionValue(t);
//                }
//            };
//            final sendero.Link.SingleLink<BasePath<T>> basePathSingleLink = new sendero.Link.SingleLink.Bound<>(path, switchMap::apply);
//
//            final BooleanConsumer booleanConsumer = BasePath.activationListenerCreator(
//                    () -> basePathSingleLink,
//                    new Consumer<Pair.Immutables.Int<BasePath<T>>>() {
//                        final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
//                            @Override
//                            protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
//                                domainSubscriber.bind(t.getValue());
//                            }
//                        };
//                        {
//                            domainHolder.expectIn(
//                                    tDomain -> tDomain != null && tDomain != domainHolder.get()
//                            );
//                        }@Override
//                        public void accept(Pair.Immutables.Int<BasePath<T>> basePathInt) {
//                            domainHolder.acceptVersionValue(basePathInt.deepCopy());
//                        }
//                    }
//            );
//            final BooleanConsumer finalConsumer = isActive -> {
//                if (isActive) domainSubscriber.tryActivate();
//                else domainSubscriber.tryDeactivate();
//
//                booleanConsumer.accept(isActive);
//            };
//            ownerCache.set(finalConsumer);
//            thisHolder.setActivationListener(finalConsumer);
//        }
//
//        @Override
//        public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
//            final Holders.ActivationHolder<T> thisHolder = holderSupplier();
//            final LinkHolder<T> domainSubscriber = new LinkHolder<T>() {
//                @Override
//                protected void coldDispatch(Pair.Immutables.Int<T> t) {
//                    thisHolder.acceptVersionValue(t);
//                }
//            };
//
//            final BooleanConsumer booleanConsumer = BasePath.activationListenerCreator(
//                    () -> path,
//                    new Consumer<Pair.Immutables.Int<S>>() {
//                        final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
//                            @Override
//                            protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
//                                domainSubscriber.bind(t.getValue());
//                            }
//                        };
//
//                        {
//                            domainHolder.expectIn(
//                                    tDomain -> tDomain != null && tDomain != domainHolder.get()
//                            );
//                        }
//                        @Override
//                        public void accept(Pair.Immutables.Int<S> sInt) {
//                            S s = sInt.getValue();
//                            int intS = sInt.getInt();
//                            Consumer<S> computedExit = exit.apply(
//                                    tDomain -> domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(intS, tDomain))
//                            );
//                            computedExit.accept(s);
//                        }
//                    }
//            );
//            final BooleanConsumer finalActivationListener = isActive -> {
//                if (isActive) domainSubscriber.tryActivate();
//                else domainSubscriber.tryDeactivate();
//
//                booleanConsumer.accept(isActive);
//            };
//            ownerCache.set(finalActivationListener);
//            thisHolder.setActivationListener(finalActivationListener);
//        }
//    }

//    protected static class LinkHolder<T> extends Holders.ActivationHolder<T> implements Link<T> {
//
//        private final AbsLink<T> absConnector = new AbsLink<T>() {
//            @Override
//            protected Holders.ActivationHolder<T> holderSupplier() {
//                return LinkHolder.this;
//            }
//        };
//
//        protected LinkHolder() {
//            super(true);
//        }
//
//        @Override
//        public boolean isBound() {
//            return activationListenerIsSet(); //LinkHolder.class
//        }
//
//        @Override
//        public boolean unbound() {
//            return absConnector.unBind();
//        }
//
//        @Override
//        public void bind(BasePath<T> path) {
//            absConnector.bind(path);
//        }
//
//        @Override
//        public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
//            absConnector.bindFun(path, exit);
//        }
//
//        @Override
//        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
//            absConnector.switchMap(path, switchMap);
//        }
//
//        @Override
//        public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
//            absConnector.switchFun(path, exit);
//        }
//    }

//    private static class PathListenerHolder<T> extends Holders.DispatcherHolder<T> implements BasePath.PathListener<T> {
//        private final BasePath.SelfAppointer<T> appointer = new BasePath.SelfAppointer<>(this);
//
//        @Override
//        public <S, P extends BasePath<S>> BasePath.Appointer<?> setPath(P basePath, Function<S, T> map) {
//            return appointer.setPath(basePath, map);
//        }
//
//        @Override
//        public <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map) {
//            appointer.setAndStart(basePath, map);
//        }
//
//        @Override
//        public <P extends BasePath<T>> void setAndStart(P basePath) {
//            appointer.setAndStart(basePath);
//        }
//
//        @Override
//        public void stopAndClearPath() {
//            appointer.stopAndClearPath();
//        }
//
//        @Override
//        public boolean start() {
//            return appointer.start();
//        }
//
//        @Override
//        public boolean stop() {
//            return appointer.stop();
//        }
//
//        @Override
//        public boolean isActive() {
//            return appointer.isActive();
//        }
//
//        @Override
//        public BasePath.Appointer<?> getAppointer() {
//            return appointer.getAppointer();
//        }
//
//        @Override
//        public BasePath.Appointer<?> clearAndGet() {
//            return appointer.clearAndGet();
//        }
//    }

//    protected static class LinkHolder2<T> extends Holders.ActivationHolder<T> implements UnboundLink<T>, BaseLink {
//
//        private final ActivePathListener<T> activePathListener = new ActivePathListener<>(manager, new BasePath.SelfAppointer<>(holder));
//
//        protected LinkHolder2() {
//            super(true);
//        }
//
//        @Override
//        public boolean isBound() {
//            return activationListenerIsSet(); //LinkHolder.class
//        }
//
//        @Override
//        public boolean unbound() {
//            return activePathListener.unbound();
//        }
//
//        @Override
//        public <P extends BasePath<T>> void bind(P basePath) {
//            activePathListener.bind(basePath);
//        }
//
//        @Override
//        public <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
//            activePathListener.bindMap(basePath, map);
//        }
//
////        @Override
////        public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
////            activePathListener.bindFun(path, exit);
////        }
////
////        @Override
////        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
////            absConnector.switchMap(path, switchMap);
////        }
////
////        @Override
////        public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
////            absConnector.switchFun(path, exit);
////        }
//    }
}
