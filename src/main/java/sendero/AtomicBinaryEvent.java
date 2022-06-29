package sendero;

import sendero.switchers.Switchers;
/**AtomicBinaryEvent is intended as a binary regressive event mediator between nodes within the reactive system,
 * a system which is inherently concurrent in nature.*/
public interface AtomicBinaryEvent extends Switchers.Switch {
    boolean start();
    boolean shutoff();
    boolean isOff();

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
