package sendero;

interface Dispatcher<T> {

    /**coldDispatch is trigger by Sendero's inner communication
     * @param t*/
    void coldDispatch(Immutable<T> t);
//    void coldDispatch(Pair.Immutables.Int<T> t);

    /**dispatch is trigger by client input*/
    void dispatch(long delay, Immutable<T> t);
//    void dispatch(long delay,Pair.Immutables.Int<T> t);
}
