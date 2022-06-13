package sendero;

class Appointer<A> extends AtomicBinaryEventConsumer {
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
        public boolean isDefault() {
            return true;
        }
    };

    final BasePath<A> producer;
    final BasePath.Receptor<A> receptor;

    @Override
    void onStart() {
        receptor.invalidate();
    }

    Appointer(
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
    public <P, R> boolean equalTo(P producer, R receptor) {
        return producer instanceof BasePath<?> && producer.equals(this.producer)
                && receptor instanceof InputMethod.Type<?, ?> && this.receptor.equalTo((InputMethod.Type<?, ?>) receptor);
    }

    @Override
    <P> boolean equalTo(P producer) {
        return producer instanceof BasePath<?> && producer.equals(this.producer);
    }

    @Override
    public String toString() {
        return "Appointer{" +
                "\n producer=" + producer +
                ",\n consumer=" + receptor +
                "} \n this is: Appointer" + "@" + hashCode();
    }
}
