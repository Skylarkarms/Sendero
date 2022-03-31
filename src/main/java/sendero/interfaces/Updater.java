package sendero.interfaces;

import java.util.function.UnaryOperator;

public interface Updater<T> {
    /**The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.*/
    void update(UnaryOperator<T> update);
    void update(long delay, UnaryOperator<T> update);
}
