package sendero;

import sendero.lists.Removed;

import java.util.function.Function;

/**The nature of an exit is for an input to be stable.
 * Since there may be multiple ways in which this class may be used, lets create a fixed and final Input Producer, and lets build
 * upon this base principle.*/
public final class AppointerMap<K, V> implements AtomicBinaryEvent{
    final ExitAppointers<K, V> exitAppointers;
    private final Function<V, AtomicBinaryEvent> eventFunction;

    public AppointerMap(
            Function<V, AtomicBinaryEvent> eventFunction
    ) {
        this.eventFunction = eventFunction;
        exitAppointers = new ExitAppointers<>(
                this::isActive
        );
    }

    @SafeVarargs
    public AppointerMap(
            Function<V, AtomicBinaryEvent> eventFunction,
            V ... exits
    ) {
        this.eventFunction = eventFunction;
        exitAppointers = new ExitAppointers<>(
                this::isActive,
                eventFunction,
                exits
        );
    }

    public AppointerMap(
            Function<V, AtomicBinaryEvent> eventFunction,
            V first
    ) {
        this.eventFunction = eventFunction;
        exitAppointers = new ExitAppointers<>(
                this::isActive,
                eventFunction,
                first
        );
    }

    public<T> Removed remove(
            V exit
    ) {
        assert exit != null;
        return exitAppointers.remove(exit);
    }

    public<T> boolean add(
            V exit
    ) {
        assert exit != null;
        return exitAppointers.add(exit,
                eventFunction
        );
    }

    @SafeVarargs
    public final void addAll(
            V... exits
    ) {
        assert exits != null;
        exitAppointers.addAll(eventFunction, exits);
    }

    @Override
    public boolean start() {
        return exitAppointers.start();
    }

    @Override
    public boolean shutoff() {
        return exitAppointers.shutoff();
    }

    @Override
    public boolean isOff() {
        return exitAppointers.isOff();
    }

    @Override
    public boolean on() {
        return exitAppointers.on();
    }

    @Override
    public boolean off() {
        return exitAppointers.off();
    }

    @Override
    public boolean isActive() {
        return exitAppointers.isActive();
    }

    public void clear() {
        exitAppointers.clear();
    }
}
