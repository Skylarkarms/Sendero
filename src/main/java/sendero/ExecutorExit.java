package sendero;

import sendero.lists.Removed;

import java.util.function.Consumer;
import java.util.function.Function;

/**The nature of an exit is for an input to be stable.
 * Since there may be multiple ways in which this class may be used, lets create a fixed and final Input Producer, and build
 * upon this base principle.*/
public final class ExecutorExit<S> implements AtomicBinaryEvent{
    final Consumer<Runnable> executor;
    final ExitAppointers<S, Builders.ReceptorBuilder<S, ?>> exitAppointers;
    private final Function<Builders.ReceptorBuilder<S, ?>, AtomicBinaryEvent> eventFunction;

    public ExecutorExit(
            BasePath<S> producer,
            Consumer<Runnable> executor
    ) {
        this.executor = executor;
        eventFunction = sExit -> sExit.build(executor).toBinaryEvent(producer);
        exitAppointers = new ExitAppointers<>(
                this::isActive
        );
    }

    @SafeVarargs
    public ExecutorExit(
            BasePath<S> producer,
            Consumer<Runnable> executor,
            Builders.ReceptorBuilder<S, ?> ... exits
    ) {
        this.executor = executor;
        eventFunction = sExit -> sExit.build(executor).toBinaryEvent(producer);
        exitAppointers = new ExitAppointers<>(
                this::isActive,
                eventFunction,
                exits
        );
    }

    public ExecutorExit(
            BasePath<S> producer,
            Consumer<Runnable> executor,
            Builders.ReceptorBuilder<S, ?> first
    ) {
        this.executor = executor;
        eventFunction = sExit -> sExit.build(executor).toBinaryEvent(producer);
        exitAppointers = new ExitAppointers<>(
                this::isActive,
                eventFunction,
                first
        );
    }

    public<T> Removed remove(
            Builders.ReceptorBuilder<S, T> exit
    ) {
        assert exit != null;
        return exitAppointers.remove(exit);
    }

    public<T> boolean add(
            Builders.ReceptorBuilder<S, T> exit
    ) {
        assert exit != null;
        return exitAppointers.add(exit,
                eventFunction
        );
    }

    @SafeVarargs
    public final void addAll(
            Builders.ReceptorBuilder<S, ?>... exits
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
