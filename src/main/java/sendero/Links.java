package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

final class Links {
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

    static abstract class AbsLink<T> implements LinkActions<T> {

        private final AtomicReference<OwnerExitConsumer> owner = new AtomicReference<>(OwnerExitConsumer.FIRST);

        private static final class OwnerExitConsumer {
            final int ownerId, exitId;
            final BooleanConsumer activationListener;

            public static final OwnerExitConsumer FIRST = INITIALIZE();

            private static OwnerExitConsumer INITIALIZE() {
                return new OwnerExitConsumer(0, 0, null);
            }

            private OwnerExitConsumer(int ownerId, int exitId, BooleanConsumer activationListener) {
                this.ownerId = ownerId;
                this.exitId = exitId;
                this.activationListener = activationListener;
            }

            boolean equalTo(int ownerHash, int exitHash) {
                return  this != FIRST && ownerId == ownerHash && exitId == exitHash;
            }
        }

        protected abstract Holders.ActivationHolder<T> holderSupplier();

        private <S> void baseConnect(
                BasePath<S> observable,
                Consumer<Pair.Immutables.Int<S>> exit
        ) {
            final BooleanConsumer activationListener = AcceptorConsumer.mate(owner, observable, exit);
            holderSupplier().setActivationListener(activationListener);
        }
        @Override
        public void bind(BasePath<T> path) {
            baseConnect(path, holderSupplier()::acceptVersionValue);
        }

        @Override
        public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
            baseConnect(path, sInt -> {
                final int sInt1 = sInt.getInt();
                final S s = sInt.getValue();
                exit.apply(t -> holderSupplier().acceptVersionValue(new Pair.Immutables.Int<>(sInt1, t))
                ).accept(s);
            });
        }

        @Override
        public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
            final Holders.ActivationHolder<T> thisHolder = holderSupplier();
            final LinkHolder<T> domainSubscriber = new LinkHolder<T>() {
                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    thisHolder.acceptVersionValue(t);
                }
            };

            final BooleanConsumer activationListener = AcceptorConsumer.mate(owner, path, new Consumer<Pair.Immutables.Int<S>>() {
                final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
                        domainSubscriber.bind(t.getValue());
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
                    domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(intS, switchMap.apply(s)));
                }
            });
            thisHolder.setActivationListener(new BooleanConsumer() {
                @Override
                public void accept(boolean isActive) {
                    if (isActive) domainSubscriber.tryActivate();
                    else domainSubscriber.tryDeactivate(true);

                    activationListener.accept(isActive);
                }
            });
        }

        @Override
        public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
            final Holders.ActivationHolder<T> thisHolder = holderSupplier();
            final LinkHolder<T> domainSubscriber = new LinkHolder<T>() {
                @Override
                protected void coldDispatch(Pair.Immutables.Int<T> t) {
                    thisHolder.acceptVersionValue(t);
                }
            };
            final BooleanConsumer activationListener = AcceptorConsumer.mate(owner, path, new Consumer<Pair.Immutables.Int<S>>() {
                final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
                        domainSubscriber.bind(t.getValue());
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
            });
            thisHolder.setActivationListener(new BooleanConsumer() {
                @Override
                public void accept(boolean isActive) {
                    if (isActive) domainSubscriber.tryActivate();
                    else domainSubscriber.tryDeactivate(true);

                    activationListener.accept(isActive);
                }
            });
        }

        private static final class AcceptorConsumer<S> implements BooleanConsumer, Consumer<Pair.Immutables.Int<S>> {
            private final BasePath<S> newOwner;
            private final Consumer<Pair.Immutables.Int<S>> exit;
            private AcceptorConsumer(Consumer<Pair.Immutables.Int<S>> exit, BasePath<S> newOwner) {
                this.exit = exit;
                this.newOwner = newOwner;
            }

            private static<S> BooleanConsumer forNew(BasePath<S> newOwner, Consumer<Pair.Immutables.Int<S>> exit) {
                return new AcceptorConsumer<>(exit, newOwner);
            }

            /**isActive will be false immediately during a call to unregister.
             * */
            @Override
            public void accept(boolean isActive) {
                if (isActive) newOwner.appoint(this);
                else {
                    if (newOwner instanceof BasePath.ToMany) {
                        ((BasePath.ToMany<S>) newOwner).demote(this);
                    } else if (newOwner instanceof BasePath.Injective) {
                        ((BasePath.Injective<S>) newOwner).demote();
                    }
                }
            }
            /**returns true if equal, if false sets new owner to this acceptor*/
            public static<S> BooleanConsumer mate(
                    AtomicReference<OwnerExitConsumer> owner,
                    BasePath<S> newOwner,
                    Consumer<Pair.Immutables.Int<S>> exit
            ) {
                int newOwnerHash = newOwner.hashCode(), newExitHash = exit.hashCode();
                OwnerExitConsumer prev, next;
                do {
                    prev = owner.get();
                    if (prev.equalTo(newOwnerHash, newExitHash)) return prev.activationListener;
                    next = new OwnerExitConsumer(newOwnerHash, newExitHash, AcceptorConsumer.forNew(newOwner, exit));
                } while (!owner.compareAndSet(prev, next));
                return next.activationListener;
            }

            @Override
            public void accept(Pair.Immutables.Int<S> tInt) {
                exit.accept(tInt);
            }
        }

    }

    protected static class LinkHolder<T> extends Holders.ActivationHolder<T> implements Link<T> {

        private final AbsLink<T> absConnector = new AbsLink<T>() {
            @Override
            protected Holders.ActivationHolder<T> holderSupplier() {
                return LinkHolder.this;
            }
        };

        protected LinkHolder() {
            super(true);
        }

        private LinkHolder(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        @Override
        public boolean isBound() {
            return activationListenerIsSet();
        }

        @Override
        public boolean unbound() {
            return clearActivationListener();
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
    }
}
