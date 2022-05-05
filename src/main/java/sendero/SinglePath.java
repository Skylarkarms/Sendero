package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;

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

    SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> operator,
            Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(operator, selfMap);
    }

    protected <S> SinglePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    protected <S> SinglePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
        super(holderBuilder, basePath, map);
    }

    @Override
    public <S> SinglePath<S> forkMap(Function<T, S> map) {
        return forkMap(
                UnaryOperator.identity(),
                map
        );
    }

    @Override
    public <S> SinglePath<S> forkUpdate(BiFunction<S, T, S> update) {
        return forkUpdate(UnaryOperator.identity(), update);
    }

    @Override
    public <S> SinglePath<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> operator, BiFunction<S, T, S> update) {
        return new SinglePath<>(
                Builders.getHolderBuild(operator),
                this,
                update
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
        return forkFun(
                UnaryOperator.identity(),
                exit
        );
    }

    @Override
    public <S> SinglePath<S> forkFun(UnaryOperator<Builders.HolderBuilder<S>> operator, Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new SinglePath<>(
                operator,
                mutateFunctionBuilder(exit)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return forkSwitch(
                UnaryOperator.identity(),
                switchMap
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> operator, Function<T, BasePath<S>> switchMap) {
        return new SinglePath<>(
                operator,
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return forkSwitchFun(
                UnaryOperator.identity(),
                mutate
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitchFun(UnaryOperator<Builders.HolderBuilder<S>> operator, Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new SinglePath<>(
                operator,
                switchMutateFunctionBuilder(mutate)
        );
    }

}

