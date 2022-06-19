package sendero;

import sendero.lists.Removed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;

/**The nature of an exit is for an input to be stable.
 * Since there may be multiple ways in which this class may be used, lets create a fixed and final Input Producer, and build
 * upon this base principle.*/
final class ExitAppointers<S, Entry> implements AtomicBinaryEvent{
    final Appointer.ConcurrentList<S> appointers;
    final Map<Entry, AtomicBinaryEvent> fieldEntries = new ConcurrentHashMap<>();

    public ExitAppointers(
    ) {
        appointers = new Appointer.ConcurrentList<>();
    }

    @SafeVarargs
    public ExitAppointers(
            BooleanSupplier stateSupplier,
            Function<Entry, AtomicBinaryEvent> eventFun,
            Entry ... entries
    ) {

        appointers = new Appointer.ConcurrentList<>(stateSupplier,
                Stream.of(entries).map(
                        entry -> {
                            AtomicBinaryEvent event = eventFun.apply(entry);
                            fieldEntries.put(entry, event);
                            return event;
                        }
                ).toArray(AtomicBinaryEvent[]::new)
        );
    }

    public ExitAppointers(
            BooleanSupplier stateSupplier,
            Function<Entry, AtomicBinaryEvent> eventFun,
            Entry first
    ) {

        appointers = new Appointer.ConcurrentList<>(stateSupplier,
                eventFun.apply(first));
    }

    public ExitAppointers(
            BooleanSupplier stateSupplier
    ) {

        appointers = new Appointer.ConcurrentList<>(stateSupplier);
    }

    public Removed remove(
            Entry exit
    ) {
        assert exit != null;
        AtomicBinaryEvent removed = fieldEntries.remove(exit);
        if (removed != null) {
            return appointers.remove(removed) ? Removed.last : Removed.removed;
        } else return Removed.failed;
    }

    public boolean add(
            Entry exit,
            Function<Entry, AtomicBinaryEvent> eventFunction
    ) {
        assert exit != null;
        AtomicBinaryEvent next =
                eventFunction.apply(exit), prev;
        prev = fieldEntries.putIfAbsent(exit, next);
        if (prev == null) {
            appointers.add(next);
            return true;
        } else return false;
    }

    @SafeVarargs
    public final void addAll(
            Function<Entry, AtomicBinaryEvent> eventFunction,
            Entry... exits
    ) {
        assert exits != null;
        for (Entry e:exits) add(e, eventFunction);
    }

    @Override
    public boolean start() {
        return appointers.start();
    }

    @Override
    public boolean shutoff() {
        return appointers.shutoff();
    }

    @Override
    public boolean isOff() {
        return appointers.isOff();
    }

    @Override
    public boolean on() {
        return appointers.on();
    }

    @Override
    public boolean off() {
        return appointers.off();
    }

    @Override
    public boolean isActive() {
        return appointers.isActive();
    }

    public void clear() {
        appointers.clear();
    }
}
