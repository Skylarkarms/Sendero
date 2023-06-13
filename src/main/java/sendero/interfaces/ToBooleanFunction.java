package sendero.interfaces;

@FunctionalInterface
public interface ToBooleanFunction<T> {
    boolean asBoolean(T t);

    @FunctionalInterface
    interface FromInt {
        boolean asBoolean(int anInt);

        default ToBooleanFunction<Integer> toParent() {
            return FromInt.this::asBoolean;
        }
    }
}
