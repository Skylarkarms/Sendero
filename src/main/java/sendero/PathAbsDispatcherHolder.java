package sendero;

import sendero.interfaces.ActivationListener;
import sendero.interfaces.ConsumerUpdater;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public abstract class PathAbsDispatcherHolder<T> extends BasePath<T> {
    private final PathAbsDispatcher<T> pathDispatcher;

    abstract PathAbsDispatcher<T> getPathDispatcher();

    private PathAbsDispatcher<T> pathDispatcherBuild() {
        return getPathDispatcher();
    }

    PathAbsDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Function<ConsumerUpdater<T>, ActivationListener> activationsFun
    ) {
        this(
                builderOperator,
                Builders.ManagerBuilder.onActive(activationsFun)
        );
    }

    protected PathAbsDispatcherHolder() {
        this(myIdentity(), (Builders.ManagerBuilder) null);
    }

    protected PathAbsDispatcherHolder(boolean mutable) {
        this(
                myIdentity(),
                Builders.ManagerBuilder.isMutable(mutable)
        );
    }

    protected <S> PathAbsDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, Function<S, T> map
    ) {
        super(builderOperator, basePath, map);
        pathDispatcher = pathDispatcherBuild();
    }

    protected <S> PathAbsDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(builderOperator, basePath, updateFun);
        pathDispatcher = pathDispatcherBuild();
    }

    protected PathAbsDispatcherHolder(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Builders.ManagerBuilder mngrBuilderOperator
    ) {
        super(builderOperator, mngrBuilderOperator);
        pathDispatcher = pathDispatcherBuild();
    }

    protected PathAbsDispatcherHolder(
            Function<Holders.StreamManager<T>, AtomicBinaryEvent> selfMap,
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator
    ) {
        this(builderOperator,
                Builders.ManagerBuilder.withFixed(
                        selfMap
                )
        );
    }

    @Override
    void coldDispatch(Immutable<T> t) {
        pathDispatcher.coldDispatch(t);
    }

    @Override
    void dispatch(long delay, Immutable<T> t) {
        pathDispatcher.dispatch(delay, t);
    }

    @Override
    protected void appoint(Receptor<T> receptor) {
        pathDispatcher.appoint(receptor);
    }

    @Override
    void pathDispatch(boolean fullyParallel, Immutable<T> t) {
        pathDispatcher.pathDispatch(fullyParallel, t);
    }

    @Override
    protected void demotionOverride(Receptor<T> intConsumer) {
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
            return (O) new Gate.Outs.SingleImpl<>(
                    mapFunctionBuilder(map)
            );
        } else if (outputType == Gate.Out.Many.class) {
            return (O) new Gate.Outs.ManyImpl<>(
                    mapFunctionBuilder(map)
            );
        }
        throw new IllegalStateException("invalid class: " + outputType);
    }

    @Override
    public String toStringDetailed() {
        return super.toString() +
                ",\n remote: " + pathDispatcher;
    }
}

