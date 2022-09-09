package sendero.functions;

import sendero.interfaces.BinaryPredicate;

import java.util.function.*;

public final class Functions {
    private static final String CLEARED_STRING = ": [EMPTY FUNCTION]";

    private static final UnaryOperator<?> IDENTITY = UnaryOperator.identity();

    public static boolean isIdentity(Function<?, ?> operator) {
        return operator == IDENTITY;
    }

    @SuppressWarnings("unchecked")
    public static<T> UnaryOperator<T> myIdentity() {
        return (UnaryOperator<T>) IDENTITY;
    }

    private static final Consumer<?> emptyConsumer = new Consumer<Object>() {
        @Override
        public void accept(Object o) {

        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING;
        }
    };
    @SuppressWarnings("unchecked")
    public static<T> Consumer<T> emptyConsumer() {
        return (Consumer<T>) emptyConsumer;
    }
    private static final Runnable emptyRunnable = new Runnable() {
        @Override
        public void run() {

        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING;
        }
    };

    public static final BinaryPredicate<?> binaryAlwaysTrue = new BinaryPredicate<Object>() {
        @Override
        public boolean test(Object next, Object prev) {
            return true;
        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING + ",\n" +
                    " value = true";
        }
    };

    static final BinaryPredicate<?> alwaysFalse = new BinaryPredicate<Object>() {
        @Override
        public boolean test(Object next, Object prev) {
            return false;
        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING + ",\n" +
                    " value = false";
        }
    };

    @SuppressWarnings("unchecked")
    public static<T> BinaryPredicate<T> binaryAlways(boolean aBoolean) {
        return aBoolean ? (BinaryPredicate<T>) binaryAlwaysTrue : (BinaryPredicate<T>) alwaysFalse;
    }
    public static Runnable emptyRunnable() {
        return emptyRunnable;
    }
    public static final Predicate<?> alwaysTrue = new Predicate<Object>() {
        @Override
        public boolean test(Object next) {
            return true;
        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING + ",\n" +
                    " value = true";
        }
    };

    @SuppressWarnings("unchecked")
    public static<T> Predicate<T> always(boolean fixed) {
        return fixed ? (Predicate<T>) alwaysTrue : t -> false;
    }
    public static boolean truePredicate(Predicate<?> predicate) {
        return predicate == alwaysTrue;
    }
    public static boolean isEmpty(Consumer<?> consumer) {
        return consumer == emptyConsumer;
    }

    public static final IntConsumer defaultIntConsumer = new IntConsumer() {
        @Override
        public void accept(int value) {

        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING;
        }
    };

    public static boolean isDefault(IntConsumer that) {
        return that == defaultIntConsumer;
    }

    public static final LongConsumer defaultLongConsumer = new LongConsumer() {
        @Override
        public void accept(long value) {

        }

        @Override
        public String toString() {
            return super.toString() + CLEARED_STRING;
        }
    };

    public static boolean isDefault(LongConsumer that) {
        return that == defaultLongConsumer;
    }

}
