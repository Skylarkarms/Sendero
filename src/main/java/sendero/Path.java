package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;

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
        super(operator,
                builder -> builder.withFixedFun(
                        selfMap
                )
        );
    }

    protected <S> Path(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map) {
        super(builderOperator, basePath, map);
    }

    protected <S> Path(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(builderOperator, basePath, updateFun);
    }

    protected Path(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            UnaryOperator<Builders.ManagerBuilder> mngrBuilderOperator

    ) {
        super(builderOperator, mngrBuilderOperator);
    }

    Path(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        super(builderOperator);
    }

    @Override
    public <S> Path<S> forkUpdate(BiFunction<S, T, S> update) {
        return (Path<S>) super.forkUpdate(update);
    }

    @Override
    public <S> Path<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update) {
        return new Path<>(
                builderOperator,
                this,
                update
        );
    }

    @Override
    public <S> Path<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map) {
        return new Path<>(
                builderOperator,
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> Path<S> forkMap(Function<T, S> map) {
        return (Path<S>) super.forkMap(map);
    }

    @Override
    public <S> Path<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return (Path<S>) super.forkFun(exit);
    }

    @Override
    public <S> Path<S> forkFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new Path<>(
                builderOperator,
                mutateFunctionBuilder(exit)
        );
    }

    @Override
    public <S> Path<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new Path<>(
                builderOperator,
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> Path<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return (Path<S>) super.forkSwitch(switchMap);
    }

    @Override
    public <S> Path<S> forkSwitchFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new Path<>(
                builderOperator,
                switchMutateFunctionBuilder(mutate)
        );
    }

    @Override
    public <S> Path<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return (Path<S>) super.forkSwitchFun(mutate);
    }
}

