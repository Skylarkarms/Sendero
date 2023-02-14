package sendero.interfaces;

import java.util.function.Function;

@FunctionalInterface
public interface ToStringFunction<T> extends Function<T, String> {
}
