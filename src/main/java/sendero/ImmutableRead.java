package sendero;

abstract class ImmutableRead<T> {
    abstract Immutable<T> getSnapshot();
    boolean isEqual(Immutable<T> other) {
        return other != null && other == getSnapshot();
    }
    Immutable.Values localSerialValues() {
        return getSnapshot().local;
    }
}
