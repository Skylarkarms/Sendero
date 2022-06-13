package sendero;

@FunctionalInterface
interface InputMethodSwapper<T, Next> {
    /**@return next AtomicBinaryEventConsumer
     *    null if:
     *    same parameters as before
     *    contention failed*/
    <S, P extends BasePath<S>> Next bind(P basePath, Builders.InputMethods<T, S> inputMethod);
}
