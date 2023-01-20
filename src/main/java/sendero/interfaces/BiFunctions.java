package sendero.interfaces;

import java.util.function.BiFunction;

public interface BiFunctions<T, U, R> {

    default BiFunction<T, U ,R> as() {
        return null;
    }

    @FunctionalInterface
    interface Int<U, R> extends BiFunctions<Integer, U, R> {
        R apply(int anInt, U u);

        @Override
        default BiFunction<Integer, U, R> as() {
            return Int.this::apply;
        }
    }
}
