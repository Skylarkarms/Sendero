package sendero;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**Atomically accesses and alters the inner value of a BasePath without dispatching any changes downstream.<p>
 * The Value returned IS NOT GUARANTEED to be the most updated as the proactive get() is not in sync with inner dispatches from the system.<p>
 * The value returned WILL NOT BE the most updated IF the BasePath owner of this object, is NOT BEING ACTIVELY OBSERVED <p>
 * while changes occurred to parent nodes, <p>
 * in which case a ProactiveSupplier must be used instead.
 * */
public class Accessor<T> implements Supplier<T> {

    final AtomicReference<Immutable<T>> reference;
    private final AtomicBoolean isConnected = new AtomicBoolean();

    public Accessor(T value) {
        this.reference = new AtomicReference<>(Immutable.forFirstValue(value));
    }

    public Accessor() {
        this.reference = new AtomicReference<>(Immutable.getNotSet());
    }

    protected AtomicReference<Immutable<T>> getRef() {
        if (isConnected.compareAndSet(false, true)) {
            return reference;
        } else throw new IllegalStateException("Container is already set to an Appointer.");
    }

    private Immutable<T> getImm() {return reference.get();}

    public boolean compareAndSet(T expect, T set) {
        Immutable<T> prev, next;
        prev = getImm();
        if (!prev.match(expect)) return false;
        next = prev.newValue(set);
        return reference.compareAndSet(prev, next);
    }

    public final T updateAndGet(UnaryOperator<T> updateFunction) {
        Immutable<T> prev, next;
        T prevT, nextT;
        do {
            prev = getImm();
            prevT = prev.get();
            nextT = updateFunction.apply(prevT);
            next = prev.newValue(nextT);
        } while ((prevT != nextT) && !reference.compareAndSet(prev, next));
        return nextT;
    }

    @Override
    public T get() {
        return getImm().get();
    }
}
