package sendero.interfaces;

import java.util.function.Function;

@FunctionalInterface
public interface ToStringFunction<T> extends Function<T, String> {

    @FunctionalInterface
    interface FromInt {
        String asString(int integer);

        default ToStringFunction<Integer> as() {
            return this::asString;
        }
    }

    @FunctionalInterface
    interface FromDouble {
        String asString(double aDouble);
        default ToStringFunction<Double> as() {
            return this::asString;
        }

    }
}
