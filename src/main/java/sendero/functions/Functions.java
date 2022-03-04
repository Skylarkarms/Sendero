package sendero.functions;

import sendero.interfaces.BinaryPredicate;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class Functions {
    private static final String CLEARED_STRING = ": [EMPTY FUNCTION]";
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

    public static<T> BinaryPredicate<T> binaryAlways(boolean aBoolean) {
        return (o, t2) -> aBoolean;
    }
    public static Runnable emptyRunnable() {
        return emptyRunnable;
    }
    public static<T> Predicate<T> always(boolean fixed) {
        return t -> fixed;
    }
}
