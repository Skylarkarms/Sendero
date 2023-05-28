package sendero;

import sendero.functions.Functions;
import sendero.interfaces.BinaryPredicate;
import sendero.interfaces.BooleanConsumer;
import sendero.interfaces.ConsumerUpdater;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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
        this(Functions.myIdentity(),
                mngrBuilder
        );
    }

    public static<T> SinglePath<T> getSinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Function<ConsumerUpdater<T>, BooleanConsumer> activationsFun
    ) {
       return new SinglePath<>(builderOperator, activationsFun);
    }

    public static<T> SinglePath<T> getSinglePath(
            BinaryPredicate<T> excludeIn,
            Function<ConsumerUpdater<T>, BooleanConsumer> activationsFun
    ) {
       return new SinglePath<>(Builders.excludeIn(excludeIn), activationsFun);
    }

    public static<T> SinglePath<T> getSinglePath(
            Function<ConsumerUpdater<T>, BooleanConsumer> activationsFun
    ) {
       return new SinglePath<>(Functions.myIdentity(), activationsFun);
    }

    protected SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Function<ConsumerUpdater<T>, BooleanConsumer> activationsFun
    ) {
        super(builderOperator, activationsFun);
    }

    protected SinglePath(
            UnaryOperator<Builders.HolderBuilder<T>> builderOperator,
            Builders.ManagerBuilder actMgmtBuilder
    ) {
        super(builderOperator, actMgmtBuilder);
    }

    SinglePath(
            Function<Holders.StreamManager<T>, AtomicBinaryEvent> selfMap,
            UnaryOperator<Builders.HolderBuilder<T>> operator
    ) {
        super(
                selfMap,
                operator
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
    public <S> SinglePath<S> map(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, S> map) {
        return new SinglePath<>(
                mapFunctionBuilder(map),
                builderOperator
        );
    }

    @Override
    public <S> SinglePath<S> map(BinaryPredicate<S> excludeIn, Function<T, S> map) {
        return (SinglePath<S>) super.map(excludeIn, map);
    }

    @Override
    public <S> SinglePath<S> map(Function<T, S> map) {
        return (SinglePath<S>) super.map(map);
    }

    @Override
    public SinglePath<T> fork(BinaryPredicate<T> excludeIn) {
        return (SinglePath<T>) super.fork(excludeIn);
    }

    @Override
    public <S> SinglePath<S> update(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, BiFunction<S, T, S> update) {
        return new SinglePath<>(
                builderOperator,
                this,
                update
        );
    }

    @Override
    public <S> SinglePath<S> update(BinaryPredicate<S> excludeIn, BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.update(excludeIn, update);
    }

    @Override
    public <S> SinglePath<S> update(BiFunction<S, T, S> update) {
        return (SinglePath<S>) super.update(update);
    }

    @Override
    public <S> SinglePath<S> switchMap(UnaryOperator<Builders.HolderBuilder<S>> builderOperator, Function<T, BasePath<S>> switchMap) {
        return new SinglePath<>(
                switchFunctionBuilder(switchMap),
                builderOperator
        );
    }

    @Override
    public <S> SinglePath<S> switchMap(BinaryPredicate<S> excludeIn, Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.switchMap(excludeIn, switchMap);
    }

    @Override
    public <S> SinglePath<S> switchMap(Function<T, BasePath<S>> switchMap) {
        return (SinglePath<S>) super.switchMap(switchMap);
    }

    @Override
    public SinglePath<T> store(String TAG) {
        return (SinglePath<T>) super.store(TAG);
    }

    private static final BasePath<?> DEFAULT = new SinglePath<Object>(){
        private void throwException() {
            throw new IllegalStateException("Action not allowed");
        }
        @Override
        protected void onSwapped(SourceType type, Object prev, Object next) {
            throwException();
            super.onSwapped(type, prev, next);
        }

        @Override
        <S> Function<Holders.StreamManager<S>, AtomicBinaryEvent> mapFunctionBuilder(Function<Object, S> map) {
            throwException();
            return super.mapFunctionBuilder(map);
        }

        @Override
        <S> Function<Holders.StreamManager<S>, AtomicBinaryEvent> switchFunctionBuilder(Function<Object, BasePath<S>> switchMap) {
            throwException();
            return super.switchFunctionBuilder(switchMap);
        }

        @Override
        public <S, O extends Gate.Out<S>> O out(Class<? super O> outputType, Function<Object, S> map) {
            throwException();
            return super.out(outputType, map);
        }
    };

    @SuppressWarnings("unchecked")
    public static<T> BasePath<T> getDefault() {
        return (BasePath<T>) DEFAULT;
    }

    @Override
    public boolean isDefault() {
        return this == DEFAULT;
    }
}

