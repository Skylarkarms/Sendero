package sendero;

class ActivePathListener<T> implements InputMethodBinder<T> {
    private final ActivationManager manager;
    private final Appointers.BasePathListenerImpl<T> basePathListener;

    Holders.StreamManager<T> getStreamManager() {
        return basePathListener.getStreamManager();
    }

    public ActivePathListener(ActivationManager manager, Appointers.BasePathListenerImpl<T> basePathListener) {
        this.manager = manager;
        this.basePathListener = basePathListener;
    }

    @Override
    public <S, P extends BasePath<S>> Void bind(P basePath, Builders.InputMethods<T, S> inputMethod) {
        AtomicBinaryEvent next = basePathListener.bind(basePath, inputMethod);
        if (next != null) {
            manager.setActivationListener(
                    next
            );
        }
        return null;
    }

    public void forcedSet(AtomicBinaryEvent activationListener) {
        manager.swapActivationListener(
                basePathListener.getAndClear(), activationListener
        );
    }

    protected boolean unbound() {
        final AtomicBinaryEvent binaryEventConsumer = basePathListener.getAndClear();
        if (binaryEventConsumer != null) return manager.swapActivationListener(binaryEventConsumer, AtomicBinaryEvent.DEFAULT);
        return false;
    }
}
