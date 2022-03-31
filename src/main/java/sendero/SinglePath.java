package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;

public class SinglePath<T> extends BasePath.Injective<T> implements Forkable<T> {

    protected SinglePath(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
        super(selfMap);
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
    public <S> SinglePath<S> forkMap(Function<T, S> map) {
        return new SinglePath<S>(
                mapFunctionBuilder(map)
        ) {};
    }

    @Override
    public <S> SinglePath<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new SinglePath<S>(
                mutateFunctionBuilder(exit)
        ) {};
    }

    @Override
    public <S> SinglePath<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return new SinglePath<S>(
                switchFunctionBuilder(switchMap)
        ) {};
    }

    @Override
    public <S> SinglePath<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new SinglePath<S>(
                switchMutateFunctionBuilder(mutate)
        ) {};
    }

}

