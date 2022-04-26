package sendero;

import sendero.event_registers.ConsumerRegisters;
import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class BasePath<T> extends Holders.ExecutorHolder<T> implements Forkable<T> {

    final Appointers.HolderAppointer<T> holderAppointer = new Appointers.HolderAppointer<>(holder);

    <S> BasePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder,
                dispatcher -> Builders.getManagerBuild().withFixed(
                        BinaryEventConsumers.producerHolderConnector(basePath, dispatcher::acceptVersionValue, map)

                ));
    }

    <S> BasePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> updateFun) {
        super(holderBuilder,
                dispatcher -> Builders.getManagerBuild().withFixed(
                        BinaryEventConsumers.producerHolderConnector(basePath, dispatcher, updateFun)

                ));
    }


    BasePath(boolean mutableActivationListener) {
        super(mutableActivationListener);
    }

    protected <S, P extends BasePath<S>> void setPath(P basePath, Function<S, T> map) {
        holderAppointer.setPathAndGet(basePath, map);
    }

    protected <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
        return holderAppointer.setAndStart(basePath, map);
    }

    protected <P extends BasePath<T>> T setAndStart(P basePath) {
        return holderAppointer.setAndStart(basePath);
    }

    protected void stopListeningPathAndUnregister() {
        holderAppointer.stopAndClearPath();
    }

    protected boolean startListeningPath() {
        return holderAppointer.start();
    }

    protected boolean stopListeningPath() {
        return holderAppointer.stop();
    }

    protected boolean pathIsActive() {
        return holderAppointer.isActive();
    }

    protected boolean pathIsSet() {
        return holderAppointer.isCleared();
    }

    public BasePath() {
    }

    BasePath(UnaryOperator<Builders.HolderBuilder<T>> operator, Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
        super(operator, selfMap);
    }

    BasePath(Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> selfMap) {
        super(UnaryOperator.identity(), selfMap);
    }

    BasePath(Builders.HolderBuilder<T> holderBuilder, Function<Holders.DispatcherHolder<T>, Builders.ManagerBuilder> actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    BasePath(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
    }

    BasePath(Builders.HolderBuilder<T> holderBuilder) {
        super(holderBuilder);
    }

    protected abstract void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);

    /**boolean continuation is:
     * <li>true if: is a dispatch product from the continuation of events in a forking path stream</li>
     * <li>false if: is a dispatch product from a client's input via update() or accept()</li>
     * */
    abstract void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t);

    // ------------------ Forking Functions ------------------

    protected abstract void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer);

    private <S> Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> mainForkingFunctionBuilder(Function<Consumer<Pair.Immutables.Int<S>>, Consumer<Pair.Immutables.Int<T>>> converter) {
        return intConsumer -> {
            final Consumer<Pair.Immutables.Int<T>> converted = converter.apply(intConsumer);
            return BinaryEventConsumers.fixedAppointer(this, converted);
//            return Appointer.booleanConsumerAppointer(this, converted);
        };
    }

    Function<Consumer<Pair.Immutables.Int<T>>, AtomicBinaryEventConsumer> injectiveFunctionBuilder() {
        return mainForkingFunctionBuilder(UnaryOperator.identity());
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> mapFunctionBuilder(Function<T, S> map) {
        return mainForkingFunctionBuilder(
                intConsumer ->
                        tInt -> intConsumer.accept(new Pair.Immutables.Int<>(tInt.getInt(), map.apply(tInt.getValue())))
        );
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> mutateFunctionBuilder(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return mainForkingFunctionBuilder(
                intConsumer ->
                        tInt -> {
                            T t = tInt.getValue();
                            int intT = tInt.getInt();
                            final Consumers.BaseConsumer<T> exitC = exit.apply(s -> intConsumer.accept(new Pair.Immutables.Int<>(intT, s)));
                            exitC.accept(t);
                        }
        );
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> switchFunctionBuilder(Function<T, BasePath<S>> switchMap) {
        return intConsumer -> {
            final Appointers.SimpleAppointer<S> appointer = new Appointers.SimpleAppointer<>(intConsumer,t -> true);
            final Consumer<Pair.Immutables.Int<T>> toAppoint = new Consumer<Pair.Immutables.Int<T>>() {
                final Holders.DispatcherHolder<BasePath<S>> domainHolder = new Holders.DispatcherHolder<BasePath<S>>() {
                    @Override
                    void coldDispatch(Pair.Immutables.Int<BasePath<S>> t) {
                        BasePath<S> toStart = t.getValue();
                        appointer.setAndStart(toStart);
                    }
                };
                {
                    domainHolder.expectIn(
                            sDomain -> sDomain != null && sDomain != domainHolder.get()
                    );
                }
                @Override
                public void accept(Pair.Immutables.Int<T> tInt) {
                    domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(tInt.getInt(), switchMap.apply(tInt.getValue())));
                }
            };
            return new AtomicBinaryEventConsumer() {
                @Override
                protected void onStateChange(boolean isActive) {
                    if (isActive) {
                        appointer.start();
                        appoint(toAppoint); // I give to appoint
                    }
                    else {
                        appointer.stop();
                        demotionOverride(toAppoint);
                    }
                }
            };
        };
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, AtomicBinaryEventConsumer> switchMutateFunctionBuilder(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return intConsumer -> {
            //Controls domain subscription
            final Appointers.SimpleAppointer<S> appointer = new Appointers.SimpleAppointer<>(intConsumer,t -> true);
            // Recepts the value to be transformed into an observable
            final Consumer<Pair.Immutables.Int<T>> toAppoint = new Consumer<Pair.Immutables.Int<T>>() {
                //Controls domain version
                final Holders.DispatcherHolder<BasePath<S>> domainHolder = new Holders.DispatcherHolder<BasePath<S>>() {
                    @Override
                    void coldDispatch(Pair.Immutables.Int<BasePath<S>> t) {
                        appointer.setAndStart(t.getValue());
                    }
                };
                {
                    domainHolder.expectIn(
                            sDomain -> sDomain != null && sDomain != domainHolder.get()
                    );
                }
                @Override
                public void accept(Pair.Immutables.Int<T> tInt) {
                    int intS = tInt.getInt();
                    T s = tInt.getValue();
                    final Consumer<T> transorfmed = mutate.apply(
                            tDomain -> domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(intS, tDomain))
                    );
                    transorfmed.accept(s);
                }
            };
            return new AtomicBinaryEventConsumer() {
                @Override
                protected void onStateChange(boolean isActive) {
                    if (isActive) {
                        appointer.start();
                        appoint(toAppoint);
                    }
                    else {
                        appointer.stop();
                        demotionOverride(toAppoint);
                    }
                }
            };
        };
    }

    static abstract class PathDispatcher<T> extends Dispatcher<T> implements IBasePath<T> {
        private final Holders.ExecutorHolder<T> executorHolder;

        void scheduleExecution(long delay, Runnable action) {
            executorHolder.scheduleExecution(delay, action);
        }

        <S> void parallelDispatch(int beginAt, Consumer<? super S>[] subs, Pair.Immutables.Int<T> t, Function<Pair.Immutables.Int<T>, S> map) {
            executorHolder.parallelDispatch(beginAt, subs, t, map);
        }

            abstract Pair.Immutables.Bool<Integer> onAddRegister(Consumer<Pair.Immutables.Int<T>> subscriber);
        abstract boolean onRegisterRemoved(Consumer<Pair.Immutables.Int<T>> intConsumer);

        @SuppressWarnings("unchecked")
        @Override
        public void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            executorHolder.onAdd(
                    subscriber,
                    consumer -> onAddRegister((Consumer<Pair.Immutables.Int<T>>) consumer),
                    UnaryOperator.identity()
            );
        }

        @Override
        public void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            if (onRegisterRemoved(intConsumer)) executorHolder.deactivate();
        }

        protected PathDispatcher(Holders.ExecutorHolder<T> executorHolder) {
            this.executorHolder = executorHolder;
        }

        abstract boolean isInactive();

        int getVersion(){
            return executorHolder.getVersion();
        }
    }

    static final class ToManyPathsDispatcher<T> extends PathDispatcher<T> {
        private final SimpleLists.LockFree<Consumer<Pair.Immutables.Int<T>>, Integer>
                remote;

        ToManyPathsDispatcher(Holders.ExecutorHolder<T> executorHolder) {
            super(executorHolder);
            remote = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);
        }

        @Override
        void dispatch(long delay, Pair.Immutables.Int<T> t) {
            //Last local observers on same thread
            scheduleExecution(
                        delay,
                        () -> pathDispatch(false, t)
                );
        }

        @Override
        void coldDispatch(Pair.Immutables.Int<T> t) {
            //From local observers on forked thread (if extended)
            pathDispatch(false, t);
        }

        @Override
        Pair.Immutables.Bool<Integer> onAddRegister(Consumer<Pair.Immutables.Int<T>> subscriber) {
            return remote.snapshotAdd(subscriber);
        }

        @Override
        boolean onRegisterRemoved(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            return remote.remove(intConsumer);
        }

        @Override
        public void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
            final Consumer<Pair.Immutables.Int<T>>[] subs = remote.copy();
            final int length = subs.length;
            if (length == 0) return;
            if (!fullyParallel) {
                if (length > 1) parallelDispatch(1, subs, t, UnaryOperator.identity());

                subs[0].accept(t); //Keep in thread
            } else {
                parallelDispatch(0, subs, t, UnaryOperator.identity());
            }

        }

        boolean isInactive() {
            return remote.isEmpty();
        }

    }

    static final class InjectivePathDispatcher<T> extends PathDispatcher<T> {

        private final ConsumerRegisters.IConsumerRegister.SnapshottingConsumerRegister<Integer, Pair.Immutables.Int<T>>
                remote;

        InjectivePathDispatcher(Holders.ExecutorHolder<T> executorHolder) {
            super(executorHolder);
            remote = ConsumerRegisters.IConsumerRegister.getInstance(this::getVersion);
        }

        @Override
        void dispatch(long delay, Pair.Immutables.Int<T> t) {
            pathDispatch(false, t);
        }

        @Override
        void coldDispatch(Pair.Immutables.Int<T> t) {
            pathDispatch(false, t);
        }

        @Override
        Pair.Immutables.Bool<Integer> onAddRegister(Consumer<Pair.Immutables.Int<T>> subscriber) {
            return remote.snapshotRegister(subscriber);
        }

        @Override
        public void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
            if (fullyParallel) throw new IllegalStateException("Injective.class dispatch cannot be parallel.");
        }

        @Override
        boolean onRegisterRemoved(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            return remote.unregister() != null;
        }

        boolean isInactive() {
            return !remote.isRegistered();
        }

    }

}

