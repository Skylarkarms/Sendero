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
        private final BiFunction<Tag, Supplier<Value>, Value> diffAndGet;
        private final BiFunction<Tag, Supplier<Value>, Value> getAndDiff;

        private BiFunction<Tag, Supplier<Value>, Value> diffOperator(BinaryPredicate<Tag> equality, Function<UnaryOperator<Pair.Immutables<Tag, Value>>, Pair.Immutables<Tag, Value>> refFun) {
            final BinaryPredicate<Tag> finalPredicate = equality == null ? (prev, next) -> prev == next : equality;
            return (tag, valueSupplier) -> refFun.apply(
                    tagValueImmutables -> {
                        if (tagValueImmutables.firstValue == NO_TAG) return new Pair.Immutables<>(tag, valueSupplier.get());
                        Tag prev = tagValueImmutables.firstValue;
                        if (!finalPredicate.test(prev, tag)) return new Pair.Immutables<>(tag, valueSupplier.get());
                        return tagValueImmutables;
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
            this.diffAndGet = diffOperator(tagEquality, this.reference::updateAndGet);
            this.getAndDiff = diffOperator(tagEquality, this.reference::getAndUpdate);
        }

        private TaggedAtomicReference(Tag tag, Value value) {
            this.reference = new AtomicReference<>(new Pair.Immutables<>(tag, value));
            diffAndGet = diffOperator(null, this.reference::updateAndGet);
            getAndDiff = diffOperator(null, this.reference::getAndUpdate);
        }

        public TaggedAtomicReference() {
            reference = new AtomicReference<>(FIRST_PAIR);
            diffAndGet = diffOperator(null, this.reference::updateAndGet);
            getAndDiff = diffOperator(null, this.reference::getAndUpdate);
        }

        public TaggedAtomicReference(Value value) {
            reference = new AtomicReference<>(new Pair.Immutables<>(NO_TAG, value));
            diffAndGet = diffOperator(null, this.reference::updateAndGet);
            getAndDiff = diffOperator(null, this.reference::getAndUpdate);
        }


        public Value diffUpdateAndGet(Tag tag, Supplier<Value> value) {
            return diffAndGet.apply(tag, value);
        }

        public Value diffUpdateAndGet(Tag tag, Function<Tag, Value> value) {
            return diffAndGet.apply(tag, () -> value.apply(tag));
        }

        public Value getAndDiffUpdate(Tag tag, Value value) {
            return getAndDiff.apply(tag, () -> value);
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
        public Value forceUpdateAndGet(Value value, Consumer<Tag> onUpdated) {
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
}
