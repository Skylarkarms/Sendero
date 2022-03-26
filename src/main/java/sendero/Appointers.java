package sendero;

import sendero.atomics.AtomicUtils;
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

        boolean isCleared() {
            return producer == null;
        }

        public static final Appointer<?> initiating = new Appointer<>(-1, null, null);

        public final Appointer<A> getCleared() {
            return new Appointer<>(appointerVersion + 1, null, null);
        }

        Appointer(int appointerVersion, BasePath<A> producer, Consumer<Pair.Immutables.Int<A>> toAppoint) {
            this.appointerVersion = appointerVersion;
            this.producer = producer;
            this.toAppoint = toAppoint;
        }

        boolean isActive() {
            return aSwitch.isActive();
        }

        boolean appoint() {
            boolean on = !isCleared() && aSwitch.on();
            if (on) {
                producer.appoint(toAppoint);
            }
            return on;
        }
        boolean demote() {
            boolean off = aSwitch.off();
            if (off) {
//                if (producer instanceof BasePath.ToMany) {
//                    ((BasePath.ToMany<A>) producer).demote(toAppoint);
//                } else {
//                    assert producer instanceof BasePath.Injective;
//                    ((BasePath.Injective<A>) producer).demote();
//                }
                producer.demotionOverride(toAppoint);
            }
            return off;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Appointer<?> appointer = (Appointer<?>) o;
            return Objects.equals(producer, appointer.producer);
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

    final static class SimpleAppointer<T> extends Holders.SingleHolder<T> implements PathListener<T> {

        private final SelfAppointer<T> selfAppointer = new SelfAppointer<>(this);

        SimpleAppointer(Consumer<Pair.Immutables.Int<T>> dispatcher, Predicate<T> expect) {
            super(dispatcher, expect);
        }


        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPath(P basePath, Function<S, T> map) {
            return selfAppointer.setPath(basePath, map);
        }

        @Override
        public <S, P extends BasePath<S>> void setAndStart(P basePath, Function<S, T> map) {
            selfAppointer.setAndStart(basePath, map);
        }

        @Override
        public <P extends BasePath<T>> void setAndStart(P basePath) {
            selfAppointer.setAndStart(basePath);
        }

        @Override
        public void stopAndClearPath() {
            selfAppointer.stopAndClearPath();
        }

        @Override
        public boolean start() {
            return selfAppointer.start();
        }

        @Override
        public boolean stop() {
            return selfAppointer.stop();
        }

        @Override
        public boolean isActive() {
            return selfAppointer.isActive();
        }

        @Override
        public Appointer<?> getAppointer() {
            return selfAppointer.getAppointer();
        }

        @Override
        public Appointer<?> clearAndGet() {
            return selfAppointer.clearAndGet();
        }
    }

    static class SelfAppointer<T> implements PathListener<T> {
        private final AtomicUtils.WitnessAtomicReference<Appointer<?>> witnessAtomicReference = new AtomicUtils.WitnessAtomicReference<>(Appointer.initiating);
        private final Holders.ColdHolder<T> self;
//        private final Holders.DispatcherHolder<T> self;

        SelfAppointer(Holders.ColdHolder<T> self) {
            this.self = self;
        }

        @Override
        public <S, P extends BasePath<S>> Appointer<?> setPath(P basePath, Function<S, T> map) {
            return witnessAtomicReference.contentiousCAS(
                    prev -> prev.producer != basePath,
                    prev -> {
                        final Consumer<Pair.Immutables.Int<S>> intConsumer = anInt -> self.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
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
                            self.acceptVersionValue(new Pair.Immutables.Int<>(anInt.getInt(), map.apply((S) anInt.getValue())));
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
                    if (prevWasSet) self.invalidate();
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
