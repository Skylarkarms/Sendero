package sendero;

import sendero.atomics.AtomicUtils;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sendero.functions.Functions.IDENTITY;
import static sendero.functions.Functions.myIdentity;

public class Appointers {
    interface SysPathListener<T> {
        /**@return last holder value*/
        <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map);
        default <P extends BasePath<T>> T setAndStart(P basePath) {
            return setAndStart(basePath, myIdentity());
        }

        boolean start();
        boolean stop();
    }

    interface PathListener<T> extends SysPathListener<T>{
        /**@return previous Path OR null under contention*/
        default Appointer<T> setPathAndGet(BasePath<T> basePath) {
            return setPathAndGet(basePath, myIdentity());
        }
        <S, P extends BasePath<S>> Appointer<S> setPathAndGet(P basePath, Function<S, T> map);
        <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update);
//        <S, P extends BasePath<S>> Appointer<?> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update);
//        /**@return last holder value*/
//        <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map);
//        <P extends BasePath<T>> T setAndStart(P basePath);
        void stopAndClearPath();

//        boolean start();
//        boolean stop();
        boolean isActive();
        boolean isCleared();

        Appointer<?> getAppointer();

        Appointer<?> getAndClear();
    }

    /*BasePath:
    * setAndStart, start, stop
    *
    * BaseUnboundSwitch A & B:
    * setAndStart, start, stop*/
    final static class SimpleAppointer<T> extends Holders.SingleColdHolder<T> implements SysPathListener<T> {

        private final SysPathListener<T> holderAppointer = new BaseHolderAppointer<>(this);

        SimpleAppointer(
                Consumer<Pair.Immutables.Int<T>> dispatcher
//                Consumer<Pair.Immutables.Int<T>> dispatcher,
//                BinaryPredicate<T> expect
        ) {
            super(dispatcher);
//            super(dispatcher, expect);
        }


        @Override
        public <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
            return holderAppointer.setAndStart(basePath, map);
        }

        @Override
        public boolean start() {
            return holderAppointer.start();
        }

        @Override
        public boolean stop() {
            return holderAppointer.stop();
        }
    }

    static class BaseHolderAppointer<T> implements SysPathListener<T> {

        final AtomicReference<Appointer<?>> witnessAtomicReference;
        final Holders.ColdHolder<T> holder;

        BaseHolderAppointer(Holders.ColdHolder<T> holder) {
            witnessAtomicReference = new AtomicReference<>(Appointer.CLEARED_APPOINTER);
            this.holder = holder;
        }

        @Override
        public <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
            assert basePath != null;
            T lastValue = null;
            final AtomicUtils.Witness<Appointer<?>> witness = AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> prev == Appointer.CLEARED_APPOINTER || !prev.equalTo(basePath) || map != IDENTITY, // always update if map is NOT identity
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

        @Override
        public boolean start() {
            return witnessAtomicReference.get().on();
        }

        @Override
        public boolean stop() {
            return witnessAtomicReference.get().off();
        }
    }

    /*
    * Appointers:
    * setAndStart, start, stop.
    *
    * BasePath:
    * all
    * */
    static class HolderAppointer<T> extends BaseHolderAppointer<T> implements PathListener<T> {
//        private final AtomicReference<Appointer<?>> witnessAtomicReference;
//        private final Holders.ColdHolder<T> holder;

        Holders.ColdHolder<T> getColdHolder() {
            return holder;
        }

        HolderAppointer(Holders.ColdHolder<T> holder) {
            super(holder);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathAndGet(P basePath, Function<S, T> map) {
            return (Appointer<S>) AtomicUtils.contentiousCAS(
                    witnessAtomicReference,
                    prev -> !prev.equalTo(basePath),
                    new UnaryOperator<Appointer<?>>() {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer =
                                map != IDENTITY ?
                                anInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue()))) :
                                        sInt -> holder.acceptVersionValue((Pair.Immutables.Int<T>) sInt);
//                                        new Consumer<Pair.Immutables.Int<S>>() {
//                                            @Override
//                                            public void accept(Pair.Immutables.Int<S> sInt) {
//                                                holder.acceptVersionValue((Pair.Immutables.Int<T>) sInt);
//                                            }
//                                        };
                        @Override
                        public Appointer<?> apply(Appointer<?> appointer) {
                            return new Appointer<>(basePath, intConsumer);
                        }
                    }
//                    prev -> {
//                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue())));
//                        return new Appointer<>(basePath, intConsumer);
//                    }
            ).next;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S, P extends BasePath<S>> Appointer<S> setPathUpdateAndGet(P basePath, BiFunction<T, S, T> update) {
            return (Appointer<S>) AtomicUtils.contentiousCAS(
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

//        @Override
//        public <S, P extends BasePath<S>> T setAndStart(P basePath, Function<S, T> map) {
//            assert basePath != null;
//            T lastValue = null;
//            final AtomicUtils.Witness<Appointer<?>> witness = AtomicUtils.contentiousCAS(
//                    witnessAtomicReference,
//                    prev -> prev == Appointer.CLEARED_APPOINTER || !prev.equalTo(basePath) || map != IDENTITY, // always update if map is NOT identity
//                    prev -> {
//                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt ->
//                                holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply(anInt.getValue())));
//                        return new Appointer<>(basePath, intConsumer);
//                    }
//            );
//            final Appointer<?> prev = witness.prev, next = witness.next;
//            if (next != null && prev != next) {
//                prev.shutDown();
//                //contention check
//                if (witnessAtomicReference.get().equalTo(basePath)) {
//                    if (!prev.isCleared()) lastValue = holder.getAndInvalidate();
//                    next.start();
//                }
//            }
//            return lastValue;
//        }

//        private final UnaryOperator<T> identity = UnaryOperator.identity();

//        @Override
//        public <P extends BasePath<T>> T setAndStart(P basePath) {
//            return setAndStart(basePath, identity);
//        }

        @Override
        public void stopAndClearPath() {
            Appointer<?> appointer = witnessAtomicReference.getAndSet(Appointer.CLEARED_APPOINTER);
            appointer.shutDown();
        }

//        @Override
//        public boolean start() {
//            return witnessAtomicReference.get().on();
//        }
//
//        @Override
//        public boolean stop() {
//            return witnessAtomicReference.get().off();
//        }

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
