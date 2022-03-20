package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Path<T> extends BasePath.ToMany<T> implements Forkable<T> {

    public Path() {
    }


    public Path(boolean activationListener) {
        super(activationListener);
    }

    Path(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
        super(selfMap);
    }

    <S> Path(Builders.HolderBuilder<T> holderBuilder, Supplier<BasePath<S>> basePathSupplier, Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
        super(holderBuilder,
                dispatcher -> ActivationManager.getBuilder().withFixed(BasePath.activationListenerCreator(basePathSupplier, toAppointFun.apply(dispatcher)))
                );
    }

    protected Path(Builders.HolderBuilder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    private <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> mainForkingFunctionBuilder(Function<Consumer<Pair.Immutables.Int<S>>, Consumer<Pair.Immutables.Int<T>>> converter) {
        return intConsumer -> {
            final Consumer<Pair.Immutables.Int<T>> converted = converter.apply(intConsumer);
            return isActive -> {
                if (isActive) appoint(converted);
                else demote(converted);
            };
        };
    }

    protected Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> injectiveFunctionBuilder() {
        return mainForkingFunctionBuilder(UnaryOperator.identity());
    }

    protected <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> mapFunctionBuilder(Function<T, S> map) {
        return mainForkingFunctionBuilder(
                intConsumer ->
                        tInt -> intConsumer.accept(new Pair.Immutables.Int<>(tInt.getInt(), map.apply(tInt.getValue())))
        );
    }

    protected <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> mutateFunctionBuilder(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return mainForkingFunctionBuilder(
                intConsumer ->
                        tInt -> {
                            T t = tInt.getValue();
                            int intT = tInt.getInt();
                            final Consumers.BaseConsumer<T> exitC = exit.apply(s -> intConsumer.accept(new Pair.Immutables.Int<S>(intT, s)));
                            exitC.accept(t);
                        }
        );
    }

    protected <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> switchFunctionBuilder(Function<T, BasePath<S>> switchMap) {
        final BasePath.ToMany<T> thisDomain = Path.this;
        return intConsumer -> {
            final BaseLinks.LinkHolder<S> jointHolder = new BaseLinks.LinkHolder<S>() {
                @Override
                protected void coldDispatch(Pair.Immutables.Int<S> t) {
                    intConsumer.accept(t);
                }
            };
            final Consumer<Pair.Immutables.Int<T>> toAppoint = new Consumer<Pair.Immutables.Int<T>>() {
                //Controls domain version
                final Holders.DispatcherHolder<BasePath<S>> domainHolder = new Holders.DispatcherHolder<BasePath<S>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<S>> t) {
                        jointHolder.bind(t.getValue());
                    }
                };
                {
                    domainHolder.expectIn(
                            sDomain -> sDomain != null && sDomain != domainHolder.get()
                    );
                }
                @Override
                public void accept(Pair.Immutables.Int<T> tInt) {
                    domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(tInt.getInt(), switchMap.apply(tInt.getValue())));
                }
            };
            return isActive -> {
                if (isActive) {
                    jointHolder.tryActivate();
                    thisDomain.appoint(toAppoint); // I give to appoint
                }
                else {
                    jointHolder.tryDeactivate();
                    thisDomain.demote(toAppoint);
                }
            };
        };
    }

    protected <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> switchMutateFunctionBuilder(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        final BasePath.ToMany<T> thisDomain = Path.this;
        return intConsumer -> {
            //Controls domain subscription
            final BaseLinks.LinkHolder<S> domainSubscriber = new BaseLinks.LinkHolder<S>() {
                @Override
                protected void dispatch(Pair.Immutables.Int<S> t) {
                    intConsumer.accept(t);
                }
            };
            // Recepts the value to be transformed into an observable
            final Consumer<Pair.Immutables.Int<T>> toAppoint = new Consumer<Pair.Immutables.Int<T>>() {
                //Controls domain version
                final Holders.DispatcherHolder<BasePath<S>> domainHolder = new Holders.DispatcherHolder<BasePath<S>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<S>> t) {
                        domainSubscriber.bind(t.getValue());
                    }
                };
                {
                    domainHolder.expectIn(
                            sDomain -> sDomain != null && sDomain != domainHolder.get()
                    );
                }
                @Override
                public void accept(Pair.Immutables.Int<T> tInt) {
                    int intS = tInt.getInt();
                    T s = tInt.getValue();
                    final Consumer<T> transorfmed = mutate.apply(
                            tDomain -> domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(intS, tDomain))
                    );
                    transorfmed.accept(s);
                }
            };
            return isActive -> {
                if (isActive) {
                    domainSubscriber.tryActivate();
                    thisDomain.appoint(toAppoint);
                }
                else {
                    domainSubscriber.tryDeactivate();
                    thisDomain.demote(toAppoint);
                }
            };
        };
    }


    @SuppressWarnings("unchecked")
    @Override
    public <O extends Gates.Out<T>> O out(Class<? super O> outputType) {
        if (outputType == Gates.Out.Single.class) {
            return (O) new Gates.Outs.SingleImpl<T>(injectiveFunctionBuilder());
        } else if (outputType == Gates.Out.Many.class) {
            return (O) new Gates.Outs.ManyImpl<T>(injectiveFunctionBuilder());
        }
        throw new IllegalStateException("invalid class: " + outputType);
    }

    @Override
    public <S> Path<S> forkMap(Function<T, S> map) {
        return new Path<S>(
                mapFunctionBuilder(map)
        ) {};
    }

    @Override
    public <S> Path<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new Path<S>(
                mutateFunctionBuilder(exit)
        ) {};
    }

    @Override
    public <S> Path<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return new Path<S>(
                switchFunctionBuilder(switchMap)
        ) {};
    }

    @Override
    public <S> Path<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new Path<S>(
                switchMutateFunctionBuilder(mutate)
        ) {};
    }
}

