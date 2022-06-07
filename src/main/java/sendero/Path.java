package sendero;

import sendero.interfaces.BinaryPredicate;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public class Path<T> extends PathAbsDispatcherHolder<T> {

    @Override
    PathAbsDispatcher<T> getPathDispatcher() {
        return new ToManyPathsAbsDispatcher<>(this);
    }

    protected Path() {
        super();
    }

    protected Path(boolean activationListener) {
        super(activationListener);
    }

    Path(
            UnaryOperator<Builders.HolderBuilder<T>> operator,
            Function<Holders.StreamManager<T>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(operator,
                selfMap
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

    Path(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        this(builderOperator, myIdentity());
    }

    protected Path(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            UnaryOperator<Builders.ManagerBuilder> mngrBuilderOperator

    ) {
        super(builderOperator, mngrBuilderOperator);
    }

    @Override
    public <S> Path<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map) {
        return new Path<>(
                builderOperator,
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> Path<S> forkMap(BinaryPredicate<S> excludeIn, Function<T, S> map) {
        return (Path<S>) super.forkMap(excludeIn, map);
    }

    @Override
    public <S> Path<S> forkMap(Function<T, S> map) {
        return (Path<S>) super.forkMap(map);
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
    public <S> Path<S> forkUpdate(BinaryPredicate<S> excludeIn, BiFunction<S, T, S> update) {
        return (Path<S>) super.forkUpdate(excludeIn, update);
    }

    @Override
    public <S> Path<S> forkUpdate(BiFunction<S, T, S> update) {
        return (Path<S>) super.forkUpdate(update);
    }

    @Override
    public <S> Path<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new Path<>(
                builderOperator,
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> Path<S> forkSwitch(BinaryPredicate<S> excludeIn, Function<T, BasePath<S>> switchMap) {
        return (Path<S>) super.forkSwitch(excludeIn, switchMap);
    }

    @Override
    public <S> Path<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return (Path<S>) super.forkSwitch(switchMap);
    }

}

