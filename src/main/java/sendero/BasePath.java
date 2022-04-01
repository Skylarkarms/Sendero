package sendero;

import sendero.event_registers.ConsumerRegister;
import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.*;

public abstract class BasePath<T> extends Holders.ExecutorHolder<T> {

    final Appointers.HolderAppointer<T> holderAppointer = new Appointers.HolderAppointer<>(holder);

    <S> BasePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
        super(holderBuilder,
                dispatcher -> Builders.getManagerBuild().withFixed(
                        Appointers.Appointer.booleanConsumerAppointer(basePath, dispatcher::acceptVersionValue, map)

                ));
    }

    <S> BasePath(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
        super(holderBuilder,
                dispatcher -> Builders.getManagerBuild().withFixed(
                        Appointers.Appointer.booleanConsumerAppointer(basePath, dispatcher, map)

                ));
    }


    BasePath(boolean mutableActivationListener) {
        super(mutableActivationListener);
    }

    protected <S, P extends BasePath<S>> void listen(P basePath, Function<S, T> map) {
        holderAppointer.setAndStart(basePath, map);
    }

    protected <P extends BasePath<T>> void listen(P basePath) {
        holderAppointer.setAndStart(basePath);
    }

    protected void stopListeningPath() {
        holderAppointer.stopAndClearPath();
    }

    public BasePath() {
    }

    BasePath(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
        super(selfMap);
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

    private <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> mainForkingFunctionBuilder(Function<Consumer<Pair.Immutables.Int<S>>, Consumer<Pair.Immutables.Int<T>>> converter) {
        return intConsumer -> {
            final Consumer<Pair.Immutables.Int<T>> converted = converter.apply(intConsumer);
            return Appointers.Appointer.booleanConsumerAppointer(this, converted);
        };
    }

    protected Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> injectiveFunctionBuilder() {
        return mainForkingFunctionBuilder(UnaryOperator.identity());
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> mapFunctionBuilder(Function<T, S> map) {
        return mainForkingFunctionBuilder(
                intConsumer ->
                        tInt -> intConsumer.accept(new Pair.Immutables.Int<>(tInt.getInt(), map.apply(tInt.getValue())))
        );
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> mutateFunctionBuilder(Function<Consumer<? super S>, ? extends Consumers.BaseConsumer<T>> exit) {
        return mainForkingFunctionBuilder(
                intConsumer ->
                        tInt -> {
                            T t = tInt.getValue();
                            int intT = tInt.getInt();
                            final Consumers.BaseConsumer<T> exitC = exit.apply(s -> intConsumer.accept(new Pair.Immutables.Int<S>(intT, s)));
                            exitC.accept(t);
                        }
        );
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> switchFunctionBuilder(Function<T, BasePath<S>> switchMap) {
        return intConsumer -> {
            final Appointers.SimpleAppointer<S> appointer = new Appointers.SimpleAppointer<>(intConsumer,t -> true);
            final Consumer<Pair.Immutables.Int<T>> toAppoint = new Consumer<Pair.Immutables.Int<T>>() {
                final Holders.DispatcherHolder<BasePath<S>> domainHolder = new Holders.DispatcherHolder<BasePath<S>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<S>> t) {
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
                    domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(tInt.getInt(), switchMap.apply(tInt.getValue())));
                }
            };
            return isActive -> {
                if (isActive) {
                    appointer.start();
                    appoint(toAppoint); // I give to appoint
                }
                else {
                    appointer.stop();
                    demotionOverride(toAppoint);
                }
            };
        };
    }

    <S> Function<Consumer<Pair.Immutables.Int<S>>, BooleanConsumer> switchMutateFunctionBuilder(Function<Consumer<? super BasePath<S>>, ? extends Consumers.BaseConsumer<T>> mutate) {
        return intConsumer -> {
            //Controls domain subscription
            final Appointers.SimpleAppointer<S> appointer = new Appointers.SimpleAppointer<>(intConsumer,t -> true);
            // Recepts the value to be transformed into an observable
            final Consumer<Pair.Immutables.Int<T>> toAppoint = new Consumer<Pair.Immutables.Int<T>>() {
                //Controls domain version
                final Holders.DispatcherHolder<BasePath<S>> domainHolder = new Holders.DispatcherHolder<BasePath<S>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<S>> t) {
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
            return isActive -> {
                if (isActive) {
                    appointer.start();
                    appoint(toAppoint);
                }
                else {
                    appointer.stop();
                    demotionOverride(toAppoint);
                }
            };
        };
    }

    static class Injective<T> extends BasePath<T> {

        private final ConsumerRegister.IConsumerRegister.SnapshottingConsumerRegister<Integer, Pair.Immutables.Int<T>>
                remote = ConsumerRegister.IConsumerRegister.getInstance(this::getVersion);

        protected Injective(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

//        protected<S> Injective(Supplier<BasePath<S>> basePathSupplier, Function<Consumer<Pair.Immutables.Int<T>>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
//            super(
//                    dispatcher -> Appointers.Appointer.booleanConsumerAppointer(basePathSupplier.get(), toAppointFun.apply(dispatcher))
//            );
//        }

        <S> Injective(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
            super(holderBuilder, basePath, map);
        }

        <S> Injective(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
            super(holderBuilder, basePath, map);
        }

        <S> Injective(Builders.HolderBuilder<T> holderBuilder, Supplier<BasePath<S>> basePathSupplier, Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
            super(holderBuilder,
                    dispatcher -> Builders.getManagerBuild().withFixed(
                            Appointers.Appointer.booleanConsumerAppointer(basePathSupplier.get(), toAppointFun.apply(dispatcher))
                    )
            );
        }

        @Override
        protected void dispatch(long delay, Pair.Immutables.Int<T> t) {
            pathDispatch(false, t);
        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            pathDispatch(false, t);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            onAdd(
                    subscriber,
                    consumer -> remote.snapshotRegister((Consumer<Pair.Immutables.Int<T>>) consumer),
                    UnaryOperator.identity()
            );
        }

        @Override
        void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
            if (fullyParallel) throw new IllegalStateException("Injective.class dispatch cannot be parallel.");
        }

        @Override
        protected void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            if (remote.unregister() != null) deactivate();
        }

        @Override
        protected boolean deactivationRequirements() {
            return !remote.isRegistered();
        }

    }

    static class ToMany<T> extends BasePath<T> {
        private final SimpleLists.SimpleList.LockFree.Snapshotting<Consumer<Pair.Immutables.Int<T>>, Integer>
                remote = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);

        ToMany(boolean activationListener) {
            super(activationListener);
        }

        ToMany() {
            super();
        }

        ToMany(Builders.HolderBuilder<T> holderBuilder) {
            super(holderBuilder);
        }

        ToMany(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        <S> ToMany(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, BiFunction<T, S, T> map) {
            super(holderBuilder, basePath, map);
        }

        ToMany(Builders.HolderBuilder<T> holderBuilder, Builders.ManagerBuilder actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        <S> ToMany(Builders.HolderBuilder<T> holderBuilder, BasePath<S> basePath, Function<S, T> map) {
            super(holderBuilder, basePath, map);
        }

        @Override
        protected void dispatch(long delay, Pair.Immutables.Int<T> t) {
            //Last local observers on same thread
                scheduleExecution(
                        delay,
                        () -> pathDispatch(false, t)
                );
        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            //From local observers on forked thread (if extended)
            pathDispatch(false, t);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            onAdd(
                    subscriber,
                    consumer -> remote.add((Consumer<Pair.Immutables.Int<T>>) consumer),
                    UnaryOperator.identity()
            );
        }

        @Override
        void pathDispatch(boolean fullyParallel, Pair.Immutables.Int<T> t) {
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

        @Override
        protected void demotionOverride(Consumer<Pair.Immutables.Int<T>> intConsumer) {
            if (remote.remove(intConsumer)) deactivate();
        }

        @Override
        protected boolean deactivationRequirements() {
            return remote.isEmpty();
        }

    }
}

