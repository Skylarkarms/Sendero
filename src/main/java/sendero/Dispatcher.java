package sendero;

import sendero.pairs.Pair;

abstract class Dispatcher<T> {

    /**coldDispatch is trigger by Sendero's inner communication*/
    void coldDispatch(Pair.Immutables.Int<T> t) {}

    /**dispatch is trigger by client input*/
    void dispatch(long delay,Pair.Immutables.Int<T> t) {}

    protected void onSwapped(T prev, T next) {}
}
