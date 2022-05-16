package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

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
            Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap
    ) {
        super(operator,
                selfMap
        );
    }

//    Path(
//            Function<Appointer<T>, AtomicBinaryEventConsumer> selfMap,
//            UnaryOperator<Builders.HolderBuilder<T>> operator
//    ) {
//        super(operator,
//                (UnaryOperator<Builders.ManagerBuilder>) managerBuilder -> managerBuilder.withFixedFun()
//        );
//    }

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
    public <S> Path<S> forkMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map) {
        return new Path<>(
                builderOperator,
                mapFunctionBuilder(map)
        );
    }

    @Override
    public <S> Path<S> forkMap(BinaryPredicate<S> constraintIn, Function<T, S> map) {
        return (Path<S>) super.forkMap(constraintIn, map);
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
    public <S> Path<S> forkUpdate(BinaryPredicate<S> constraintIn, BiFunction<S, T, S> update) {
        return (Path<S>) super.forkUpdate(constraintIn, update);
    }

    @Override
    public <S> Path<S> forkUpdate(BiFunction<S, T, S> update) {
        return (Path<S>) super.forkUpdate(update);
    }

    @Override
    public <S> Path<S> forkSwitch(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new Path<>(
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
    public <S> Path<S> forkSwitch(BinaryPredicate<S> constraintIn, Function<T, BasePath<S>> switchMap) {
        return (Path<S>) super.forkSwitch(constraintIn, switchMap);
    }

    @Override
    public <S> Path<S> forkSwitch(Function<T, BasePath<S>> switchMap) {
        return (Path<S>) super.forkSwitch(switchMap);
    }

}

