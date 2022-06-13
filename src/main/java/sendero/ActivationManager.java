package sendero;

import sendero.event_registers.BinaryEventRegisters;
import sendero.functions.Functions;
import sendero.switchers.Switchers;

abstract class ActivationManager implements Switchers.Switch {

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

    ActivationManager(AtomicBinaryEvent fixedActivationListener, boolean mutableActivationListener) {
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

    @Override
    public boolean on() {
        return switchRegister.on();
    }

    @Override
    public boolean off() {
        if (deactivationRequirements()) {
            return switchRegister.off();
        }
        return false;
    }

    protected void setActivationListener(AtomicBinaryEvent listener) {
        thrower.run();
        ((BinaryEventRegisters.BinaryEventRegister)switchRegister).register(listener);
    }

    protected boolean swapActivationListener(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
        thrower.run();
        return ((BinaryEventRegisters.BinaryEventRegister.Atomic)switchRegister).swapRegister(expect, set) == expect;
    }

    protected boolean activationListenerIsSet() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).isRegistered();
    }

    protected boolean clearActivationListener() {
        return !((BinaryEventRegisters.BinaryEventRegister.Atomic)switchRegister).unregister().isDefault();
    }

    @Override
    public boolean isActive() {
        return switchRegister.isActive();
    }

}

