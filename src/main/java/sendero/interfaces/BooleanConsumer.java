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
    default boolean isCleared() {
        return this == CLEARED;
    }
}
