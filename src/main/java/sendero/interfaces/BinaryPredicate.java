package sendero.interfaces;

import java.util.function.BiPredicate;

public interface BinaryPredicate<T> extends BiPredicate<T, T> {
    @Override
    boolean test(T t, T t2);

    @Override
    default BiPredicate<T, T> and(BiPredicate<? super T, ? super T> other) {
        return BiPredicate.super.and(other);
    }

    @Override
    default BiPredicate<T, T> negate() {
        return BiPredicate.super.negate();
    }

    @Override
    default BiPredicate<T, T> or(BiPredicate<? super T, ? super T> other) {
        return BiPredicate.super.or(other);
    }
}
