package sendero;

import sendero.atomics.AtomicUtils;
import sendero.pairs.Pair;
import sendero.switchers.Switchers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.isIdentity;
import static sendero.functions.Functions.myIdentity;

public class Appointers {
    static class AppointerSwapCore<T> implements Switchers.Switch {
        /**Accessed via side effects, ignore warnings*/
        final AtomicReference<Appointer<?>> witnessAtomicReference;
        final Holders.ColdHolder<T> holder;

        AppointerSwapCore(Holders.ColdHolder<T> holder) {
            this(holder, Appointer.CLEARED_APPOINTER);
        }

        AppointerSwapCore(Holders.ColdHolder<T> holder, Appointer<?> fixedAppointer) {
            this.witnessAtomicReference = new AtomicReference<>(fixedAppointer);
            this.holder = holder;
        }

        private Appointer<?> get() {
            return witnessAtomicReference.get();
        }

        @Override
        public boolean on() {
            return get().on();
        }

        @Override
        public boolean isActive() {
           return get().isActive();
        }

        @Override
        public boolean off() {
            return get().off();
        }

        void accept(Pair.Immutables.Int<T> pair) {
            holder.accept(pair);
        }

        T getAndInvalidate() {
            return holder.getAndInvalidate();
        }
    }

    interface BasePathListener<T> extends Switchers.Switch {
        /**@return last holder value, shutsDown previous*/
        default <P extends BasePath<T>> T setAndStart(P basePath) {
            return setAndStart(basePath, myIdentity());
        }
        /**@return last holder value, shutsDown previous*/
        <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map);
    }

    static class BasePathListenerImpl<T> implements BasePathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;

        BasePathListenerImpl(Holders.ColdHolder<T> holder) {
            appointerSwapCore = new AppointerSwapCore<>(holder);
        }

        @Override
        public <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
            assert basePath != null;
            T lastValue = null;
            final AtomicReference<Appointer<?>> witnessAtomicReference = appointerSwapCore.witnessAtomicReference;
            final AtomicUtils.Witness<Appointer<?>> witness = AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> prev == Appointer.CLEARED_APPOINTER || !prev.equalTo(basePath) || !isIdentity(map), // always update if map is NOT identity
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt ->
                                appointerSwapCore.accept(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue())));
                        return new Appointer<>(basePath, intConsumer);
                    }
            );
            final Appointer<?> prev = witness.prev, next = witness.next;
            if (next != null && prev != next) {
                prev.shutDown();
                //contention check
                if (witnessAtomicReference.get().equalTo(basePath)) {
                    if (!prev.isCleared()) lastValue = appointerSwapCore.getAndInvalidate();
                    next.start();
                }
            }
            return lastValue;
        }

        @Override
        public boolean on() {
            return appointerSwapCore.on();
        }

        @Override
        public boolean off() {
            return appointerSwapCore.off();
        }

        @Override
        public boolean isActive() {
            return appointerSwapCore.isActive();
        }
    }

    interface UnboundPathListener<T> {
        /**@return previous Path OR null under contention*/
        <S, P extends BasePath<S>> Appointer<S> setPathAndGet(P basePath, Function<S, T> map);
        default Appointer<T> setPathAndGet(BasePath<T> basePath) {
            return setPathAndGet(basePath, myIdentity());
        }
        <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update);
        Appointer<?> getAndClear();
    }

    static class UnboundPathListenerImpl<T> implements UnboundPathListener<T> {

        final AppointerSwapCore<T> appointerSwapCore;

        UnboundPathListenerImpl(AppointerSwapCore<T> appointerSwapCore) {
            this.appointerSwapCore = appointerSwapCore;
        }

        UnboundPathListenerImpl(Holders.ColdHolder<T> coldHolder) {
            this.appointerSwapCore = new AppointerSwapCore<>(coldHolder);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathAndGet(P basePath, Function<S, T> map) {
            return (Appointer<S>) AtomicUtils.contentiousCAS(
                    appointerSwapCore.witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    new UnaryOperator<Appointer<?>>() {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer =
                                !isIdentity(map) ?
                                        anInt -> appointerSwapCore.accept(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue())))
                                        :
                                        sInt -> appointerSwapCore.accept((Pair.Immutables.Int<T>) sInt);
                        @Override
                        public Appointer<?> apply(Appointer<?> appointer) {
                            return new Appointer<>(basePath, intConsumer);
                        }
                    }
            ).next;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return (Appointer<S>) AtomicUtils.contentiousCAS(
                    appointerSwapCore.witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt ->
                                appointerSwapCore.accept(new Pair.Immutables.Int<>(anInt.getInt(),
                                                update.apply(getColdHolder().get(), anInt.getValue()))
                                );
                        return new Appointer<>(basePath, intConsumer);
                    }
            ).next;
        }

        @Override
        public Appointer<?> getAndClear() {
            return appointerSwapCore.witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER);
        }

        Holders.ColdHolder<T> getColdHolder() {
            return appointerSwapCore.holder;
        }
    }

    /**Used by Path, this one is for client usage, and can emulate the behaviour of Unbound Link classes*/
    public interface PathListener<T> extends BasePathListener<T>, UnboundPathListener<T> {
        void stopAndClearPath();

        boolean isActive();
        boolean isCleared();

        static<T> PathListener<T> getInstance(BasePath<T> basePath) {
            return new PathListenerImpl<>(basePath.baseTestDispatcher);
        }
    }

    static class PathListenerImpl<T> extends BasePathListenerImpl<T> implements PathListener<T> {

        final UnboundPathListenerImpl<T> unboundPathListener;

        PathListenerImpl(Holders.ColdHolder<T> holder) {
            super(holder);
            unboundPathListener = new UnboundPathListenerImpl<>(appointerSwapCore);
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathAndGet(P basePath, Function<S, T> map) {
            return unboundPathListener.setPathAndGet(basePath, map);
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return unboundPathListener.setPathUpdateAndGet(basePath, update);
        }

        @Override
        public void stopAndClearPath() {
            Appointer<?> appointer = appointerSwapCore.witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER);
            appointer.shutDown();
        }

        @Override
        public boolean isActive() {
            return appointerSwapCore.witnessAtomicReference.get().isActive();
        }

        @Override
        public boolean isCleared() {
            return appointerSwapCore.witnessAtomicReference.get().isCleared();
        }

        /** If next == null, prev was already cleared
         * @return next appointer*/
        @Override
        public Appointer<?> getAndClear() {
            return unboundPathListener.getAndClear();
        }
    }
}
