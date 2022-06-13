package sendero;

import sendero.switchers.Switchers;

public interface AtomicBinaryEvent extends Switchers.Switch {
    boolean start();
    boolean shutoff();
    boolean isOff();

    <P, R> boolean equalTo(P producer, R receptor);
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
        public <P, R> boolean equalTo(P producer, R receptor) {
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
