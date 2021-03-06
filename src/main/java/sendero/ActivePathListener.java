package sendero;

class ActivePathListener<T> implements InputMethodBinder<T> {
    private final ActivationManager manager;
    private final Appointers.ConcurrentProducerSwapper<T> basePathListener;

    Holders.StreamManager<T> getStreamManager() {
        return basePathListener.getStreamManager();
    }

    public ActivePathListener(ActivationManager manager, Appointers.ConcurrentProducerSwapper<T> basePathListener) {
        this.manager = manager;
        this.basePathListener = basePathListener;
    }

    @Override
    public <S, P extends BasePath<S>> Void bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
        checkInstance(basePath);
        AtomicBinaryEvent next = basePathListener.bind(basePath, inputMethod);
        if (next != null) {
            manager.setActivationListener(
                    next
            );
        }
        return null;
    }

    private <S, P extends BasePath<S>> void checkInstance(P basePath) {
        if (basePath.activationManager == manager) {
            throw new IllegalStateException("Cannot bind BasePath<>: " + basePath + " to itself!");
        }
    }

    public void forcedSet(AtomicBinaryEvent activationListener) {
        manager.swapActivationListener(
                basePathListener.getAndClear(), activationListener
        );
    }

    protected boolean unbound() {
        final AtomicBinaryEvent binaryEventConsumer = basePathListener.getAndClear();
        return binaryEventConsumer != null && manager.swapActivationListener(binaryEventConsumer, AtomicBinaryEvent.DEFAULT);
    }
}
