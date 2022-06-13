package sendero;

abstract class ImmutableRead<T> {
    abstract Immutable<T> getSnapshot();
    protected T get() {return getSnapshot().get();}
    boolean isEqual(Immutable<T> other) {
        return other != null && other == getSnapshot();
    }
    Immutable.Values localSerialValues() {
        return getSnapshot().local;
    }
}
