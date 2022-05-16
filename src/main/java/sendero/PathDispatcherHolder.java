package sendero;

import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public abstract class PathDispatcherHolder<T> extends BasePath<T> {
    private final PathDispatcher<T> pathDispatcher;

    abstract PathDispatcher<T> getPathDispatcher();

    private PathDispatcher<T> pathDispatcherBuild() {
        return getPathDispatcher();
    }

    protected PathDispatcherHolder() {
        super(myIdentity(), myIdentity());
        pathDispatcher = pathDispatcherBuild();
    }

    protected PathDispatcherHolder(boolean activationListener) {
        super(
                myIdentity(),
managerBuilder -> managerBuilder.withMutable(activationListener)
//                activationListener
        );
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

    protected PathDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(builderOperator,
                managerBuilder -> managerBuilder.withFixedFun(
                        (Function<Holders.ColdHolder<T>, AtomicBinaryEventConsumer>) tColdHolder -> selfMap.apply(tColdHolder::acceptVersionValue)
                )
        );
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
        final Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer> function = new Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer>() {
            final Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> innerFunction = mapFunctionBuilder(map);
            @Override
            public AtomicBinaryEventConsumer apply(Holders.ColdHolder<S> sColdHolder) {
                return innerFunction.apply(sColdHolder::acceptVersionValue);
            }
        };
        if (outputType == Gate.Out.Single.class) {
            return (O) new Gate.Outs.SingleImpl<>(function);
        } else if (outputType == Gate.Out.Many.class) {
            return (O) new Gate.Outs.ManyImpl<>(
                    function
//                    mapFunctionBuilder(map)
            );
        }
        throw new IllegalStateException("invalid class: " + outputType);
    }

}

