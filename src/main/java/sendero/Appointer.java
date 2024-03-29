package sendero;


import sendero.lists.SimpleLists;
import sendero.pairs.Pair;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**re-appointments will only return a signal if the value in cache(Receptor) has changed. */
class Appointer<A> extends AtomicBinaryEventConsumer {
    final BasePath<A> producer;
    final BasePath.Receptor<A> receptor;

    @Override
    void onStart() {
        receptor.invalidate();
    }

    Appointer(
            BasePath<A> producer,
            BasePath.Receptor<A> receptor
    ) {
        this.producer = producer;
        this.receptor = receptor;
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) appoint();
        else demote();
    }

    private void appoint() {
        producer.appoint(receptor);
    }
    private void demote() {
        producer.demotionOverride(receptor);
    }

    public boolean equalTo(BasePath<?> producer, InputMethod.Type<?, ?> receptor) {
        return this.producer.equals(producer)
                && this.receptor.equalTo(receptor);
    }

    @Override
    public String toString() {
        return "Appointer{" +
                "\n producer=" + producer +
                ",\n consumer=" + receptor +
                "} \n this is: Appointer" + "@" + hashCode();
    }

    /**This Appointer will default receptor on re-appointment, returning a signal on each re-appointment. */
    static class Defaulter<A> extends Appointer<A> {

        Defaulter(BasePath<A> producer, BasePath.Receptor<A> receptor) {
            super(producer, receptor);
        }

        @Override
        protected void onStateChange(boolean isActive) {
            super.onStateChange(isActive);
            if (!isActive) receptor.invalidate();
        }
    }

    /**This To-Many Appointer has Autonomy from Producer's activation state*/
    static class ConcurrentToMany extends AtomicBinaryEventConsumer {

        private final BooleanSupplier stateSupplier;

        private final SimpleLists.LockFree.Snapshooter<AtomicBinaryEvent, Boolean> receptors;

        public ConcurrentToMany() {
            stateSupplier = this::isActive;
            receptors = SimpleLists.getSnapshotting(
                    AtomicBinaryEvent.class,
                    stateSupplier::getAsBoolean
            );
        }

        ConcurrentToMany(BooleanSupplier stateSupplier, AtomicBinaryEvent ... binaryEvents) {
            this.stateSupplier = stateSupplier;
            this.receptors = SimpleLists.getSnapshotting(
                    AtomicBinaryEvent.class,
                    stateSupplier::getAsBoolean
            );
            for (AtomicBinaryEvent e:binaryEvents) this.receptors.add(e);
        }

        ConcurrentToMany(BooleanSupplier stateSupplier, AtomicBinaryEvent first) {
            this.stateSupplier = stateSupplier;
            this.receptors = SimpleLists.getSnapshotting(
                    AtomicBinaryEvent.class,
                    stateSupplier::getAsBoolean
            );
            this.receptors.add(first);
        }

        @Override
        protected void onStateChange(boolean isActive) {
            if (isActive) set(AtomicBinaryEvent::on);
            else set(AtomicBinaryEvent::off);
        }

        @Override
        void onStart() {
            set(AtomicBinaryEvent::start);
        }

        @Override
        protected final void onDestroyed() {
            set(AtomicBinaryEvent::shutoff);
        }

        private void set(Consumer<AtomicBinaryEvent> eventConsumer) {
            for (AtomicBinaryEvent r:receptors.copy()) eventConsumer.accept(r);
        }

        public void add(AtomicBinaryEvent receptor) {
            Pair.Immutables.Bool<Boolean> snap = receptors.snapshotAdd(receptor);
            receptor.shutoff();
            if (snap.value) receptor.start();
        }

        /**Returns true if last*/
        public boolean remove(AtomicBinaryEvent receptor) {
            boolean last = receptors.remove(receptor);
            receptor.shutoff();
            return last;
        }

        public void clear() {
            for (AtomicBinaryEvent e:receptors.clear()) e.shutoff();
        }

        @Override
        public String toString() {
            return "ConcurrentList{" +
                    "\n receptors=" + receptors +
                    "\n }";
        }
    }
}
