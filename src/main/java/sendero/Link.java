package sendero;

import sendero.atomics.AtomicUtils;
import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class Link<T> extends Path<T> implements Links.Link<T> {
    private final Links.AbsLink<T> absConnector = new Links.AbsLink<T>() {
        @Override
        protected Holders.ActivationHolder<T> holderSupplier() {
            return Link.this;
        }
    };

    public Link() {
        super(true);
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
    private static<S> BooleanConsumer activationListenerCreator(
            BasePath<S> fixedPath,
            Consumer<Pair.Immutables.Int<S>> toAppoint
    ) {
        return isActive -> {
            if (isActive) fixedPath.appoint(toAppoint);
            else if (fixedPath instanceof ToMany) {
                ((ToMany<S>) fixedPath).demote(toAppoint);
            } else if (fixedPath instanceof Injective) {
                ((Injective<S>) fixedPath).demote();
            }
        };
    }
    public static final class In {
        private static <T, S> Consumer<Pair.Immutables.Int<S>> appointCreator(
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
        public static class UnBound<T> extends Path<T> implements BaseLink {

            private final AtomicUtils.TaggedAtomicReference.Int<BooleanConsumer> reference = new AtomicUtils.TaggedAtomicReference.Int<>();
            private final AtomicInteger version = new AtomicInteger();

            public UnBound(
                    T initialValue,
                    Predicate<T> expectOut
            ) {
                super(
                        Holders.DispatcherHolder.get(builder -> builder.withInitial(initialValue).expectOut(expectOut)),
                        ActivationManager.getBuilder().withMutable(true)
                );
            }

            @Override
            public void update(UnaryOperator<T> update) {
                super.update(update);
            }

            @Override
            public boolean isBound() {
                return activationListenerIsSet();
            }

            @Override
            public boolean unbound() {
                return clearActivationListener();
            }

            public<S> void bind(BasePath<S> path, BiFunction<S, T, T> update) {
                setActivationListener(
                        reference.getOrSet(path.hashCode(), () -> activationListenerCreator(path, appointCreator(version, this::update, update)))
                );
            }
        }
        public static class Bound<T> extends Path<T> {

            public<S> Bound(
                    T initialValue,
                    BasePath<S> fixedPath,
                    BiFunction<S, T, T> update,
                    Predicate<T> expectOut
            ) {
                super(
                        Holders.DispatcherHolder.get(sBuilder -> sBuilder.withInitial(initialValue).expectOut(expectOut)),
                        new Function<Holders.DispatcherHolder<T>, ActivationManager.Builder>() {
                            private final AtomicInteger version = new AtomicInteger();
                            @Override
                            public ActivationManager.Builder apply(Holders.DispatcherHolder<T> tDispatcherHolder) {
                                final Consumer<Pair.Immutables.Int<S>> toAppoint = appointCreator(version, tDispatcherHolder, update);
                                return ActivationManager.getBuilder().withFixed(
                                        activationListenerCreator(fixedPath, toAppoint)
                                );
                            }
                        }
                );
            }

            @Override
            public void update(UnaryOperator<T> update) {
                super.update(update);
            }
        }
    }
}

