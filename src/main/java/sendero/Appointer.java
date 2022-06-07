package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;

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
    final BasePath.Receptor<A> receptor;

    @Override
    void onStart() {
        receptor.invalidate();
    }

    public static<S, T> AtomicBinaryEventConsumer producerConnector(
            BasePath<S> producer,
            Holders.StreamManager<T> consumer,
            Function<S, T> map) {
        return new Appointer<>(producer, BasePath.Receptor.withManagerInput(
                consumer,
                InputMethod.Type.map(map)
        ));
    }

    public static<T> AtomicBinaryEventConsumer producerConnector(
            BasePath<T> producer,
            Holders.StreamManager<T> consumer
    ) {
        return new Appointer<>(producer,
                BasePath.Receptor.withManagerInput(
                        consumer,
                        InputMethod.Type.identity()
                )
        );
    }

    static<S, T> AtomicBinaryEventConsumer producerHolderConnector(
            BasePath<S> producer,
            Holders.StreamManager<T> holder,
            BiFunction<T, S, T> update) {
        return new Appointer<>(producer,
                BasePath.Receptor.withManagerInput(
                        holder,
                        InputMethod.Type.update(update)
                )
        );
    }

    private Appointer(
            BasePath<A> producer,
            BasePath.Receptor<A> receptor
    ) {
        this.producer = producer;
        this.receptor = receptor;
    }

    @Override
    protected void onStateChange(boolean isActive) {
        if (isActive) appoint();
        else demote();
    }

    private void appoint() {
        producer.appoint(receptor);
    }
    private void demote() {
        producer.demotionOverride(receptor);
    }

    @Override
    <S> boolean equalTo(S s) {
        return s instanceof BasePath<?> && s.equals(producer);
    }

    @Override
    public String toString() {
        return "Appointer{" +
                "\n producer=" + producer +
                ",\n consumer=" + receptor +
                "} \n this is: Appointer" + "@" + hashCode();
    }
}
