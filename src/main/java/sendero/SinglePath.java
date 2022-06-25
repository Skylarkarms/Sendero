package sendero;

import sendero.interfaces.BinaryPredicate;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.myIdentity;

public class SinglePath<T> extends PathAbsDispatcherHolder<T> {

    @Override
    PathAbsDispatcher<T> getPathDispatcher() {
        return new InjectivePathAbsDispatcher<>(this);
    }

    protected SinglePath() {
        super();
    }

    protected SinglePath(boolean mutableActivationListener) {
        super(mutableActivationListener);
    }

    protected SinglePath(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        this(builderOperator, (Builders.ManagerBuilder) null);
    }


    protected SinglePath(Builders.ManagerBuilder mngrBuilder) {
        this(myIdentity(),
                mngrBuilder
        );
    }

    protected SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Builders.ManagerBuilder actMgmtBuilder
    ) {
        super(builderOperator, actMgmtBuilder);
    }

    SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> operator,
            Function<Holders.StreamManager<T>, AtomicBinaryEvent> selfMap
    ) {
        super(
                operator,
                selfMap
        );
    }

    protected <S> SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> holderBuilder,
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
    public <S> SinglePath<S> forkMap(BinaryPredicate<S> excludeIn, Function<T, S> map) {
        return (SinglePath<S>) super.forkMap(excludeIn, map);
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
    public <S> SinglePath<S> forkUpdate(BinaryPredicate<S> excludeIn, BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.forkUpdate(excludeIn, update);
    }

    @Override
    public <S> SinglePath<S> forkUpdate(BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.forkUpdate(update);
    }

    @Override
    public <S> SinglePath<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new SinglePath<>(
                builderOperator,
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitch(BinaryPredicate<S> excludeIn, Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.forkSwitch(excludeIn, switchMap);
    }

    @Override
    public <S> SinglePath<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.forkSwitch(switchMap);
    }

    @Override
    public SinglePath<T> store(String TAG) {
        return (SinglePath<T>) super.store(TAG);
    }
}

