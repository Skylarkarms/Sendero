package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class Link<T> extends Path<T> implements BaseLink {
    private Link(Builders.HolderBuilder<T> holderBuilder, boolean mutableManager) {
        super(holderBuilder, ActivationManager.getBuilder().withMutable(mutableManager));
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
        return clearActivationListener();
    }

    public static class Unbound<T> extends Link<T> implements UnboundLink<T> {

        final ActivePathListener<T> activePathListener;

        void forceSet(BooleanConsumer activationListener) {
            activePathListener.forcedSet(activationListener);
        }


        public Unbound() {
            super(true);
            activePathListener = new ActivePathListener<T>(manager, selfAppointer) {
//                @Override
//                void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
//                    Unbound.this.acceptVersionValue(versionValue);
//                }
            };

        }

        public Unbound(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder, true);
            activePathListener = new ActivePathListener<T>(manager, selfAppointer) {
//                @Override
//                void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
//                    Unbound.this.acceptVersionValue(versionValue);
//                }
            };
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
            //        void bind(BasePath<T> path);
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

            private final BaseLinks.AbsLink2<T> absLink = new BaseLinks.AbsLink2<T>(activePathListener) {
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
}
