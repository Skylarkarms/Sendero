package sendero.interfaces;

@FunctionalInterface
public interface ToBooleanFunction<T> {
    boolean asBoolean(T t);
}
