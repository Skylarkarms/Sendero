package sendero;

import sendero.pairs.Pair;

import java.util.function.Predicate;

abstract class Dispatcher<T> {

    protected abstract void setExpectOutput(Predicate<T> expectOutput);

    protected void coldDispatch(Pair.Immutables.Int<T> t) {}
    protected void dispatch(Pair.Immutables.Int<T> t) {}
}
