package sendero;

import sendero.functions.Consumers;
import sendero.interfaces.BooleanConsumer;
import sendero.pairs.Pair;

import java.util.function.Consumer;
import java.util.function.Function;

abstract class AbsLink<T> implements sendero.Link.Unbound.UnboundSwitch<T>{

    private final ActivePathListener<T> activePathListener;

    protected AbsLink(ActivePathListener<T> activePathListener) {
        this.activePathListener = activePathListener;
    }


    protected abstract void onResult(Pair.Immutables.Int<T> tPair);

    private <S> void baseConnect(
            BasePath<S> observable,
            Consumer<Pair.Immutables.Int<S>> exit
    ) {
        activePathListener.forcedSet(
                Appointers.Appointer.booleanConsumerAppointer(observable, exit)
        );
    }

    @Override
    public <S> void bindFun(BasePath<S> path, Function<Consumer<? super T>, ? extends Consumers.BaseConsumer<S>> exit) {
        baseConnect(path, sInt -> {
            final int sInt1 = sInt.getInt();
            final S s = sInt.getValue();
            exit.apply(t -> onResult(new Pair.Immutables.Int<>(sInt1, t))
            ).accept(s);
        });
    }

    @Override
    public <S> void switchMap(BasePath<S> path, Function<S, ? extends BasePath<T>> switchMap) {
        final Appointers.SimpleAppointer<T> appointer = new Appointers.SimpleAppointer<>(this::onResult,t -> true);

        final BooleanConsumer booleanConsumerAppointer = Appointers.Appointer.booleanConsumerAppointer(
                path,
                new Holders.DispatcherHolder<BasePath<T>>() {
                    @Override
                    protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
                        appointer.setAndStart(t.getValue());
                    }
                }::acceptVersionValue,
                switchMap::apply
        );

        final BooleanConsumer finalConsumer = isActive -> {
            if (isActive) appointer.start();
            else appointer.stop();
            booleanConsumerAppointer.accept(isActive);
        };
        activePathListener.forcedSet(finalConsumer);
    }

    @Override
    public <S> void switchFun(BasePath<S> path, Function<Consumer<? super BasePath<T>>, ? extends Consumers.BaseConsumer<S>> exit) {
        final Appointers.SimpleAppointer<T> appointer = new Appointers.SimpleAppointer<>(this::onResult,t -> true);

        final BooleanConsumer booleanConsumerAppointer = Appointers.Appointer.booleanConsumerAppointer(
                path,
                new Consumer<Pair.Immutables.Int<S>>() {
                    final Holders.DispatcherHolder<BasePath<T>> domainHolder = new Holders.DispatcherHolder<BasePath<T>>() {
                        @Override
                        protected void coldDispatch(Pair.Immutables.Int<BasePath<T>> t) {
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
        final BooleanConsumer finalActivationListener = isActive -> {
            if (isActive) appointer.start();
            else appointer.stop();

            booleanConsumerAppointer.accept(isActive);
        };
        activePathListener.forcedSet(finalActivationListener);
    }
}
