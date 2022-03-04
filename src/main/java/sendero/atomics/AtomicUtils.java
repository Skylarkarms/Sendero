package sendero.atomics;

import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class AtomicUtils {
    public static<T, V> TaggedAtomicReference<T, V> get(UnaryOperator<TaggedAtomicReference.Builder<T, V>> op) {
        return TaggedAtomicReference.getBuilder(op).build();
    }
    @SuppressWarnings("unchecked")
    public static<T, V> TaggedAtomicReference<T, V> get() {
        return (TaggedAtomicReference<T, V>) TaggedAtomicReference.getBuilder().build();
    }
    public static class TaggedAtomicReference<Tag, Value> {

        private final BinaryPredicate<Tag> equality;
        private final BiFunction<Tag, Supplier<Value>, Value> valueSupplier;
        private BiFunction<Tag, Supplier<Value>, Value> builderNoEquality() {
            return (tag, valueSupplier) -> reference.updateAndGet(
                    tagValueImmutables -> {
                        if (tagValueImmutables == null) return new Pair.Immutables<>(tag, valueSupplier.get());
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
                        if (tagValueImmutables == null) return new Pair.Immutables<>(tag, valueSupplier.get());
                        Tag prev = tagValueImmutables.firstValue;
                        if (equality.test(prev, tag)) return new Pair.Immutables<>(tag, valueSupplier.get());
                        return tagValueImmutables;
                    }
            ).secondValue;
        }

        static<T, V> Builder<T, V> getBuilder(UnaryOperator<Builder<T, V>> op) {
            return op.apply(new Builder<>());
        }

        static<T, V> Builder<T, V> getBuilder() {
            return new Builder<>();
        }

        public static class Builder<Tag, Value> {
            private BinaryPredicate<Tag> tagEquality;
            private Pair.Immutables<Tag, Value> first;

            public void check(BinaryPredicate<Tag> tagEquality) {
                this.tagEquality = tagEquality;
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
            this.reference = new AtomicReference<>(reference);
            this.valueSupplier = tagEquality != null ? builderEquality() : builderNoEquality();
        }

        public TaggedAtomicReference(Tag tag, Value value) {
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

        public static class Int<Value> {
            private final Pair.Immutables.Int<Value> NOT_SET = new Pair.Immutables.Int<>(-1, null);
            private final AtomicReference<Pair.Immutables.Int<Value>> reference;

            public Int() {
                this.reference = new AtomicReference<>(NOT_SET);
            }
            public Int(int first, Value initial) {
                this.reference = new AtomicReference<>(new Pair.Immutables.Int<>(first, initial));
            }

            public Value getOrSet(int nextI, Supplier<Value> next) {
                assert next != null;
                return reference.updateAndGet(
                        valueInt -> {
                            int prevInt = valueInt.getInt();
                            if (valueInt == NOT_SET || prevInt != nextI) return new Pair.Immutables.Int<>(nextI, next.get());
                            return valueInt;
                        }
                ).getValue();
            }

        }
    }
}
