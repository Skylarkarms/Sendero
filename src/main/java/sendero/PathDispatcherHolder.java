package sendero;

import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class PathDispatcherHolder<T> extends BasePath<T> {
    private final PathDispatcher<T> pathDispatcher;

    abstract PathDispatcher<T> getPathDispatcher();

    private PathDispatcher<T> pathDispatcherBuild() {
        return getPathDispatcher();
    }

    protected PathDispatcherHolder() {
        super();
        pathDispatcher = pathDispatcherBuild();
    }

    protected PathDispatcherHolder(boolean activationListener) {
        super(activationListener);
        pathDispatcher = pathDispatcherBuild();
    }

    protected <S> PathDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map) {
        super(builderOperator, basePath, map);
        pathDispatcher = pathDispatcherBuild();
    }

    protected <S> PathDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(builderOperator, basePath, updateFun);
        pathDispatcher = pathDispatcherBuild();
    }

    protected PathDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            UnaryOperator<Builders.ManagerBuilder> mngrBuilderOperator
    ) {
        super(builderOperator, mngrBuilderOperator);
        pathDispatcher = pathDispatcherBuild();
    }

    PathDispatcherHolder(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        super(builderOperator);
        pathDispatcher = pathDispatcherBuild();
    }

    @Override
    void coldDispatch(Pair.Immutables.Int<T> t) {
        pathDispatcher.coldDispatch(t);
    }

    @Override
    void dispatch(long delay, Pair.Immutables.Int<T> t) {
        pathDispatcher.dispatch(delay, t);
    }

    @Override
    protected void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
        pathDispatcher.appoint(subscriber);
    }

    @Override
    void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
        pathDispatcher.pathDispatch(fullyParallel, t);
    }

    @Override
    protected void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer) {
        pathDispatcher.demotionOverride(intConsumer);
    }

    @Override
    boolean deactivationRequirements() {
        return pathDispatcher.isInactive();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, O extends Gate.Out<S>> O out(Class<? super O> outputType, Function<T, S> map) {
        if (outputType == Gate.Out.Single.class) {
            return (O) new Gate.Outs.SingleImpl<>(mapFunctionBuilder(map));
        } else if (outputType == Gate.Out.Many.class) {
            return (O) new Gate.Outs.ManyImpl<>(mapFunctionBuilder(map));
        }
        throw new IllegalStateException("invalid class: " + outputType);
    }

}

