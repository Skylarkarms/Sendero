package sendero;

import sendero.event_registers.BinaryEventRegisters;
import sendero.functions.Functions;
import sendero.interfaces.BooleanConsumer;
import sendero.switchers.Switchers;
import sendero.threshold_listener.ThresholdListeners;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class ActivationManager {

    private final Switchers.Switch switchRegister;

    public enum EService {
        INSTANCE;
        private ExecutorService service;
        private boolean active;
        private final ThresholdListeners.ThresholdListener thresholdSwitch = ThresholdListeners.getAtomicOf(
                0, 0,
                isActive -> {
                    if (isActive) create();
                    else destroy();
                }
        );
        public void create() {
            if (thresholdSwitch.thresholdCrossed() && !active) {
                active = true;
                service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            }
        }
        public void destroy() {
            active = false;
            service.shutdown();
        }
        public void increment() {thresholdSwitch.increment();}
        public void decrement() {thresholdSwitch.decrement();}
        public void execute(Runnable runnable) {
            if (thresholdSwitch.thresholdCrossed()
                    && service != null
                    && !service.isShutdown()) {
                service.execute(runnable);
            }
        }
    }

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

    protected ActivationManager(boolean withActivationListener) {
        switchRegister = withActivationListener ? BinaryEventRegisters.getAtomicRegister() : Switchers.getAtomic();
        thrower = withActivationListener ? Functions.emptyRunnable() : createThrower();
    }

    protected ActivationManager(BooleanConsumer fixedActivationListener) {
        switchRegister = BinaryEventRegisters.getAtomicWith(fixedActivationListener);
        thrower = createThrower();
    }

    protected abstract boolean deactivationRequirements();

//    protected void manageDeactivation(
//            boolean attemptRemove
//    ) {
//        if (attemptRemove && deactivationRequirements()) {
//            tryDeactivate();
//        }
//    }
    protected boolean tryActivate() {
        return switchRegister.on();
    }
    protected void tryDeactivate() {
        if (deactivationRequirements()) {
            switchRegister.off();
        }
    }

    protected void setActivationListener(BooleanConsumer listener) {
        thrower.run();
        ((BinaryEventRegisters.BinaryEventRegister)switchRegister).register(listener);
    }

    protected boolean activationListenerIsSet() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).isRegistered();
    }

    protected boolean clearActivationListener() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).unregister() != null;
    }

    protected boolean isIdle() {
        return !switchRegister.isActive();
    }
}

