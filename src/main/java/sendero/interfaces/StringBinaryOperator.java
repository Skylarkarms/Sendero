package sendero.interfaces;

import java.util.function.BiFunction;

@FunctionalInterface
public interface StringBinaryOperator extends BiFunction<String, String, String> {
}
