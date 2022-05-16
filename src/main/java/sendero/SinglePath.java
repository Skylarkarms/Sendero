package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

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
        super(builderOperator, actMgmtBuilder);
    }

    SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> operator,
            Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap
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
    public <S> SinglePath<S> forkMap(BinaryPredicate<S> constraintIn, Function<T, S> map) {
        return (SinglePath<S>) super.forkMap(constraintIn, map);
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
    public <S> SinglePath<S> forkUpdate(BinaryPredicate<S> constraintIn, BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.forkUpdate(constraintIn, update);
    }

    @Override
    public <S> SinglePath<S> forkUpdate(BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.forkUpdate(update);
    }

    @Override
    public <S> SinglePath<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new SinglePath<>(
                builderOperator,
//                new Function<Holders.ColdHolder<S>, AtomicBinaryEventConsumer>() {
//                    final Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> function = switchFunctionBuilder(switchMap);
//                    @Override
//                    public AtomicBinaryEventConsumer apply(Holders.ColdHolder<S> holder) {
//                        return function.apply(holder::acceptVersionValue);
//                    }
//                }
                switchFunctionBuilder(switchMap)
        );
    }

    @Override
    public <S> SinglePath<S> forkSwitch(BinaryPredicate<S> constraintIn, Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.forkSwitch(constraintIn, switchMap);
    }

    @Override
    public <S> SinglePath<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.forkSwitch(switchMap);
    }
}

