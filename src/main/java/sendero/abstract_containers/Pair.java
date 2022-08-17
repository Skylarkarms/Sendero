package sendero.abstract_containers;

import sendero.interfaces.BinaryPredicate;

import java.util.Objects;

public final class Pair {

    public static class Immutables<T, U> {
        public final T firstValue;
        public final U secondValue;

        public Immutables(T firstValue, U secondValue) {
            this.firstValue = firstValue;
            this.secondValue = secondValue;
        }

        public static class Long<T> {
            private final long aLong;
            private final T value;

            public Long(long aLong, T value) {
                this.aLong = aLong;
                this.value = value;
            }

            public long getLong() {
                return aLong;
            }

            public T getValue() {
                return value;
            }
        }

        public static class Int<T> {
            public final int anInt;
            public final T value;

            public Int(int anInt, T value) {
                this.anInt = anInt;
                this.value = value;
            }

            public Int(Int<T> other) {
                assert  other != null;
                this.anInt = other.anInt;
                this.value = other.value;
            }

            /**0 if equal, < 0 if other is greater, > 0 if other is lesser*/
            public int compareTo(Int<T> other) {
                return anInt - other.anInt;
            }

            public int compareTo(int other) {
                return anInt - other;
            }

            public Int<T> deepCopy() {
                return new Int<>(anInt, value);
            }

            public boolean sameValue(Int<T> that, BinaryPredicate<T> equal) {
                return value == that.value || equal.test(that.value, value);
            }

        }

        public static class Bool<T> {
            public final boolean aBoolean;
            public final T value;
            public Bool(boolean aBoolean, T value) {
                this.aBoolean = aBoolean;
                this.value = value;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Immutables<?, ?> that = (Immutables<?, ?>) o;
            return Objects.equals(firstValue, that.firstValue) && Objects.equals(secondValue, that.secondValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstValue, secondValue);
        }
    }

}
