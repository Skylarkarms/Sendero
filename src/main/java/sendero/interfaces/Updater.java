package sendero.interfaces;

import java.util.function.UnaryOperator;

/**Any successful update will trigger a dispatch event to all observers.
 * An update is considered unsuccessful If the same instance is returned,
 * or If any of the Logic gates prevent its swap.
 * By default, an update will not proceed if the same instance is returned*/
public interface Updater<T> {
    /**The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     * @return: the updated value. || null if Predicate test == false*/
    T updateAndGet(UnaryOperator<T> update);
    /**The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     * @return: the updated value. || null if Predicate test == false*/
    T getAndUpdate(UnaryOperator<T> update);

    void update(long delay, UnaryOperator<T> update);
}
