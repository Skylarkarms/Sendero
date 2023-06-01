package sendero.interfaces;

import sendero.functions.Functions;

import java.util.function.BiPredicate;

@FunctionalInterface
public interface BinaryPredicate<T> extends BiPredicate<T, T> {
    @Override
    boolean test(T next, T prev);

    /**@return true if either of them is null, BUT not the other, <p>
     * OR true if both are null <p>
     * else proceeds to perform the test*/
    static<T> BinaryPredicate<T> nonNullTest(BinaryPredicate<T> test) {
        return (next1, prev1) -> {
            boolean prevNull = false;
            if (next1 == null || (prevNull = prev1 == null)) {
                return !prevNull && prev1 == null;
            } else {
                return test.test(next1, prev1);
            }
        };
    }

    @Override
    default BinaryPredicate<T> negate() {
        final RuntimeException e = new RuntimeException();
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
