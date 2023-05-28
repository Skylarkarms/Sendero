package sendero;

import sendero.interfaces.BooleanConsumer;
import sendero.switchers.Switchers;
/**AtomicBinaryEvent is intended as a binary regressive event mediator between nodes within the reactive system, <p>
 * a system inherently concurrent. <p>
 * It adds 2 more states to the non-concurrent Switch: <p>
 *         private static final int NOT_SET = -2, SHUT_DOWN = -1, ON = 1, OFF = 0; (see AtomicBinaryEventConsumer) <p>
 * these states prevent: <p>
 *  A) double overlapping signals between registration("start") && subsequent on(). <p>
 *  B) double overlapping signals between off() & subsequent unregistration("shutdown") <p>
 *
 * Once a shutdown has been reached, no more signals will be received and a new allocation will be required.
 * */
public interface AtomicBinaryEvent extends Switchers.Switch {
    boolean start();
    boolean shutoff();
    boolean isOff();

    /**Transforms a non-concurrent switch to an atomicBinaryEvent. <p>
     * The object will possess overlap countermeasures, supported by AtomicBinaryEventConsumer's inner state. <p>
     * The object won't have access to onStart() and onDestroyed() methods. <p>
     * Once the event reaches a shutoff, no new events will be received. <p>
     * If required, a new allocation for Switch must be created.
     * */
    static AtomicBinaryEvent from(Switchers.Switch aSwitch) {
        return new AtomicBinaryEventConsumer() {
            @Override
            protected void onStateChange(boolean isActive) {
                if (isActive) aSwitch.on();
                else aSwitch.off();
            }
        };
    }

    static AtomicBinaryEvent base(BooleanConsumer listener) {
        return new AtomicBinaryEventConsumer() {
            @Override
            protected void onStateChange(boolean isActive) {
                listener.accept(isActive);
            }
        };
    }

    AtomicBinaryEvent DEFAULT = new AtomicBinaryEvent() {
        @Override
        public boolean start() {
            return false;
        }

        @Override
        public boolean shutoff() {
            return false;
        }

        @Override
        public boolean isOff() {
            return false;
        }

        @Override
        public boolean on() {
            return false;
        }

        @Override
        public boolean off() {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    };
    default boolean isDefault() {
        return this == DEFAULT;
    }
}
