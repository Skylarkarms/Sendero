package sendero.atomics;

import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class AtomicUtils {
    public static class TaggedAtomicReference<Tag, Value> {

        @SuppressWarnings("unchecked")
        private final Tag NO_TAG = (Tag) new Object();
        private final Pair.Immutables<Tag, Value> FIRST_PAIR = new Pair.Immutables<>(NO_TAG, null);
        private final BinaryPredicate<Tag> equality;
        private final BiFunction<Tag, Supplier<Value>, Value> valueSupplier;
        private BiFunction<Tag, Supplier<Value>, Value> builderNoEquality() {
            return (tag, valueSupplier) -> reference.updateAndGet(
                    tagValueImmutables -> {
                        if (tagValueImmutables.firstValue == NO_TAG) return new Pair.Immutables<>(tag, valueSupplier.get());
                        Tag prev = tagValueImmutables.firstValue;
                        if (prev != tag) return new Pair.Immutables<>(tag, valueSupplier.get());
                        return tagValueImmutables;
                    }
            ).secondValue;
        }

        private BiFunction<Tag, Supplier<Value>, Value> builderEquality() {
            assert equality != null;
            return (tag, valueSupplier) -> reference.updateAndGet(
                    tagValueImmutables -> {
                        if (tagValueImmutables.firstValue == NO_TAG) return new Pair.Immutables<>(tag, valueSupplier.get());
                        Tag prev = tagValueImmutables.firstValue;
                        if (equality.test(prev, tag)) return new Pair.Immutables<>(tag, valueSupplier.get());
                        return tagValueImmutables;
                    }
            ).secondValue;
        }

        public static<T, V> TaggedAtomicReference<T, V> build(UnaryOperator<Builder<T, V>> op) {
            return op.apply(new Builder<>()).build();
        }

        public static class Builder<Tag, Value> {
            private BinaryPredicate<Tag> tagEquality;
            private Pair.Immutables<Tag, Value> first;

            public Builder<Tag, Value> check(BinaryPredicate<Tag> tagEquality) {
                this.tagEquality = tagEquality;
                return this;
            }

            public void first(Tag tag, Value value) {
                this.first = new Pair.Immutables<>(tag, value);
            }
            public TaggedAtomicReference<Tag, Value> build() {
                return new TaggedAtomicReference<>(tagEquality, first);
            }
        }

        private TaggedAtomicReference(
                BinaryPredicate<Tag> tagEquality,
                Pair.Immutables<Tag, Value> reference
        ) {
            this.equality = tagEquality;
            this.reference = new AtomicReference<>(reference == null ? FIRST_PAIR : reference);
            this.valueSupplier = tagEquality != null ? builderEquality() : builderNoEquality();
        }

        private TaggedAtomicReference(Tag tag, Value value) {
            this.reference = new AtomicReference<>(new Pair.Immutables<>(tag, value));
            equality = null;
            valueSupplier = builderNoEquality();
        }

        public TaggedAtomicReference() {
            reference = new AtomicReference<>();
            equality = null;
            valueSupplier = builderNoEquality();
        }

        private final AtomicReference<Pair.Immutables<Tag, Value>> reference;

        public Value getOrSet(Tag tag, Supplier<Value> value) {
            return valueSupplier.apply(tag, value);
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

        public Value getAndSet(Tag tag, Value value) {
            return reference.getAndSet(new Pair.Immutables<>(tag, value)).secondValue;
        }

        public void set(Value value) {
            reference.set(new Pair.Immutables<>(NO_TAG, value));
        }

    }
}
