package sendero.interfaces;

import sendero.functions.Functions;

import java.util.function.BiPredicate;

public interface BinaryPredicate<T> extends BiPredicate<T, T> {
    @Override
    boolean test(T next, T prev);

    @Override
    default BinaryPredicate<T> negate() {
        RuntimeException e = new RuntimeException();
        return (next, prev) -> {
            try {
                return !test(next, prev);
            } catch (Exception ex) {
                e.initCause(ex);
                throw e;
            }
        };
    }

    default boolean alwaysTrue() {
        return this == Functions.binaryAlwaysTrue;
    }

    static<T> BinaryPredicate<T> always(boolean value) {
        return Functions.binaryAlways(value);
    }
}
