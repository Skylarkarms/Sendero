package sendero;

import sendero.pairs.Pair;

import java.util.function.Predicate;

abstract class Dispatcher<T> {

    protected abstract void setExpectOutput(Predicate<T> expectOutput);

    /**coldDispatch is trigger by Sendero's inner communication*/
    protected void coldDispatch(Pair.Immutables.Int<T> t) {}

    /**dispatch is trigger by client input*/
    protected void dispatch(long delay,Pair.Immutables.Int<T> t) {}
//    protected void dispatch(boolean delayed,Pair.Immutables.Int<T> t) {}
//    protected void dispatch(Pair.Immutables.Int<T> t) {}
}
