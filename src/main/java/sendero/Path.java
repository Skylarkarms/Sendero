package sendero;

import sendero.interfaces.ActivationListener;
import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.ConsumerUpdater;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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

    public static<T> Path<T> getPath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Function<ConsumerUpdater<T>, ActivationListener> activationsFun
    ) {
        return new Path<>(builderOperator, activationsFun);
    }

    protected Path(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Function<ConsumerUpdater<T>, ActivationListener> activationsFun
    ) {
        super(builderOperator, activationsFun);
    }

    Path(
            Function<Holders.StreamManager<T>, AtomicBinaryEvent> selfMap,
            UnaryOperator<Builders.HolderBuilder<T>> operator
            ) {
        super(
                selfMap,
                operator
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

    Path(T initialValue) {
        this(Builders.withInitial(initialValue));
    }

    protected Path(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        this(builderOperator, (Builders.ManagerBuilder) null);
    }

    protected Path(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Builders.ManagerBuilder mngrBuilderOperator

    ) {
        super(builderOperator, mngrBuilderOperator);
    }

    @Override
    public <S> Path<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map) {
        return new Path<>(
                mapFunctionBuilder(map),
                builderOperator
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
                switchFunctionBuilder(switchMap),
                builderOperator
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

    @Override
    public Path<T> store(String TAG) {
        return (Path<T>) super.store(TAG);
    }
}

