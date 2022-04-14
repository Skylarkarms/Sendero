package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class SinglePath<T> extends PathDispatcherHolder<T> {

    @Override
    PathDispatcher<T> getPathDispatcher() {
        return new InjectivePathDispatcher<>(this);
    }

    protected SinglePath() {
        super();
    }

    protected SinglePath(boolean mutableActivationListener) {
        super(mutableActivationListener);
    }

    protected SinglePath(Builders.HolderBuilder<T> holderBuilder) {
        super(holderBuilder, Builders.getManagerBuild());
    }

    protected SinglePath(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    SinglePath(UnaryOperator<Builders.HolderBuilder<T>> operator, Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
        super(operator, selfMap);
    }

    SinglePath(Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
        super(selfMap);
    }

    protected <S> SinglePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    protected <S> SinglePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
        super(holderBuilder, basePath, map);
    }

    @Override
    public <S> SinglePath<S> forkMap(Function<T, S> map) {
        return new SinglePath<>(
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> SinglePath<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> operator, Function<T, S> map) {
        return new SinglePath<>(
                operator,
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> SinglePath<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new SinglePath<>(
                mutateFunctionBuilder(exit)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return new SinglePath<>(
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new SinglePath<>(
                switchMutateFunctionBuilder(mutate)
        );
    }

}

