package sendero;

import sendero.atomics.AtomicUtils;
import sendero.event_registers.BinaryEventRegisters;
import sendero.event_registers.ConsumerRegister;
import sendero.interfaces.BooleanConsumer;
import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public abstract class BasePath<T> extends Holders.ExecutorHolder<T> {

    //Todo: Does not need to be a path
    private final SelfAppointer<T> pathListener = new SelfAppointer<>(this);

    //Todo: this needs to ne pushed downstream
    private final ActivePathListener<T> absBaseLinkManager;
    private final boolean mutableManager;
    private void throwException() {
        throw new IllegalStateException(
                "Manager must be defined as mutable == true." +
                        "\n By calling constructors:  " +
                        "\n BasePath(boolean mutableActivationListener) set true, " +
                        "\n Or ActivationManager.Builder withMutable(boolean activationListener) set true");
    }
    private ActivePathListener<T> build(ActivationManager manager) {
        return mutableManager ? new ActivePathListener<T>(manager, pathListener) {
            @Override
            void acceptVersionValue(Pair.Immutables.Int<T> versionValue) {
                BasePath.this.acceptVersionValue(versionValue);
            }
        } : null;
    }

    public BasePath(boolean mutableActivationListener) {
        super(mutableActivationListener);
        mutableManager = mutableActivationListener;
        absBaseLinkManager = build(manager);
    }
//    /**Disconnect by unregistering activationListener
//     * this method is unable to infer repeating connections to same basePath*/
//    protected<S> void connect(BasePath<S> other, Function<S, T> map) {
//        setActivationListener(activationListenerCreator(() -> other, sInt -> acceptVersionValue(new Pair.Immutables.Int<>(sInt.getInt(), map.apply(sInt.getValue())))));
//    }
    static class Appointer<A> {
        public void setAppointerVersion(int appointerVersion) {
            this.appointerVersion = appointerVersion;
        }

        int appointerVersion;
        final BasePath<A> producer;
        final Consumer<Pair.Immutables.Int<A>> toAppoint;

        Appointer(int appointerVersion, BasePath<A> producer, Consumer<Pair.Immutables.Int<A>> toAppoint) {
            this.appointerVersion = appointerVersion;
            this.producer = producer;
            this.toAppoint = toAppoint;
        }

        Appointer(BasePath<A> producer, Consumer<Pair.Immutables.Int<A>> toAppoint) {
            this.producer = producer;
            this.toAppoint = toAppoint;
        }

        void appoint() {
            producer.appoint(toAppoint);
        }
        void demote() {
            if (producer instanceof ToMany) {
                ((ToMany<A>) producer).demote(toAppoint);
            } else {
                assert producer instanceof Injective;
                ((Injective<A>) producer).demote();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Appointer<?> appointer = (Appointer<?>) o;
            return Objects.equals(producer, appointer.producer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(producer);
        }
    }
    static class SelfAppointer<T> implements PathListener<T> {
        private final AtomicUtils.TaggedAtomicReference<BasePath<?>, Appointer<?>> basePathAtomicReference;
        private final Holders.ActivationHolder<T> self;

        private SelfAppointer(Holders.ActivationHolder<T> self) {
            this.self = self;
            basePathAtomicReference = new AtomicUtils.TaggedAtomicReference<>();
        }

        private SelfAppointer(Holders.ActivationHolder<T> self, Appointer<T> appointer) {
            this.self = self;
            this.basePathAtomicReference = new AtomicUtils.TaggedAtomicReference<>(appointer);
        }



        //Returns same Appointer if same basePath
        private final AtomicInteger appointVersion = new AtomicInteger();
        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPath(P basePath, Function<S, T> map) {
            final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> self.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
//            final Appointer<?> next = new Appointer<>(basePath, intConsumer);
            //            boolean prevNotNull = prev != null;
//            if (next != prevOrNext) {
////                if (prevNotNull) prev.demote();//Do not demote, let BooleanCosumer take care of demotion.
//                return next;
//            }
            return basePathAtomicReference.diffUpdateAndGet(basePath, () -> new Appointer<>(appointVersion.incrementAndGet(), basePath, intConsumer));
        }

        @Override
        public <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map) {
            final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> self.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
            final Appointer<?> next = new Appointer<>(basePath, intConsumer);
            final Appointer<?> prev = basePathAtomicReference.getAndDiffUpdate(basePath, next);
            if (next != prev) {
                boolean prevNotNull = prev != null;
                if (prevNotNull) prev.demote();
                //contention check
                if (basePathAtomicReference.contains(basePath)) {
                    if (prevNotNull) self.invalidate();
                    next.appoint();
                }
            }
        }

//        @Override
//        public  <S, P extends BasePath<S>> void listen(P basePath, Function<S, T> map) {
//            final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> self.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
//            final Appointer<?> next = new Appointer<>(basePath, intConsumer);
//            final Appointer<?> prev = basePathAtomicReference.getAndDiffUpdate(basePath, next);
//            if (next != prev) {
//                boolean prevNotNull = prev != null;
//                if (prevNotNull) prev.demote();
//                //contention check
//                if (basePathAtomicReference.contains(basePath)) {
//                    if (prevNotNull) self.invalidate();
//                    next.appoint();
//                }
//            }
//        }

        @Override
        public <P extends BasePath<T>> void setAndStart(P basePath) {
            setAndStart(basePath, UnaryOperator.identity());
        }

        @Override
        public void stopPath() {
            Appointer<?> appointer = basePathAtomicReference.get();
            if (appointer != null) appointer.demote();
        }

        @Override
        public void startPath() {
            Appointer<?> appointer = basePathAtomicReference.get();
            if (appointer != null) appointer.appoint();
        }

        @Override
        public void stopAndClearPath() {
            Appointer<?> appointer = basePathAtomicReference.getAndClear();
            if (appointer != null) {
                appointer.demote();
                //contention check
//                if (basePathAtomicReference.isCleared()) self.invalidate();
            }
        }

        @Override
        public void clearAndDemote(Appointer<?> expect) {
            if (basePathAtomicReference.expectAndClear(expect)) {
                expect.demote();
            }
        }

        @Override
        public void clear(Appointer<?> expect) {
            basePathAtomicReference.expectAndClear(expect);
        }

        @Override
        public Appointer<?> getAndClear() {
            return basePathAtomicReference.getAndClear();
        }
    }

    private void pathIsBoundWarning() {
        System.err.println("Path was bound, binding dropped!");
    }

    protected <S, P extends BasePath<S>> void listen(P basePath, Function<S, T> map) {
        pathListener.setAndStart(basePath, map);
        if (unbond()) pathIsBoundWarning();
    }

    protected <P extends BasePath<T>> void listen(P basePath) {
        pathListener.setAndStart(basePath);
        if (unbond()) pathIsBoundWarning();
    }

    protected void stopListeningPath() {
        pathListener.stopAndClearPath();
    }

    protected  <P extends BasePath<T>> void bind(P basePath) {
        if (mutableManager) absBaseLinkManager.bind(basePath);
        else throwException();
    }

    protected  <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
        if (mutableManager) absBaseLinkManager.bindMap(basePath, map);
        else throwException();
    }

    protected boolean isBound() {
        return absBaseLinkManager.isBound();
    }

    protected boolean unbond() {
        return absBaseLinkManager.unbound();
    }

    @Override
    protected void setActivationListener(BooleanConsumer activationListener) {
        if (mutableManager) absBaseLinkManager.forcedSet(activationListener);
        else super.setActivationListener(activationListener);
    }

    public BasePath() {
        mutableManager = false;
        absBaseLinkManager = null;
    }

    protected BasePath(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
        super(selfMap);
        mutableManager = false;
        absBaseLinkManager = null;
    }

    BasePath(Builders.HolderBuilder<T> holderBuilder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
        mutableManager = manager.isMutable();
        absBaseLinkManager = build(manager);
    }

    protected BasePath(Builders.HolderBuilder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
        super(holderBuilder, actMgmtBuilder);
        mutableManager = manager.isMutable();
        absBaseLinkManager = build(manager);
    }

    static<S> BooleanConsumer activationListenerCreator(
            Supplier<BasePath<S>> basePathSupplier,
            Consumer<Pair.Immutables.Int<S>> toAppoint
    ) {
        return new BooleanConsumer() {
            //The supplier is for the Client to be allowed to  create a path at super()
            final BasePath<S> basePath = basePathSupplier.get();
            @Override
            public void accept(boolean isActive) {
                if (isActive) basePath.appoint(toAppoint);
                else if (basePath instanceof ToMany) {
                    ((ToMany<S>) basePath).demote(toAppoint);
                } else if (basePath instanceof Injective) {
                    ((Injective<S>) basePath).demote();
                }
            }
        };
    }

    protected abstract void appoint(Consumer<Pair.Immutables.Int<T>> subscriber);

    static class Injective<T> extends BasePath<T> {

        private final ConsumerRegister.IConsumerRegister.SnapshottingConsumerRegister<Integer, Pair.Immutables.Int<T>>
                remote = ConsumerRegister.IConsumerRegister.getInstance(this::getVersion);

        protected Injective(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        protected<S> Injective(Supplier<BasePath<S>> basePathSupplier, Function<Consumer<Pair.Immutables.Int<T>>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
            super(
                    dispatcher -> activationListenerCreator(basePathSupplier, toAppointFun.apply(dispatcher))
            );
        }

        Injective(Builders.HolderBuilder<T> holderBuilder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        <S> Injective(Builders.HolderBuilder<T> holderBuilder, Supplier<BasePath<S>> basePathSupplier, Function<Holders.DispatcherHolder<T>, Consumer<Pair.Immutables.Int<S>>> toAppointFun) {
            super(holderBuilder,
                    dispatcher -> ActivationManager.getBuilder().withFixed(BasePath.activationListenerCreator(basePathSupplier, toAppointFun.apply(dispatcher)))
            );
        }


        @Override
        protected void dispatch(Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            if (remote.isRegistered()) {
                if (t.compareTo(getVersion()) != 0) return;
                remote.accept(t);
            }
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
        protected boolean deactivationRequirements() {
            return !remote.isRegistered();
        }

        protected void demote() {
            if (remote.unregister() != null) deactivate();
        }

    }

    static class ToMany<T> extends BasePath<T> {
        private final SimpleLists.SimpleList.LockFree.Snapshotting<Consumer<Pair.Immutables.Int<T>>, Integer>
                remote = SimpleLists.getSnapshotting(Consumer.class, this::getVersion);

        public ToMany(boolean activationListener) {
            super(activationListener);
        }

        public ToMany() {
            super();
        }

        protected ToMany(Function<Consumer<Pair.Immutables.Int<T>>, BooleanConsumer> selfMap) {
            super(selfMap);
        }

        ToMany(Builders.HolderBuilder<T> holderBuilder, Function<Holders.DispatcherHolder<T>, ActivationManager.Builder> actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        protected ToMany(Builders.HolderBuilder<T> holderBuilder, ActivationManager.Builder actMgmtBuilder) {
            super(holderBuilder, actMgmtBuilder);
        }

        @Override
        protected void dispatch(Pair.Immutables.Int<T> t) {
            //Last local observers on same thread
            dispatchRemotes(false, t);

        }

        @Override
        protected void coldDispatch(Pair.Immutables.Int<T> t) {
            //From local observers on forked thread (if extended)
            dispatchRemotes(false, t);
        }

        protected void dispatchRemotes(boolean fullyParallel, Pair.Immutables.Int<T> t) {
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
        @SuppressWarnings("unchecked")
        protected void appoint(Consumer<Pair.Immutables.Int<T>> subscriber) {
            onAdd(
                    subscriber,
                    consumer -> remote.add((Consumer<Pair.Immutables.Int<T>>) consumer),
                    UnaryOperator.identity()
            );
        }

        @Override
        protected boolean deactivationRequirements() {
            return remote.isEmpty();
        }

        protected void demote(Consumer<Pair.Immutables.Int<T>> subscriber) {
            if (remote.remove(subscriber)) {
                deactivate();
            }
        }

    }
}

