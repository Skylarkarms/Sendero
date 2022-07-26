package sendero;

interface Dispatcher<T> {
    /**coldDispatch is trigger by Sendero's inner communication
     * @param t immutable upstream message*/
    void coldDispatch(Immutable<T> t);

    /**dispatch is trigger by client input*/
    void dispatch(long delay, Immutable<T> t);
}
