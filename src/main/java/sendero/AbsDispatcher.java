package sendero;

import sendero.pairs.Pair;

import java.util.function.UnaryOperator;

import static sendero.functions.Functions.isIdentity;

abstract class AbsDispatcher<T> {
    static final int HOT = 0;

    final Holders.BaseTestDispatcher<T> baseTestDispatcher;

    protected AbsDispatcher(UnaryOperator<Builders.HolderBuilder<T>> builderOperator) {
        this.baseTestDispatcher =
                isIdentity(builderOperator) ?
                        new Holders.BaseTestDispatcher<>(this)
                        :
                        builderOperator.apply(Builders.getHolderBuild()).buildDispatcher(this);
    }

    /**coldDispatch is triggered by Sendero's inner communication*/
    void coldDispatch(Pair.Immutables.Int<T> t) {}

    /**dispatch is triggered by client input*/
    void dispatch(long delay,Pair.Immutables.Int<T> t) {}

    protected void onSwapped(T prev, T next) {}

    Pair.Immutables.Int<T> getSnapshot() {
        return baseTestDispatcher.getSnapshot();
    }

    boolean outPutTest(T toDispatch) {
        return baseTestDispatcher.outPutTest(toDispatch);
    }

    protected T get() {
        return baseTestDispatcher.get();
    }

    int getVersion() {
        return baseTestDispatcher.getVersion();
    }

}
