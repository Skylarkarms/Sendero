package sendero.atomics;

import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

public final class AtomicUtils {
    public static class TaggedAtomicReference<Tag, Value> implements Supplier<Value> {

        @SuppressWarnings("unchecked")
        private final Tag NO_TAG = (Tag) new Object();
        private final Pair.Immutables<Tag, Value> FIRST_PAIR = new Pair.Immutables<>(NO_TAG, null);
        private final BiFunction<Tag, UnaryOperator<Value>, Value> diffUpdateAndGet;
        private final BiFunction<Tag, UnaryOperator<Value>, Value> diffGetAndUpdate;
//        private final BiFunction<Tag, Supplier<Value>, Value> diffAndGet;
//        private final BiFunction<Tag, Supplier<Value>, Value> getAndDiff;

//        private BiFunction<Tag, Supplier<Value>, Value> diffOperator(BinaryPredicate<Tag> equality, Function<UnaryOperator<Pair.Immutables<Tag, Value>>, Pair.Immutables<Tag, Value>> refFun) {
//            final BinaryPredicate<Tag> finalPredicate = equality == null ? (prev, next) -> prev == next : equality;
//            return (tag, valueSupplier) -> refFun.apply(
//                    tagValueImmutables -> {
//                        if (tagValueImmutables.firstValue == NO_TAG) return new Pair.Immutables<>(tag, valueSupplier.get());
//                        Tag prev = tagValueImmutables.firstValue;
//                        if (!finalPredicate.test(prev, tag)) return new Pair.Immutables<>(tag, valueSupplier.get());
//                        return tagValueImmutables;
//                    }
//            ).secondValue;
//        }
        private BiFunction<Tag, UnaryOperator<Value>, Value> diffUpdateOperator(BinaryPredicate<Tag> equality, Function<UnaryOperator<Pair.Immutables<Tag, Value>>, Pair.Immutables<Tag, Value>> refFun) {
            final BinaryPredicate<Tag> finalPredicate = equality == null ? (prev, next) -> prev == next : equality;
            return (tag, valueUnaryOperator) -> refFun.apply(
                    prevPair -> {
                        Value prevValue = prevPair.secondValue;
                        Tag prevTag = prevPair.firstValue;
                        if (prevTag == NO_TAG || !finalPredicate.test(prevTag, tag)) return new Pair.Immutables<>(tag, valueUnaryOperator.apply(prevValue));
//                        if (!finalPredicate.test(prevTag, tag)) return new Pair.Immutables<>(tag, valueUnaryOperator.apply(prevValue));
                        return prevPair;
                    }
            ).secondValue;
        }

        public static<T, V> TaggedAtomicReference<T, V> build(UnaryOperator<Builder<T, V>> op) {
            return op.apply(new Builder<>()).build();
        }

        @Override
        public Value get() {
            return reference.get().secondValue;
        }

        public Tag getTag() {
            Tag t = reference.get().firstValue;
            return t == NO_TAG ? null : t;
        }

        public static class Builder<Tag, Value> {
            private BinaryPredicate<Tag> tagEquality;
            private Pair.Immutables<Tag, Value> first;

            public Builder<Tag, Value> check(BinaryPredicate<Tag> tagEquality) {
                this.tagEquality = tagEquality;
                return this;
            }

            public Builder<Tag, Value> first(Tag tag, Value value) {
                this.first = new Pair.Immutables<>(tag, value);
                return this;
            }
            private TaggedAtomicReference<Tag, Value> build() {
                return new TaggedAtomicReference<>(tagEquality, first);
            }
        }
        private final AtomicReference<Pair.Immutables<Tag, Value>> reference;

        private TaggedAtomicReference(
                BinaryPredicate<Tag> tagEquality,
                Pair.Immutables<Tag, Value> reference
        ) {
            this.reference = new AtomicReference<>(reference == null ? FIRST_PAIR : reference);
//            this.diffAndGet = diffOperator(tagEquality, this.reference::updateAndGet);
            this.diffUpdateAndGet = diffUpdateOperator(tagEquality, this.reference::updateAndGet);
            this.diffGetAndUpdate = diffUpdateOperator(tagEquality, this.reference::getAndUpdate);
//            this.getAndDiff = diffOperator(tagEquality, this.reference::getAndUpdate);
        }

        private TaggedAtomicReference(Tag tag, Value value) {
            this.reference = new AtomicReference<>(new Pair.Immutables<>(tag, value));
//            diffAndGet = diffOperator(null, this.reference::updateAndGet);
//            getAndDiff = diffOperator(null, this.reference::getAndUpdate);
            this.diffUpdateAndGet = diffUpdateOperator(null, this.reference::updateAndGet);
            this.diffGetAndUpdate = diffUpdateOperator(null, this.reference::getAndUpdate);


        }

        public TaggedAtomicReference() {
            reference = new AtomicReference<>(FIRST_PAIR);
//            diffAndGet = diffOperator(null, this.reference::updateAndGet);
//            getAndDiff = diffOperator(null, this.reference::getAndUpdate);
            this.diffUpdateAndGet = diffUpdateOperator(null, this.reference::updateAndGet);
            this.diffGetAndUpdate = diffUpdateOperator(null, this.reference::getAndUpdate);
        }

        public TaggedAtomicReference(Value value) {
            reference = new AtomicReference<>(new Pair.Immutables<>(NO_TAG, value));
//            diffAndGet = diffOperator(null, this.reference::updateAndGet);
//            getAndDiff = diffOperator(null, this.reference::getAndUpdate);
            this.diffUpdateAndGet = diffUpdateOperator(null, this.reference::updateAndGet);
            this.diffGetAndUpdate = diffUpdateOperator(null, this.reference::getAndUpdate);
        }


        public Value diffSetAndGet(Tag tag, Supplier<Value> value) {
            return diffUpdateAndGet.apply(tag, prevVal -> value.get());
//            return diffAndGet.apply(tag, value);
        }

        public Value diffUpdateAndGet(Tag tag, UnaryOperator<Value> value) {
            return diffUpdateAndGet.apply(tag, value);
        }

        public Value diffSetAndGet(Tag tag, Function<Tag, Value> value) {
            return diffUpdateAndGet.apply(tag, prevVal -> value.apply(tag));
//            return diffAndGet.apply(tag, () -> value.apply(tag));
        }

        public Value getAndDiffUpdate(Tag tag, Value value) {
            return diffGetAndUpdate.apply(tag, prevVal -> value);
//            return getAndDiff.apply(tag, () -> value);
        }

        public Value getAndClear() {
            final Pair.Immutables<Tag, Value> prev = reference.getAndSet(FIRST_PAIR);
            return prev == FIRST_PAIR ? null : prev.secondValue;
        }

        public boolean isCleared() {
            return reference.get() == FIRST_PAIR;
        }

        public boolean expectAndClear(Value expect) {
            boolean shouldClear;
            for (Pair.Immutables<Tag, Value> prev = reference.get();;) {
                shouldClear = prev.firstValue != NO_TAG && prev.secondValue == expect;
                if (shouldClear && reference.compareAndSet(prev, FIRST_PAIR)) break;
                if (prev == (prev = reference.get())) break;
            }
            return shouldClear;
        }

        public Value expectTagAndClear(Tag expect) {
            boolean shouldClear;
            for (Pair.Immutables<Tag, Value> prev = reference.get();;) {
                Tag prevTag = prev.firstValue;
                shouldClear = prevTag != NO_TAG && prevTag == expect;
                if (shouldClear && reference.compareAndSet(prev, FIRST_PAIR)) return prev.secondValue;
                if (prev == (prev = reference.get())) return null;
            }
        }

        public Value expectTagAndSet(Tag expect, Value value) {
            boolean shouldClear;
            for (Pair.Immutables<Tag, Value> prev = reference.get();;) {
                Tag prevTag = prev.firstValue;
                shouldClear = prevTag != NO_TAG && prevTag == expect;
                if (shouldClear && reference.compareAndSet(prev, new Pair.Immutables<>(NO_TAG, value))) return prev.secondValue;
                if (prev == (prev = reference.get())) return null;
            }
        }

        public Tag getAndClear(Value expect) {
            boolean shouldClear;
            for (Pair.Immutables<Tag, Value> prev = reference.get();;) {
                shouldClear = prev.firstValue != NO_TAG && prev.secondValue == expect;
                if (shouldClear && reference.compareAndSet(prev, FIRST_PAIR)) {
                    return prev.firstValue;
                }
                if (prev == (prev = reference.get())) break;
            }
            return null;
        }

        public Value clear(Consumer<Tag> cleared) {
            Pair.Immutables<Tag, Value> prev = reference.getAndSet(FIRST_PAIR);
            cleared.accept(prev.firstValue);
            return prev.secondValue;
        }

        public boolean contains(Tag tag) {
            return reference.get().firstValue == tag;
        }

        public Value getAndSet(Tag tag, Value value) {
            return reference.getAndSet(new Pair.Immutables<>(tag, value)).secondValue;
        }

        public void set(Value value) {
            reference.set(new Pair.Immutables<>(NO_TAG, value));
        }

        public Value forceUpdateAndGet(Value value) {
            return reference.updateAndGet(
                    tagValueImmutables -> new Pair.Immutables<>(NO_TAG, value)
            ).secondValue;
        }

        /**Consumes previous Tag*/
        public Value forcedSetAndGet(Value value, Consumer<Tag> onUpdated) {
            Pair.Immutables<Tag, Value> prev = reference.get(), next = null;
            for (boolean haveNext = false;;) {
                if (!haveNext)
                    next = new Pair.Immutables<>(NO_TAG, value);
                if (reference.compareAndSet(prev, next)) {
                    Tag tag = prev.firstValue;
                    if (tag != NO_TAG) onUpdated.accept(tag);
                    return next.secondValue;
                }
            }
        }
        public Value forcedUpdateAndGet(UnaryOperator<Value> value, Consumer<Tag> onUpdated) {
            Pair.Immutables<Tag, Value> prev = reference.get(), next = null;
            Value prevValue = prev.secondValue;
            for (boolean remainSame = false;; remainSame = (prev == (prev = reference.get()))) {
                if (!remainSame)
                    prevValue = prev.secondValue;
                    next = new Pair.Immutables<>(NO_TAG, value.apply(prevValue));
                if (reference.compareAndSet(prev, next)) {
                    Tag tag = prev.firstValue;
                    if (tag != NO_TAG) onUpdated.accept(tag);
                    return next.secondValue;
                }
            }
        }

//        public Value expectAndForceUpdateAndGet(Supplier<Tag> expectSupplier, Value value) {
//            Pair.Immutables<Tag, Value> prev = reference.get(), next = new Pair.Immutables<>(NO_TAG, value);
//            Tag prevTag = prev.firstValue, expectTag = expectSupplier.get();
//            boolean notNullTag = expectTag != null, shouldReplace = notNullTag && prevTag == expectTag;
//            for (boolean same = true;;) {
//                if (!same) {
//                    expectTag = expectSupplier.get();
////                    next = new Pair.Immutables<>(NO_TAG, value);
//                    prevTag = prev.firstValue;
//                    shouldReplace = notNullTag && prevTag == expectTag;
//
//                }
//                if (shouldReplace && reference.compareAndSet(prev, next)) return next.secondValue;
//                if (same == (prev == (prev = reference.get()))) return null;
//            }
//        }

    }

    public static final class WitnessAtomicReference<T> implements Supplier<T> {
        private final AtomicReference<T> ref;

        public WitnessAtomicReference(T value) {
            this.ref = new AtomicReference<>(value);
        }

        public WitnessAtomicReference() {
            this.ref = new AtomicReference<>();
        }

        @Override
        public T get() {
            return ref.get();
        }

        public T getAndSet(T newVal) {
            return ref.getAndSet(newVal);
        }

        public T getAndUpdate(UnaryOperator<T> operator) {
            return ref.getAndUpdate(operator);
        }

        public boolean expectAndSet(Predicate<T> expect, T next) {
            T prev = ref.get();
            for (;;) {
                if (expect.test(prev) && ref.compareAndSet(prev, next)) return true;
                if (prev == (prev = ref.get())) return false;
            }
        }

        public boolean trySet(Predicate<T> setIf, T next, Predicate<T> retryIf) {
            T prev = ref.get();
            boolean first = true;
            while (first || retryIf.test(prev = ref.get())) {
                if (setIf.test(prev) && ref.compareAndSet(prev, next)) return true;
                first = false;
            }
            return false;
        }

        /**Will retry even without contention, until set OR retryIf == false*/
//        public boolean retryUpdate(Predicate<T> updateIf, UnaryOperator<T> next, Predicate<T> retryIf) {
//            T prev = ref.get(), nextT = null;
//            boolean /*first = true, */update, retry = true;
//            while (retry) {
////            while (first || retryIf.test(prev = ref.get())) {
//                update = updateIf.test(prev);
//                if (update) {
//                    nextT = next.apply(prev);
//                    if (ref.compareAndSet(prev, nextT)) return true;
//                }
//                retry = retryIf.test(prev = ref.get());
////                first = false;
//            }
//            return false;
//        }

        /**Will retry if:
         *  <li> Contention met && retryIf test == true
         * <br>
         * <br>
         *@return next == null if:
         * <li> A) updateIf == false && no contention met
         * <li> B) Contention met && retryIf == false
         * */
        public Witness<T> spinSwap(
                Predicate<T> updateIf,
                UnaryOperator<T> next,
                Predicate<T> retryIf) {
            T prev = ref.get(), nextT;
            boolean update, retry = true;
            for (;retry;retry = retryIf.test(prev)) {
                update = updateIf.test(prev);
                if (update) {
                    nextT = next.apply(prev);
                    if (ref.compareAndSet(prev, nextT)) return new Witness<>(prev, nextT);
                }
                if (prev == (prev = ref.get())) return new Witness<>(prev, null); // if true, update was false, should return
            }
            return new Witness<>(prev, null);
        }

        public T updateAndGet(UnaryOperator<T> next) {
            return ref.updateAndGet(next);
        }

//        /*If next is null, the set was NOT succesful*/
//        public static class Witness<T, S> {
//            public final T prev, next;
//            public final S snapshot;
//
//            Witness(
//                    T prev, T next, S snapshot
//            ) {
//                this.prev = prev;
//                this.next = next;
//                this.snapshot = snapshot;
//            }
//        }

        public static class Witness<T> {
            public final T prev, next;

            Witness(T prev, T next) {
                this.prev = prev;
                this.next = next;
            }
        }

//        @FunctionalInterface
//        public interface UpdateIf<T, S> {
//            boolean test(T prev, S shot);
//        }

//        @FunctionalInterface
//        public interface BreakIf<T, S> {
//            boolean test(T prev, S shot);
////            static<T, S> BreakIf<T, S> breakIfSet() {
////                return (prev, shot) -> true;
////            }
//            static <T> Predicate<T> ifSet() {
//                return t -> true;
//            }
//        }

//        public static Predicate<?> onSet = (Predicate<Object>) o -> true;
//        public static BreakIf<?, ?> breakIfSet = (BreakIf<Object, Object>) (prev, shot) -> true;

//        @FunctionalInterface
//        public interface Map<T, S> {
//            T map(T prev, S shot);
//        }

//        public <S> Witness<T, S> CAS(
//              Supplier<S> snapshot,
//              UpdateIf<T, S> updateIf,
//              Map<T, S> nextMap,
//              BiPredicate<T, S> retryIf
//        ) {
//            T prev = ref.get(), next;
//            S shot = snapshot.get();
//            boolean first = true;
//            for (boolean update;
//                //If changed, retryIf
//                 first || retryIf.test(prev, shot);
//                 shot = snapshot.get()
//            ) {
//                update = updateIf.test(prev, shot);
//                if (update) {
//                    next = nextMap.map(prev, shot);
//                    if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next, shot);
//                }
//                first = false;
//                if (prev == (prev = ref.get()) //If true update was false
//                ) return new Witness<>(prev, null, shot);
//            }
//            return new Witness<>(prev, null, shot);
//        }

//        /**@return next == null if:
//         * A) Update == false;
//         * B) retryIf == false
//         *
//         * retryIf test will check on contention met*/
//        public <S> Witness<T> retryCAS(
//              Predicate<T> updateIf,
//              UnaryOperator<T> nextMap,
//              Predicate<T> retryIf
//        ) {
//            T prev = ref.get(), next;
//            boolean first = true;
//            for (boolean update;
//                //If changed, retryIf
//                 first || retryIf.test(prev);
//            ) {
//                update = updateIf.test(prev);
//                if (update) {
//                    next = nextMap.apply(prev);
//                    if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
//                }
//                first = false;
//                if (prev == (prev = ref.get()) //If true update was false
//                ) return new Witness<>(prev, null);
//            }
//            return new Witness<>(prev, null);
//        }

        /**
         * Will retry until match.
         * <br>
         * @return next == null if:
         * <li> A) update == false;
         *
         *
         * */
        public<S> Witness<T> contentiousCAS(
              Predicate<T> updateIf,
              UnaryOperator<T> nextMap
        ) {
            T prev = ref.get(), next;
            for (boolean update;;) {
                update = updateIf.test(prev);
                if (update) {
                    next = nextMap.apply(prev);
                    if (ref.compareAndSet(prev, next)) return new Witness<>(prev, next);
                }
                if (
                        /*breakIf.test(prev) //
                                && */prev == (prev = ref.get()) //If true, update was false
                ) return new Witness<>(prev, null);
            }
        }
    }

}
