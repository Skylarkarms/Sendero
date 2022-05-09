package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

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

    protected SinglePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        this(builderOperator, myIdentity());
    }

    protected SinglePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator, UnaryOperator<Builders.ManagerBuilder> actMgmtBuilder) {
//    protected SinglePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator, Builders.ManagerBuilder actMgmtBuilder) {
//    protected SinglePath(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
        super(builderOperator, actMgmtBuilder);
    }

    SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> operator,
            Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(
                operator,
                builder -> builder.withFixedFun(selfMap)
//                Builders.getManagerBuild().withFixedFun(selfMap)
        );
    }

    protected <S> SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> holderBuilder,
//            Builders.HolderBuilder<T> holderBuilder,
            BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder, basePath, map);
    }

    protected <S> SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> map) {
        super(builderOperator, basePath, map);
    }

    @Override
    public <S> SinglePath<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map) {
        return new SinglePath<>(
                builderOperator,
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> SinglePath<S> forkMap(Function<T, S> map) {
        return (SinglePath<S>) super.forkMap(map);
    }


    @Override
    public <S> SinglePath<S> forkUpdate(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update) {
        return new SinglePath<>(
                builderOperator,
                this,
                update
        );
    }

    @Override
    public <S> SinglePath<S> forkUpdate(BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.forkUpdate(update);
    }

    @Override
    public <S> SinglePath<S> forkFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return new SinglePath<>(
                builderOperator,
                mutateFunctionBuilder(exit)
        );
    }

    @Override
    public <S> SinglePath<S> forkFun(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return (SinglePath<S>) super.forkFun(exit);
    }

    @Override
    public <S> SinglePath<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new SinglePath<>(
                builderOperator,
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.forkSwitch(switchMap);
    }

    @Override
    public <S> SinglePath<S> forkSwitchFun(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return new SinglePath<>(
                builderOperator,
                switchMutateFunctionBuilder(mutate)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitchFun(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return (SinglePath<S>) super.forkSwitchFun(mutate);
    }
}

