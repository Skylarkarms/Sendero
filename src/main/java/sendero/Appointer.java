package sendero;


import sendero.abstract_containers.Pair;
import sendero.lists.SimpleLists;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    /**This To-Many Appointer has Autonomy from Producer's activation state*/
    static class ConcurrentList<S> extends AtomicBinaryEventConsumer {

        final BooleanSupplier stateSupplier;

        final SimpleLists.LockFree.Snapshooter<AtomicBinaryEvent, Boolean> receptors;

        public ConcurrentList() {
            stateSupplier = this::isActive;
            receptors = SimpleLists.getSnapshotting(
                    AtomicBinaryEvent.class,
                    stateSupplier::getAsBoolean
            );
        }

        ConcurrentList(BooleanSupplier stateSupplier, AtomicBinaryEvent ... binaryEvents) {
            this.stateSupplier = stateSupplier;
            this.receptors = SimpleLists.getSnapshotting(
                    AtomicBinaryEvent.class,
                    stateSupplier::getAsBoolean
            );
            for (AtomicBinaryEvent e:binaryEvents) this.receptors.add(e);
        }

        ConcurrentList(BooleanSupplier stateSupplier, AtomicBinaryEvent first) {
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
        void onDestroyed() {
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
