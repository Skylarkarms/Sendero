package sendero.interfaces;

import java.util.function.BiPredicate;

public interface BinaryPredicate<T> extends BiPredicate<T, T> {
    @Override
    boolean test(T next, T prev);

    @Override
    default BinaryPredicate<T> negate() {
        return (next, prev) -> !test(next, prev);
    }
}
