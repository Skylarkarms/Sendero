package sendero.event_registers;

import sendero.AtomicBinaryEvent;
import sendero.switchers.Switchers;

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
    static class BaseEvent implements Switchers.Switch {
        final Switchers.Switch mainState = Switchers.getAtomic();

        @Override
        public boolean on() {
            return mainState.on();
        }

        @Override
        public boolean off() {
            return mainState.off();
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
     * By using a loose relation between both, the only requirement is a volatile read of the current boolean state at the moment of new registration, with minor drawbacks explained*/
    private static class AtomicBinaryEventRegisterImpl extends BaseEvent implements BinaryEventRegister.Atomic {
        private final ConsumerRegisters.StateAwareBinaryConsumerRegister register = ConsumerRegisters.getStateAware(this::isActive);
        @Override
        public boolean on() {
            boolean isOn = super.on();
            if (isOn) register.on();
            return isOn;
        }

        @Override
        public boolean off() {
            boolean isOff = super.off();
            if (isOff) register.off();
            return isOff;
        }

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
        public boolean on() {
            boolean on = super.on();
            if (on) fixed.on();
            return on;
        }

        @Override
        public boolean off() {
            boolean off = super.off();
            if (off) fixed.off();
            return off;
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
}
