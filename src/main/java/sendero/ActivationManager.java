package sendero;

import sendero.event_registers.BinaryEventRegisters;
import sendero.functions.Functions;
import sendero.switchers.Switchers;

abstract class ActivationManager implements Switchers.Switch {

    /**BinaryEventRegisters handle both the onStart and onShutdown states of each AtomicEventListener.
     * Each of which should only be handled during swap concurrent operations.
     * The user should only care of both "on" and "off" operations, both which override onStart, but are overridden by shutdown
     * If this state register is required by and external dependecy it could in theory be chacked as instanceof AtomicEventListener and gain access to it's inner functions*/
    private final Switchers.Switch stateRegister;

    protected ActivationManager() {
        stateRegister = Switchers.getAtomic();
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
        this.stateRegister = fixedActivationListener != null ?
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
        return stateRegister.on();
    }

    @Override
    public boolean off() {
        if (deactivationRequirements()) {
            return stateRegister.off();
        }
        return false;
    }

    protected void setActivationListener(AtomicBinaryEvent listener) {
        thrower.run();
        ((BinaryEventRegisters.BinaryEventRegister) stateRegister).register(listener);
    }

    protected boolean swapActivationListener(AtomicBinaryEvent expect, AtomicBinaryEvent set) {
        thrower.run();
        return ((BinaryEventRegisters.BinaryEventRegister.Atomic) stateRegister).swapRegister(expect, set) == expect;
    }

    protected boolean activationListenerIsSet() {
        return ((BinaryEventRegisters.BinaryEventRegister) stateRegister).isRegistered();
    }

    protected boolean clearActivationListener() {
        return !((BinaryEventRegisters.BinaryEventRegister.Atomic) stateRegister).unregister().isDefault();
    }

    @Override
    public boolean isActive() {
        return stateRegister.isActive();
    }

}

