package sendero.interfaces;

import java.util.function.Function;

@FunctionalInterface
public interface StringFunction<T> extends Function<String, T> {
}
