package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.AtomicBinaryEventConsumer;
import sendero.interfaces.BinaryPredicate;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;

final class BaseUnboundSwitch<T> implements UnboundSwitch<T> {

    private final ActivePathListener<T> activePathListener;

    BaseUnboundSwitch(ActivePathListener<T> activePathListener) {
        this.activePathListener = activePathListener;
    }


    private Consumer<Pair.Immutables.Int<T>> getColdHolder() {
        return activePathListener.getColdHolder();
    }

    private <S> void baseConnect(
            BasePath<S> observable,
            Consumer<Pair.Immutables.Int<S>> exit
    ) {
        activePathListener.forcedSet(
                BinaryEventConsumers.fixedAppointer(observable, exit)
        );
    }

    @Override
    public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
        baseConnect(path, sInt -> {
            final int sInt1 = sInt.getInt();
            final S s = sInt.getValue();
            exit.apply(t ->
                    getColdHolder().accept(new Pair.Immutables.Int<>(sInt1, t)
                    )
            ).accept(s);
        });
    }

    @Override
    public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
        final Appointers.SysPathListener<T> appointer = new Appointers.SimpleAppointer<>(getColdHolder());
//        final Appointers.SysPathListener<T> appointer = new Appointers.SimpleAppointer<>(getColdHolder(), BinaryPredicate.always(true));
//        final Appointers.SimpleAppointer<T> appointer = new Appointers.SimpleAppointer<>(getColdHolder(), BinaryPredicate.always(true));

        final AtomicBinaryEventConsumer booleanConsumerAppointer = BinaryEventConsumers.producerHolderConnector(
                path,
                new Holders.DispatcherHolder<BasePath<T>>() {
                    @Override
                    void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
                        appointer.setAndStart(t.getValue());
                    }
                }::acceptVersionValue,
                switchMap::apply
        );

        final AtomicBinaryEventConsumer finalConsumer = new AtomicBinaryEventConsumer() {
            @Override
            protected void onStateChange(boolean isActive) {
                if (isActive) {
                    appointer.start();
                    booleanConsumerAppointer.on();
                }
                else {
                    appointer.stop();
                    booleanConsumerAppointer.off();
                }
            }
        };
        activePathListener.forcedSet(finalConsumer);
    }

    @Override
    public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
        final Appointers.SysPathListener<T> appointer = new Appointers.SimpleAppointer<>(getColdHolder());
//        final Appointers.SysPathListener<T> appointer = new Appointers.SimpleAppointer<>(getColdHolder(),BinaryPredicate.always(true));
//        final Appointers.SimpleAppointer<T> appointer = new Appointers.SimpleAppointer<>(getColdHolder(),BinaryPredicate.always(true));

        final AtomicBinaryEventConsumer booleanConsumerAppointer = BinaryEventConsumers.fixedAppointer(
                path,
                new Consumer<Pair.Immutables.Int<S>>() {
                    final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
                        @Override
                        void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
                            appointer.setAndStart(t.getValue());
                        }
                    };

                    {
                        domainHolder.expectIn(
                                tDomain -> tDomain != null && tDomain != domainHolder.get()
                        );
                    }
                    @Override
                    public void accept(Pair.Immutables.Int<S> sInt) {
                        S s = sInt.getValue();
                        int intS = sInt.getInt();
                        Consumer<S> computedExit = exit.apply(
                                tDomain -> domainHolder.acceptVersionValue(new Pair.Immutables.Int<>(intS, tDomain))
                        );
                        computedExit.accept(s);
                    }
                }
        );
        final AtomicBinaryEventConsumer finalActivationListener = new AtomicBinaryEventConsumer() {
            @Override
            protected void onStateChange(boolean isActive) {
                if (isActive) {
                    appointer.start();
                    booleanConsumerAppointer.on();
                }
                else {
                    appointer.stop();
                    booleanConsumerAppointer.off();
                }

            }
        };
        activePathListener.forcedSet(finalActivationListener);
    }
}
