package sendero;

import sendero.atomics.AtomicUtils;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class Appointers {
    interface PathListener<T> {
        /**@return previous Path OR null under contention*/
        <S, P extends BasePath<S>> Appointer<?> setPathAndGet(P basePath, Function<S, T> map);
        <S, P extends BasePath<S>> Appointer<?> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update);
        /**@return last holder value*/
        <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map);
        <P extends BasePath<T>> T setAndStart(P basePath);
        void stopAndClearPath();

        boolean start();
        boolean stop();
        boolean isActive();
        boolean isCleared();

        Appointer<?> getAppointer();

        Appointer<?> getAndClear();
    }

    final static class SimpleAppointer<T> extends Holders.SingleColdHolder<T> implements PathListener<T> {

        private final HolderAppointer<T> holderAppointer = new HolderAppointer<>(this);

        SimpleAppointer(
                Holders.ColdHolder<T> dispatcher,
                BinaryPredicate<T> expect
        ) {
            super(dispatcher, expect);
        }


        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPathAndGet(P basePath, Function<S, T> map) {
            return holderAppointer.setPathAndGet(basePath, map);
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return holderAppointer.setPathUpdateAndGet(basePath, update);
        }

        @Override
        public <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
            return holderAppointer.setAndStart(basePath, map);
        }

        @Override
        public <P extends BasePath<T>> T setAndStart(P basePath) {
            return holderAppointer.setAndStart(basePath);
        }

        @Override
        public void stopAndClearPath() {
            holderAppointer.stopAndClearPath();
        }

        @Override
        public boolean start() {
            return holderAppointer.start();
        }

        @Override
        public boolean stop() {
            return holderAppointer.stop();
        }

        @Override
        public boolean isActive() {
            return holderAppointer.isActive();
        }

        @Override
        public boolean isCleared() {
            return holderAppointer.isCleared();
        }

        @Override
        public Appointer<?> getAppointer() {
            return holderAppointer.getAppointer();
        }

        @Override
        public Appointer<?> getAndClear() {
            return holderAppointer.getAndClear();
        }
    }

    static class HolderAppointer<T> implements PathListener<T> {
        private final AtomicReference<Appointer<?>> witnessAtomicReference;
        private final Holders.ColdHolder<T> holder;

        Holders.ColdHolder<T> getColdHolder() {
            return holder;
        }

        HolderAppointer(Holders.ColdHolder<T> holder) {
            this.holder = holder;
            witnessAtomicReference = new AtomicReference<>(Appointer.CLEARED_APPOINTER);
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPathAndGet(P basePath, Function<S, T> map) {
            return AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue())));
                        return new Appointer<>(basePath, intConsumer);
                    }
            ).next;
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(),
                                update.apply(getColdHolder().get(), anInt.getValue()))
                        );
                        return new Appointer<>(basePath, intConsumer);
                    }
            ).next;
        }

        @Override
        public <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
            assert basePath != null;
            T lastValue = null;
            final AtomicUtils.Witness<Appointer<?>> witness = AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> prev == Appointer.CLEARED_APPOINTER || !prev.equalTo(basePath) || map != identity, // always update if map is NOT identity
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt ->
                                holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue())));
                        return new Appointer<>(basePath, intConsumer);
                    }
            );
            final Appointer<?> prev = witness.prev, next = witness.next;
            if (next != null && prev != next) {
                prev.shutDown();
                //contention check
                if (witnessAtomicReference.get().equalTo(basePath)) {
                    if (!prev.isCleared()) lastValue = holder.getAndInvalidate();
                    next.start();
                }
            }
            return lastValue;
        }

        private final UnaryOperator<T> identity = UnaryOperator.identity();

        @Override
        public <P extends BasePath<T>> T setAndStart(P basePath) {
            return setAndStart(basePath, identity);
        }

        @Override
        public void stopAndClearPath() {
            Appointer<?> appointer = witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER);
            appointer.shutDown();
        }

        @Override
        public boolean start() {
            return witnessAtomicReference.get().on();
        }

        @Override
        public boolean stop() {
            return witnessAtomicReference.get().off();
        }

        @Override
        public boolean isActive() {
            return witnessAtomicReference.get().isActive();
        }

        @Override
        public boolean isCleared() {
            return witnessAtomicReference.get().isCleared();
        }

        @Override
        public Appointer<?> getAppointer() {
            return witnessAtomicReference.get();
        }
        /** If next == null, prev was already cleared
         * @return next appointer*/
        @Override
        public Appointer<?> getAndClear() {
            return witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER);
        }
    }
}
