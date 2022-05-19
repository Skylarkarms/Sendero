package sendero;

import sendero.event_registers.BinaryEventRegisters;
import sendero.functions.Functions;
import sendero.switchers.Switchers;

abstract class ActivationManager {

    private final Switchers.Switch switchRegister;

    protected ActivationManager() {
        switchRegister = Switchers.getAtomic();
        thrower = createThrower();
    }

    private final Runnable thrower;

    private Runnable createThrower() {
        return () -> {
            throw new IllegalStateException("Must use the according constructor: ObservableTest(boolean withActivationListener == true)");
        };
    }

    private static final Runnable ON_MUTABLE = Functions.emptyRunnable();

    ActivationManager(AtomicBinaryEventConsumer fixedActivationListener, boolean mutableActivationListener) {
        this.switchRegister = fixedActivationListener != null ?
                BinaryEventRegisters.getAtomicWith(fixedActivationListener)
                :
                mutableActivationListener ?
                        BinaryEventRegisters.getAtomicRegister()
                        :
                        Switchers.getAtomic();
        this.thrower = mutableActivationListener ? ON_MUTABLE : createThrower();
    }

    abstract boolean deactivationRequirements();

    boolean tryActivate() {
        return switchRegister.on();
    }

    boolean tryDeactivate() {
        if (deactivationRequirements()) {
            return switchRegister.off();
        }
        return false;
    }

    protected void setActivationListener(AtomicBinaryEventConsumer listener) {
        thrower.run();
        ((BinaryEventRegisters.BinaryEventRegister)switchRegister).register(listener);
    }

    protected boolean swapActivationListener(AtomicBinaryEventConsumer expect, AtomicBinaryEventConsumer set) {
        thrower.run();
        return ((BinaryEventRegisters.BinaryEventRegister.Atomic)switchRegister).swapRegister(expect, set) == expect;
    }

    protected boolean activationListenerIsSet() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).isRegistered();
    }

    protected boolean clearActivationListener() {
        return !((BinaryEventRegisters.BinaryEventRegister.Atomic)switchRegister).unregister().isCleared();
    }

    protected boolean isIdle() {
        return !switchRegister.isActive();
    }

}

