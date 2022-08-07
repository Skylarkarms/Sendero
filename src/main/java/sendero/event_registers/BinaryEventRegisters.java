package sendero.event_registers;

import sendero.AtomicBinaryEvent;
import sendero.switchers.Switchers;

import java.util.HashMap;
import java.util.function.Consumer;

public final class BinaryEventRegisters {
    public static Switchers.Switch getAtomicRegister() {
        return new AtomicBinaryEventRegisterImpl();
    }
    public static Switchers.Switch getAtomicWith(AtomicBinaryEvent fixed) {
        return new AtomicWithFixed(fixed);
    }
    public interface BinaryEventRegister {
        void register(AtomicBinaryEvent booleanConsumer);
        AtomicBinaryEvent unregister();
        boolean isRegistered();
        interface Atomic extends BinaryEventRegister {
            /**@return prev, which is the same as expect if successful*/
            AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set);
        }
    }

    /**Keeps the BasePath's state alive while swapping event listeners*/
    static abstract class BaseEvent implements Switchers.Switch {
        final Switchers.Switch mainState = Switchers.getAtomic();

        abstract void onStateChanged(boolean isActive);

        @Override
        public boolean on() {
            boolean on = mainState.on();
            if (on) onStateChanged(true);
            return on;
        }

        @Override
        public boolean off() {
            boolean off = mainState.off();
            if (off) onStateChanged(false);
            return off;
        }

        @Override
        public boolean isActive() {
            return mainState.isActive();
        }
    }

    /**The link between Consumer and boolean state is loosely related.
     * Why aren't both states not strictly related and under the umbrella of a single Atomic value?
     * By having a loose relation we allow that:
     *      Boolean values can receive changes even without a Consumer attached.
     *      Boolean values can change while the Consumer is in a heavy contention process of change.
     *      Consumer can perform registrations freely without being tied to the on() or off() atomic pipeline. (the ifAccept() short-circuits if false)
     *
     * If both were heavily related, contention between boolean changes and consumer changes both with heavy traffic would be required to access a single atomic pipeline.
     * By using a loose relation between both, the only requirement is a volatile read of the current boolean state at the moment of new registration, with minor drawbacks explained
     *
     * This class can handle ONE register at a time.
     * For multiple registers see Appointer.ConcurrentToMany.
     * For multiple NON-concurrent see bellow class*/
    private static class AtomicBinaryEventRegisterImpl extends BaseEvent implements BinaryEventRegister.Atomic {
        private final ConsumerRegisters.StateAwareBinaryConsumerRegister register = ConsumerRegisters.getStateAware(this::isActive);

        @Override
        void onStateChanged(boolean isActive) {
            if (isActive) register.on();
            else register.off();
        }

        /**There are two caches for state, BaseEvent, which handles the Path's state (owner of this register), another within the AtomicBinaryEvent created on the fly.
         * AtomicBinaryEvent's state controls its own register and unregister functions, while This one retains state for the next incoming register (if any)*/
        @Override
        public void register(AtomicBinaryEvent booleanConsumer) {
            register.registerDispatch(booleanConsumer);
        }

        @Override
        public AtomicBinaryEvent unregister() {
            return register.unregisterDispatch();
        }

        @Override
        public boolean isRegistered() {
            return register.isRegistered();
        }

        @Override
        public AtomicBinaryEvent swapRegister(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
            return register.swapRegister(expect, set);
        }
    }

    private static class AtomicWithFixed extends BaseEvent implements BinaryEventRegister {

        private final AtomicBinaryEvent fixed;

        private AtomicWithFixed(AtomicBinaryEvent fixed) {
            this.fixed = fixed;
        }

        @Override
        void onStateChanged(boolean isActive) {
            if (isActive) fixed.on();
            else fixed.off();
        }

        @Override
        public void register(AtomicBinaryEvent booleanConsumer) {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public AtomicBinaryEvent unregister() {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public boolean isRegistered() {
            return true;
        }

        @Override
        public String toString() {
            return "AtomicWithFixed{" +
                    "\n mainState=" + mainState +
                    ",\n fixed=" + fixed +
                    "}@" + hashCode();
        }
    }

    public static class NonConcurrentToMany<K> extends BaseEvent {
        private final HashMap<K, Switchers.Switch> suppliersSet = new HashMap<>();

        protected <S extends Switchers.Switch> S putIfAbsent(K key, S aSwitch) {
            assert aSwitch != null;
            if (!suppliersSet.containsKey(key)) {
                suppliersSet.put(key, aSwitch);
                if (isActive()) aSwitch.on();
                return aSwitch;
            } throw new IllegalStateException("Key: " + key + " already present in Map.");
        }
        protected <S extends Switchers.Switch> boolean trueIfAbsent(K key, S aSwitch) {
            assert aSwitch != null;
            if (!suppliersSet.containsKey(key)) {
                suppliersSet.put(key, aSwitch);
                if (isActive()) aSwitch.on();
                return true;
            } return false;
        }
        protected boolean contains(K key) {
            return suppliersSet.containsKey(key);
        }
        protected void remove(K key) {
            Switchers.Switch removed = suppliersSet.remove(key);
            if (removed != null && removed.isActive()) removed.off();
        }
        protected void clear() {
            forEachSet(Switchers.Switch::off);
            suppliersSet.clear();
        }
        private void forEachSet(Consumer<Switchers.Switch> consumer) {
            for (Switchers.Switch s:suppliersSet.values()) consumer.accept(s);
        }

        @Override
        void onStateChanged(boolean isActive) {
            if (isActive) forEachSet(Switchers.Switch::on);
            else forEachSet(Switchers.Switch::off);
        }
    }

}
