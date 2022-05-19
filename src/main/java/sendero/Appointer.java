package sendero;

import sendero.pairs.Pair;

import java.util.function.Consumer;

public class Appointer<A> extends AtomicBinaryEventConsumer {
    public static final Appointer<?> CLEARED_APPOINTER = new Appointer<Object>(null, null) {

        @Override
        public boolean on() {
            return false;
        }

        @Override
        public boolean start() {
            return false;
        }

        @Override
        public boolean isCleared() {
            return true;
        }
    };

    final BasePath<A> producer;
    final Consumer<Pair.Immutables.Int<A>> toAppoint;

    public Appointer(BasePath<A> producer, Consumer<Pair.Immutables.Int<A>> toAppoint) {
        this.producer = producer;
        this.toAppoint = toAppoint;
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) appoint();
        else demote();
    }

    private void appoint() {
        producer.appoint(toAppoint);
    }
    private void demote() {
        producer.demotionOverride(toAppoint);
    }

    public<P> boolean equalTo(BasePath<P> basePath) {
        return basePath.equals(producer);
    }

    @Override
    public String toString() {
        return "Appointer{" +
                "producer=" + producer +
                ", consumer=" + toAppoint +
                "}\n" +
                " this is: Appointer" + "@" + hashCode();
    }
}
