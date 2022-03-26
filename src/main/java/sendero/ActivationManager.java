package sendero;

import sendero.event_registers.BinaryEventRegisters;
import sendero.functions.Functions;
import sendero.interfaces.BooleanConsumer;
import sendero.switchers.Switchers;

import java.util.function.BooleanSupplier;

abstract class ActivationManager {

    private final Switchers.Switch switchRegister;

//    public enum EService {
//        INSTANCE;
//        private ExecutorService service;
//        private boolean active;
//        private final ThresholdListeners.ThresholdListener thresholdSwitch = ThresholdListeners.getAtomicOf(
//                0, 0,
//                isActive -> {
//                    if (isActive) create();
//                    else destroy();
//                }
//        );
//        public void create() {
//            if (thresholdSwitch.thresholdCrossed() && !active) {
//                active = true;
//                service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//            }
//        }
//        public void destroy() {
//            active = false;
//            service.shutdown();
//        }
//        public void increment() {thresholdSwitch.increment();}
//        public void decrement() {thresholdSwitch.decrement();}
//        public void execute(Runnable runnable) {
//            if (thresholdSwitch.thresholdCrossed()
//                    && service != null
//                    && !service.isShutdown()) {
//                service.execute(runnable);
//            }
//        }
//    }

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

    static Builder getBuilder() {
        return new Builder();
    }

    protected static class Builder {
        private BooleanConsumer activationListener;
        private boolean mutableActivationListener;

        public Builder withFixed(BooleanConsumer activationListener) {
            if (mutableActivationListener) throwException();
            this.activationListener = activationListener;
            this.mutableActivationListener = false;
            return this;
        }

        void throwException() {
            throw new IllegalStateException("Only one at a time.");
        }

        public Builder withMutable(boolean activationListener) {
            if (this.activationListener != null) throwException();
            this.mutableActivationListener = activationListener;
            return this;
        }

        protected ActivationManager build(BooleanSupplier deactivation) {
            return new ActivationManager(activationListener, mutableActivationListener) {
                @Override
                protected boolean deactivationRequirements() {
                    return deactivation.getAsBoolean();
                }
            };
        }
    }

    private static final Runnable ON_MUTABLE = Functions.emptyRunnable();

    private ActivationManager(BooleanConsumer fixedActivationListener, boolean mutableActivationListener) {
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

    protected abstract boolean deactivationRequirements();

    protected boolean tryActivate() {
        return switchRegister.on();
    }

    protected boolean tryDeactivate() {
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

    protected boolean clearActivationListener() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).unregister() != null;
    }

    protected BooleanConsumer getAndClearActivationListener() {
        return ((BinaryEventRegisters.BinaryEventRegister)switchRegister).unregister();
    }

    protected boolean expectClearActivationListener(BooleanConsumer activationListener) {
        return ((BinaryEventRegisters.BinaryEventRegister.Atomic)switchRegister).unregister(activationListener);
    }

    protected boolean isIdle() {
        return !switchRegister.isActive();
    }

    boolean isMutable() {
        return this.thrower == ON_MUTABLE;
    }
}

