package sendero;

import sendero.atomics.AtomicUtils;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;
import sendero.switchers.Switchers;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Appointers {
    static class Appointer<A> {
        final int appointerVersion;
        final BasePath<A> producer;
        final Consumer<Pair.Immutables.Int<A>> toAppoint;

        final Switchers.Switch aSwitch = Switchers.getAtomic();

        private static<T, S> Consumer<Pair.Immutables.Int<S>> toAppointCreator(Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
            return sInt -> holder.accept(new Pair.Immutables.Int<T>(sInt.getInt(), map.apply(sInt.getValue())));
        }

        private static<S, T> Appointer<S> fixedAppointer(BasePath<S> producer, Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
            return new Appointer<>(-1, producer, toAppointCreator(holder, map));
        }

        private static<T> Appointer<T> fixedAppointer(BasePath<T> producer, Consumer<Pair.Immutables.Int<T>> holder) {
            return new Appointer<>(-1, producer, holder);
        }

        public static<S, T> BooleanConsumer booleanConsumerAppointer(BasePath<S> producer, Consumer<Pair.Immutables.Int<T>> holder, Function<S, T> map) {
            final Appointer<S> finalAppointer = fixedAppointer(producer, holder, map);
            return booleanConsumerAppointer(finalAppointer);
        }

        public static<T> BooleanConsumer booleanConsumerAppointer(BasePath<T> producer, Consumer<Pair.Immutables.Int<T>> holder) {
            final Appointer<T> finalAppointer = fixedAppointer(producer, holder);
            return booleanConsumerAppointer(finalAppointer);
        }

        public static<T> BooleanConsumer booleanConsumerAppointer(Appointer<T> appointer) {
            final Appointer<T> finalAppointer = appointer;
            return new BooleanConsumer() {
                @Override
                public void accept(boolean aBoolean) {
                    if (aBoolean) finalAppointer.appoint();
                    else finalAppointer.demote();
                }

                @Override
                public boolean equals(Object obj) {
                    assert obj instanceof BasePath;
                    return finalAppointer.equalProducer((BasePath<?>) obj);
                }
            };
        }

        private final boolean cleared;

        boolean isCleared() {
            return cleared;
        }

        public static final Appointer<?> initiating = new Appointer<>(-1, null, null);

        public final Appointer<A> getCleared() {
            return new Appointer<>(appointerVersion + 1, null, null);
        }

        Appointer(int appointerVersion, BasePath<A> producer, Consumer<Pair.Immutables.Int<A>> toAppoint) {
            this.appointerVersion = appointerVersion;
            this.producer = producer;
            cleared = producer == null;
            this.toAppoint = toAppoint;
        }

        boolean isActive() {
            return aSwitch.isActive();
        }

        boolean appoint() {
            boolean on = !isCleared() && aSwitch.on();
            if (on) producer.appoint(toAppoint);
            return on;
        }
        boolean demote() {
            boolean off = aSwitch.off();
            if (off) producer.demotionOverride(toAppoint);
            return off;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Appointer<?> appointer = (Appointer<?>) o;
            return Objects.equals(producer, appointer.producer);
        }

        public boolean equalProducer(BasePath<?> other) {
            return this.producer == other;
        }

        public<P> boolean equalTo(BasePath<P> basePath) {
            return producer.equals(basePath);
        }

        /**Returns this - other*/
        public int compareTo(Appointer<?> other) {
            return appointerVersion - other.appointerVersion;
        }

        @Override
        public int hashCode() {
            return Objects.hash(producer);
        }
    }

    interface PathListener<T> {
        /**@return previous Path OR null under contention*/
        <S, P extends BasePath<S>> Appointer<?> setPath(P basePath, Function<S, T> map);
        <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map);
        <P extends BasePath<T>> void setAndStart(P basePath);
        void stopAndClearPath();

        boolean start();
        boolean stop();
        boolean isActive();

        Appointer<?> getAppointer();

        Appointer<?> clearAndGet();
    }

    final static class SimpleAppointer<T> extends Holders.SingleColdHolder<T> implements PathListener<T> {

        private final HolderAppointer<T> holderAppointer = new HolderAppointer<>(this);

        SimpleAppointer(Consumer<Pair.Immutables.Int<T>> dispatcher, Predicate<T> expect) {
            super(dispatcher, expect);
        }


        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPath(P basePath, Function<S, T> map) {
            return holderAppointer.setPath(basePath, map);
        }

        @Override
        public <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map) {
            holderAppointer.setAndStart(basePath, map);
        }

        @Override
        public <P extends BasePath<T>> void setAndStart(P basePath) {
            holderAppointer.setAndStart(basePath);
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
        public Appointer<?> getAppointer() {
            return holderAppointer.getAppointer();
        }

        @Override
        public Appointer<?> clearAndGet() {
            return holderAppointer.clearAndGet();
        }
    }

    static class HolderAppointer<T> implements PathListener<T> {
        private final AtomicUtils.WitnessAtomicReference<Appointer<?>> witnessAtomicReference;
        private final Holders.ColdHolder<T> holder;

        HolderAppointer(Holders.ColdHolder<T> holder) {
            this.holder = holder;
            witnessAtomicReference = new AtomicUtils.WitnessAtomicReference<>(Appointer.initiating);
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPath(P basePath, Function<S, T> map) {
            return witnessAtomicReference.contentiousCAS(
                    prev -> prev.producer != basePath,
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
                        return new Appointer<>(prev.appointerVersion + 1, basePath, intConsumer);
                    }
            ).next;
        }

        @Override
        public <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map) {
            assert basePath != null;
            final AtomicUtils.WitnessAtomicReference.Witness<Appointer<?>> witness = witnessAtomicReference.contentiousCAS(
                    prev -> prev == Appointer.initiating || !prev.equalTo(basePath) || map != identity, // always update if map is NOT identity
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt ->
                        {
                            holder.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
                        };
                        return new Appointer<>(prev.appointerVersion + 1, basePath, intConsumer);
                    }
            );
            final Appointer<?> prev = witness.prev, next = witness.next;
            if (next != null && prev != next) {
                boolean prevWasSet = prev != Appointer.initiating && !prev.isCleared();
                if (prevWasSet && prev.isActive()) prev.demote();
                //contention check
                if (witnessAtomicReference.get().equalTo(basePath)) {
                    if (prevWasSet) holder.invalidate();
                    next.appoint();
                }
            }
        }

        private final UnaryOperator<T> identity = UnaryOperator.identity();

        @Override
        public <P extends BasePath<T>> void setAndStart(P basePath) {
            setAndStart(basePath, identity);
        }

        @Override
        public void stopAndClearPath() {
            Appointer<?> appointer = witnessAtomicReference.getAndUpdate(Appointer::getCleared);
            if (appointer != Appointer.initiating && !appointer.isCleared()) {
                appointer.demote();
            }
        }

        @Override
        public boolean start() {
            return witnessAtomicReference.get().appoint();
        }

        @Override
        public boolean stop() {
            return witnessAtomicReference.get().demote();
        }

        @Override
        public boolean isActive() {
            return witnessAtomicReference.get().isActive();
        }

        @Override
        public Appointer<?> getAppointer() {
            return witnessAtomicReference.get();
        }
        /** If next == null, prev was already cleared
         * @return*/
        @Override
        public Appointer<?> clearAndGet() {
            return witnessAtomicReference.contentiousCAS(
                    ((Predicate<Appointer<?>>) Appointer::isCleared).negate(),
                    Appointer::getCleared
            ).next;
        }
    }
}
