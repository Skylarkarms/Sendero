package sendero.event_registers;

import sendero.interfaces.BooleanConsumer;
import sendero.switchers.Switchers;

public final class BinaryEventRegisters {
    public static BinaryEventRegister getAtomicRegister() {
        return new AtomicBinaryEventRegisterImpl();
    }
    public static BinaryEventRegister getAtomicWith(BooleanConsumer fixed) {
        return new AtomicWithFixed(fixed);
    }
    public static BinaryEventRegister getSequentialRegister() {
        return new Sequential();
    }
    public interface BinaryEventRegister extends Switchers.Switch {
        void register(BooleanConsumer booleanConsumer);
        BooleanConsumer unregister();
        BooleanConsumer unregisterAndOff();
        boolean isRegistered();
        interface Atomic extends BinaryEventRegister {
            boolean unregister(BooleanConsumer expect);
        }
    }
    private static class Sequential implements BinaryEventRegister {
        private final Switchers.Switch aSwitch = Switchers.getSequential();
        private final ConsumerRegister.BinaryRegisters.BinaryConsumerRegister.Sequential binaryConsumerRegister = ConsumerRegister.BinaryRegisters.getSequential();
        @Override
        public boolean on() {
            return binaryConsumerRegister.ifAccept(aSwitch::on, true);
        }

        @Override
        public boolean off() {
            return binaryConsumerRegister.ifAccept(aSwitch::off, false);
        }

        @Override
        public boolean isActive() {
            return aSwitch.isActive();
        }

        @Override
        public void register(BooleanConsumer booleanConsumer) {
            final BooleanConsumer last = binaryConsumerRegister.register(booleanConsumer);
            if (isActive()) {
                last.accept(true);
                booleanConsumer.accept(true);
            }
        }

        @Override
        public BooleanConsumer unregister() {
            BooleanConsumer last = binaryConsumerRegister.unregister();
            if (isActive()) last.accept(false);
            return last;
        }

//        @Override
//        public boolean unregister(BooleanConsumer expect) {
//            if (binaryConsumerRegister.isRegistered())
//            BooleanConsumer last = binaryConsumerRegister.unregister(expect, isActive());
//            boolean listenerFound = last != null;
//            if (listenerFound && isActive()) last.accept(false);
//            return listenerFound;
//        }

        @Override
        public BooleanConsumer unregisterAndOff() {
            aSwitch.off();
            return binaryConsumerRegister.unregister();
        }

        @Override
        public boolean isRegistered() {
            return binaryConsumerRegister.isRegistered();
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
        private final ConsumerRegister.BinaryRegisters.StateAwareBinaryConsumerRegister register = ConsumerRegister.BinaryRegisters.getStateAware(state::isActive);
        @Override
        public boolean on() {
            return register.ifAccept(state::on, true);
        }

        @Override
        public boolean off() {
            return register.ifAccept(state::off, false);
        }

        @Override
        public boolean isActive() {
            return state.isActive();
        }

        @Override
        public void register(BooleanConsumer booleanConsumer) {
            register.registerDispatch(booleanConsumer);
        }

        @Override
        public BooleanConsumer unregister() {
            return register.unregisterDispatch();
        }

        @Override
        public BooleanConsumer unregisterAndOff() {
            BooleanConsumer removed;
            removed = register.unregister();
            if (state.off() && removed != null) removed.accept(false);
            return removed;
        }

        @Override
        public boolean isRegistered() {
            return register.isRegistered();
        }

        @Override
        public boolean unregister(BooleanConsumer expect) {
            return false;
        }
    }

    private static class AtomicWithFixed implements BinaryEventRegister {
        private final Switchers.Switch state = Switchers.getAtomic();

        private final BooleanConsumer fixed;

        private AtomicWithFixed(BooleanConsumer fixed) {
            this.fixed = fixed;
        }

        @Override
        public boolean on() {
            boolean on = state.on();
            if (on) fixed.accept(true);
            return on;
        }

        @Override
        public boolean off() {
            boolean off = state.off();
            if (off) fixed.accept(false);
            return off;
        }

        @Override
        public boolean isActive() {
            return state.isActive();
        }

        @Override
        public void register(BooleanConsumer booleanConsumer) {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public BooleanConsumer unregister() {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public BooleanConsumer unregisterAndOff() {
            throw new IllegalStateException("Fixed");
        }

        @Override
        public boolean isRegistered() {
            return true;
        }
    }
}
