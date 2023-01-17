package sendero;

import java.util.function.Supplier;

/**The system is composed of 2 types of reconfigurations:
 * 1) Data reconfigurations, for when Data versions are changed either by mapping operations, or by user input.
 * 2) Branch flattening operations, for when "flattening" operations occur (branch switch/switchMap), */
final class Immutable<T> implements Supplier<T> {
    private static final Immutable<?> NOT_SET = new Immutable<>(Values.NOT_SET, Values.NOT_SET, null);

    @SuppressWarnings("unchecked")
    public static<S> Immutable<S> getNotSet() {
        return (Immutable<S>) NOT_SET;
    }

    public static<S> Immutable<S> forFirstValue(S data) {
        return new Immutable<>(new Values(0, 1), Values.NOT_SET, data);
    }
    final Values local, upper;
    private final T data;

    Immutable(Values local, Values upper, T data) {
        this.local = local;
        this.upper = upper;
        this.data = data;
    }

    /**If upper.branch < top.local.branch => LesserThan.branch
     * *Swaps upper && dataSerial
     * */
    Immutable<T> branchSwitched(Values topValues, T nextData){
        return new Immutable<>(local.incrementBranch(), topValues, nextData);
    }

    /**If upper.dataSerial < top.local.dataSerial => LesserThan.data
     * *Swaps dataSerial ONLY
     * */
    Immutable<T> dataSwap(Values topValues, T nextData) {
        return new Immutable<>(local.incrementData(), topValues, nextData);
    }

    public Immutable<T> newValue(T value) {
        return new Immutable<>(local.incrementData(), upper, value);
    }

    Immutable<T> invalidate() {
        return isSet() ? new Immutable<>(local, Values.NOT_SET, data) : this;
    }

    @Override
    public T get() {
        return data;
    }

    static final class Values {
        final int branchSerial, dataSerial;
        public static final Values NOT_SET = new Values(0, 0);
        Values(int branchSerial, int dataSerial) {
            this.branchSerial = branchSerial;
            this.dataSerial = dataSerial;
        }

        Values incrementData() {
            return new Values(branchSerial, dataSerial + 1);
        }

        Values incrementBranch() {
            return new Values(branchSerial + 1, dataSerial);
        }

        enum LesserThan {
            branch, data, none;
            boolean isLesser() {
                return this != none;
            }
            private boolean isBranch() {
                return this == branch;
            }

            <S> Immutable<S> getNext(Immutable<S> prev, Values topValues, S nextData) {
                return isBranch() ?
                        prev.branchSwitched(topValues, nextData)
                        :
                        prev.dataSwap(topValues, nextData);
            }
        }

        LesserThan test(Values top) {
            if (branchSerial < top.branchSerial) return LesserThan.branch;
            else if (dataSerial < top.dataSerial) return LesserThan.data;
            else return LesserThan.none;
        }

        boolean lesserThanBranch(int branchSerial) {
            return this.branchSerial < branchSerial;
        }
        boolean lesserThanData(int dataSerial) {
            return this.dataSerial < dataSerial;
        }
        boolean equalTo(Values other) {
            return other == this;
        }

        @Override
        public String toString() {
            return "   Values{" +
                    "\n >> branchSerial=" + branchSerial +
                    ",\n >> dataSerial=" + dataSerial +
                    "\n}";
        }
    }

    Values.LesserThan test(Values top) {
        return upper.test(top);
    }

    public boolean isSet() {
        return this != NOT_SET;
    }

    public boolean isInvalid() {
        return /*isSet() &&*/ upper == Values.NOT_SET;
    }

    public int dataSerial() {
        return local.dataSerial;
    }

    boolean match(Values localValues) {
        return localValues.equalTo(local);
    }

    boolean match(T data) {
        return this.data == data;
    }

    @Override
    public String toString() {
        return "Immutable{" +
                "\n >> local=" + local +
                ",\n >> upper=" + upper +
                ",\n >> data=" + data +
                ",\n >> hash = " + hashCode() +
                "\n }";
    }
}
