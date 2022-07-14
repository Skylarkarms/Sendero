package sendero.interfaces;

import sendero.functions.Functions;

import java.util.function.BiPredicate;

public interface BinaryPredicate<T> extends BiPredicate<T, T> {
    @Override
    boolean test(T next, T prev);

    @Override
    default BinaryPredicate<T> negate() {
        return (next, prev) -> !test(next, prev);
    }

    default boolean alwaysTrue() {
        return this == Functions.binaryAlwaysTrue;
    }

    static<T> BinaryPredicate<T> always(boolean value) {
        return Functions.binaryAlways(value);
    }
}
