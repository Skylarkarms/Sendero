package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.Updater;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Path<T> extends BasePath.ToMany<T> implements Forkable<T> {

    protected Path() {
    }

    protected Path(boolean activationListener) {
        super(activationListener);
    }

    Path(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
        super(selfMap);
    }

    protected <S> Path(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<Updater<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
        super(holderBuilder,
                dispatcher -> Builders.getManagerBuild().withFixed(
                        Appointers.Appointer.booleanConsumerAppointer(basePath, toAppointFun.apply(dispatcher))

                ));
    }

    <S> Path(Builders.HolderBuilder<T> holderBuilder, Function<Holders.ColdHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun, BasePath<S> basePath) {
        super(holderBuilder,
                dispatcher -> Builders.getManagerBuild().withFixed(
                        Appointers.Appointer.booleanConsumerAppointer(basePath, toAppointFun.apply(dispatcher))

                ));
    }

    protected Path(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    Path(Builders.HolderBuilder<T> holderBuilder) {
        super(holderBuilder);
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

    @SuppressWarnings("unchecked")
    @Override
    public <S, O extends Gates.Out<S>> O out(Class<? super O> outputType, Function<T, S> map) {
        if (outputType == Gates.Out.Single.class) {
            return (O) new Gates.Outs.SingleImpl<S>(mapFunctionBuilder(map));
        } else if (outputType == Gates.Out.Many.class) {
            return (O) new Gates.Outs.ManyImpl<S>(mapFunctionBuilder(map));
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

