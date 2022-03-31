package sendero.interfaces;

@FunctionalInterface
public interface BooleanConsumer {
    void accept(boolean aBoolean);
    BooleanConsumer CLEARED = new BooleanConsumer() {
        @Override
        public void accept(boolean aBoolean) {
        }

        @Override
        public String toString() {
            return super.toString() + ": [CLEARED]";
        }
    };
    static BooleanConsumer cleared() {
        return CLEARED;
    }
    interface Bi<V> {
        void accept(boolean aBoolean, V value);
        Bi<?> CLEARED = new Bi<Object>() {
            @Override
            public void accept(boolean aBoolean, Object value) {
            }

            @Override
            public String toString() {
                return super.toString() + ": [CLEARED]";
            }
        };
    }
}
