package sendero;

import sendero.event_registers.BinaryEventRegisters;
import sendero.functions.Functions;
import sendero.interfaces.BooleanConsumer;
import sendero.switchers.Switchers;

import java.util.function.BooleanSupplier;

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

    ActivationManager(BooleanConsumer fixedActivationListener, boolean mutableActivationListener) {
        this.switchRegister = fixedActivationListener != null ?
                BinaryEventRegisters.getAtomicWith(fixedActivationListener)
                :
                mutableActivationListener ?
                        BinaryEventRegisters.getAtomicRegister()
                        :
                        Switchers.getAtomic();
        this.thrower = mutableActivationListener ? ON_MUTABLE : createThrower();
    }

    protected ActivationManager(boolean withActivationListener) {
        switchRegister = withActivationListener ? BinaryEventRegisters.getAtomicRegister() : Switchers.getAtomic();
        thrower = withActivationListener ? Functions.emptyRunnable() : createThrower();
    }

    protected ActivationManager(BooleanConsumer fixedActivationListener) {
        switchRegister = BinaryEventRegisters.getAtomicWith(fixedActivationListener);
        thrower = createThrower();
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

    protected void setActivationListener(BooleanConsumer listener) {
        thrower.run();
        ((BinaryEventRegisters.BinaryEventRegister)switchRegister).register(listener);
    }

    protected boolean activationListenerIsSet() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).isRegistered();
    }

    protected boolean expectClearActivationListener(BooleanConsumer activationListener) {
        return ((BinaryEventRegisters.BinaryEventRegister.Atomic)switchRegister).unregister(activationListener);
    }

    protected boolean isIdle() {
        return !switchRegister.isActive();
    }

}

