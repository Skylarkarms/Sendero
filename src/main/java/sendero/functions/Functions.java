package sendero.functions;

import sendero.interfaces.BinaryPredicate;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Functions {
    private static final String CLEARED_STRING = ": [EMPTY FUNCTION]";

    public static final UnaryOperator<?> IDENTITY = UnaryOperator.identity();

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

    private static final BinaryPredicate<?> alwaysTrue = new BinaryPredicate<Object>() {
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

    private static final BinaryPredicate<?> alwaysFalse = new BinaryPredicate<Object>() {
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
        return aBoolean ? (BinaryPredicate<T>) alwaysTrue : (BinaryPredicate<T>) alwaysFalse;
//        return (o, t2) -> aBoolean;
    }
    public static Runnable emptyRunnable() {
        return emptyRunnable;
    }
    public static<T> Predicate<T> always(boolean fixed) {
        return t -> fixed;
    }
}
