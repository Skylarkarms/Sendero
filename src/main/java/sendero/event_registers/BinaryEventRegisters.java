package sendero.event_registers;

import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.switchers.Switchers;

public final class BinaryEventRegisters {
    public static BinaryEventRegister getAtomicRegister() {
        return new AtomicBinaryEventRegisterImpl();
    }
    public static BinaryEventRegister getAtomicWith(AtomicBinaryEventConsumer fixed) {
        return new AtomicWithFixed(fixed);
    }
    public interface BinaryEventRegister extends Switchers.Switch {
        void register(AtomicBinaryEventConsumer booleanConsumer);
        AtomicBinaryEventConsumer unregister();
        boolean isRegistered();
        interface Atomic extends BinaryEventRegister {
            /**@return prev, which is the same as expect if successful*/
            AtomicBinaryEventConsumer swapRegister(AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set);

        }
    }

    /**The link between Consumer and boolean state is loosely related.
     * Why arent both states not strictly related and under the umbrella of a single Atomic value?
     * By having a loose relation we allow that:
     *      Boolean values can receive changes even without a Consumer attached.
     *      Boolean values can change while the Consumer is in a heavy contention process of change.
     *      Consumer can perform registrations freely without being tied to the on() or off() atomic pipeline. (the ifAccept() short-circuits if false)
     *
     * If both were heavily related, contention between boolean changes and consumer changes both with heavy traffic would be required to access a single atomic pipeline.
     * By using a loose relation between both, the only requirement is a volatile read of the current boolean state at the moment of new registration, with minor drawbacks explained*/
    private static class AtomicBinaryEventRegisterImpl implements BinaryEventRegister.Atomic {
        private final Switchers.Switch state = Switchers.getAtomic();
        private final ConsumerRegisters.BinaryRegisters.StateAwareBinaryConsumerRegister register = ConsumerRegisters.BinaryRegisters.getStateAware(state::isActive);
        @Override
        public boolean on() {
            boolean isOn = state.on();
            if (isOn) register.on();
            return isOn;
        }

        @Override
        public boolean off() {
            boolean isOff = state.off();
            if (isOff) register.off();
            return isOff;
        }

        @Override
        public boolean isActive() {
            return state.isActive();
        }

        @Override
        public void register(AtomicBinaryEventConsumer booleanConsumer) {
            register.registerDispatch(booleanConsumer);
        }

        @Override
        public AtomicBinaryEventConsumer unregister() {
            return register.unregisterDispatch();
        }

        @Override
        public boolean isRegistered() {
            return register.isRegistered();
        }

        @Override
        public AtomicBinaryEventConsumer swapRegister(AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set) {
            return register.swapRegister(expect, set);
        }
    }

    private static class AtomicWithFixed implements BinaryEventRegister {
        private final Switchers.Switch state = Switchers.getAtomic();

        private final AtomicBinaryEventConsumer fixed;

        private AtomicWithFixed(AtomicBinaryEventConsumer fixed) {
            this.fixed = fixed;
        }

        @Override
        public boolean on() {
            boolean on = state.on();
            if (on) fixed.on();
            return on;
        }

        @Override
        public boolean off() {
            boolean off = state.off();
            if (off) fixed.off();
            return off;
        }

        @Override
        public boolean isActive() {
            return state.isActive();
        }

        @Override
        public void register(AtomicBinaryEventConsumer booleanConsumer) {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public AtomicBinaryEventConsumer unregister() {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public boolean isRegistered() {
            return true;
        }
    }
}
