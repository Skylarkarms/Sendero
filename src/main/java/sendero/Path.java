package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class Path<T> extends PathDispatcherHolder<T> {

    @Override
    PathDispatcher<T> getPathDispatcher() {
        return new ToManyPathsDispatcher<>(this);
    }

    protected Path() {
        super();
    }

    protected Path(boolean activationListener) {
        super(activationListener);
    }

    Path(
            UnaryOperator<Builders.HolderBuilder<T>> operator,
            Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(operator, selfMap);
    }

    Path(
            Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(selfMap);
    }

    protected <S> Path(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    protected <S> Path(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(holderBuilder, basePath, updateFun);
    }

    protected Path(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    Path(Builders.HolderBuilder<T> holderBuilder) {
        super(holderBuilder);
    }

    @Override
    public <S> Path<S> forkMap(Function<T, S> map) {
        return new Path<>(
                UnaryOperator.identity(),
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> Path<S> forkUpdate(BiFunction<S, T, S> update) {
        return forkUpdate(
                UnaryOperator.identity(),
                update
        );
    }

    @Override
    public <S> Path<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> operator, BiFunction<S, T, S> update) {
        return new Path<>(
                Builders.getHolderBuild(operator),
                this,
                update
        );
    }

    @Override
    public <S> Path<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> operator, Function<T, S> map) {
        return new Path<>(
                operator,
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> Path<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new Path<>(
                mutateFunctionBuilder(exit)
        );
    }

    @Override
    public <S> Path<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return forkSwitch(
                UnaryOperator.identity(),
                switchMap
        );
    }

    @Override
    public <S> Path<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> operator, Function<T, BasePath<S>> switchMap) {
        return new Path<>(
                operator,
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> Path<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new Path<>(
                switchMutateFunctionBuilder(mutate)
        );
    }
}

