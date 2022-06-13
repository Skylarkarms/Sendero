package sendero;


import static sendero.Holders.SwapBroadcast.NO_DELAY;

interface ImmutableWrite<T> extends Holders.Invalidator {
    boolean compareAndSet(Immutable<T> prev, Immutable<T> next, long delay);
    default boolean compareAndSet(Immutable<T> prev, Immutable<T> next) {
        return compareAndSet(prev, next, NO_DELAY);
    }
}
