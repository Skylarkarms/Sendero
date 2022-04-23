package sendero.lists;

import sendero.pairs.Pair;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SimpleLists {
    private static final Object[] DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA = {};
    private static final int DEFAULT_CAPACITY = 10;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

//    public static<E> SimpleList.LockFree<E> getLockFree(Class<? super E> componentType) {
//        return new LockFreeImpl<>(componentType);
//    }
    public static<E, Snapshot> SimpleList.LockFree.Snapshotting<E, Snapshot> getSnapshotting(Class<? super E> componentType, Supplier<Snapshot> snapshotSupplier) {
        return new SnapshottingImpl<>( componentType, snapshotSupplier);
    }
    public interface SimpleList<E> extends Iterable<E>{
        boolean add(E element);
        void add(int index, E element);
        E get(int index);
        E set(int index, E element);
        E remove(int index);
        boolean remove(E element);
        void clear();
        boolean isEmpty();
        E[] getArray();
        E[] copy();
        interface LockFree<E> extends Iterable<E> {
            default void compute(int index, Consumer<E> eConsumer) {}
            /**returns true if this is the first item to be added*/
            boolean add(E element);
            /**returns true if this is the last item to be removed*/
            boolean remove(E element);

            boolean removeIf(Predicate<E> removeIf);

            interface Snapshotting<E, Snapshot> extends Iterable<E> {
                /**returns true if this is the first item to be added*/
                Pair.Immutables.Bool<Snapshot> add(E element);
                /**returns true if this is the last item to be removed*/
                boolean remove(E element);
                boolean removeIf(Predicate<E> removeIf);
                boolean isEmpty();
                E[] copy();

            }

            boolean isEmpty();
            E[] copy();

            final class Snapshot<E> {
                final E[] copy;
                final int version;
                final int size;

                private static<T> Snapshot<T> initialize(T[] EMPTY_ARRAY) {
                    return new Snapshot<>(EMPTY_ARRAY, 0, 0);
//                    return new Snapshot<>(Arrays.copyOf(EMPTY_ARRAY, 0), 0, 0);
                }

                private Snapshot(E[] copy, int version, int size/*, boolean removed*/) {
                    this.copy = copy;
                    this.version = version;
                    this.size = size;
                }
                private static<E> int find(E element, E[] prevArray, int prevSize) {
                    int i = 0;
                    if (element == null) {
                        for (; i < prevSize; i++)
                            if (prevArray[i] == null)
                                return i;
                    } else {
                        for (; i < prevSize; i++)
                            if (element.equals(prevArray[i]))
                                return i;
                    }
                    return -1;
                }
                private static<E> int findIf(Predicate<E> removeIf, E[] prevArray, int prevSize) {
                    int i = 0;
                    for (; i < prevSize; i++) {
                        E toTest = prevArray[i];
                        if (toTest != null && removeIf.test(toTest))
                            return i;
                    }
                    return -1;
                }
                private static final int retries = 3;
                private static <E> boolean remove(AtomicReference<Snapshot<E>> atomicReference, E element) {
                    //When a rapid succession of actions are executed, where an "add" followed by a "remove" is performed && the Collection has 0 items
                    //The remove may arrive before the add,
                    // And the final state will result in an item being added and size being 1, when it should have been 0.
                    // to prevent a wrong final state we must retry until the add arrives.

                    int i = 0;

                    Snapshot<E> prev = atomicReference.get(),
                            next = remove(element, prev);
                    boolean wasLast = prev.size == 1 && next != prev,
                            zeroSize = prev.size <= 0, shouldRetry;

//                    boolean wasLast, shouldRetry;
//                    Snapshot<E> prev, next;
//
                    for (boolean same = true;;i++) {
                        if (!same) {
                            zeroSize = prev.size <= 0;
                        }
                        shouldRetry = zeroSize && i <= retries;
                        if (!zeroSize && !same) {
                            next = remove(element, prev);
                            wasLast = prev.size == 1 && next != prev;
                        }
                        //If the item is not found after all retries, then next remains the same as prev and wasLast == false.
                        if (!shouldRetry && atomicReference.compareAndSet(prev, next)) return wasLast;
                        if (same == (same = (prev == (prev = atomicReference.get()))) && !shouldRetry) return false;
                    }

                }
                private static <E> boolean removeIf(AtomicReference<Snapshot<E>> atomicReference, Predicate<E> removeIf) {
                    boolean wasLast;
                    Snapshot<E> prev, next;
                    do {
                        prev = atomicReference.get();
                        next = removeIf(removeIf, prev);
                        wasLast = prev.size == 1 && next != prev;
                    } while (!atomicReference.compareAndSet(prev, next));
                    return wasLast;
                }
                private static <S, E> Pair.Immutables.Bool<S> remove(AtomicReference<Snapshot<E>> atomicReference, E element, Supplier<S> sSupplier) {
                    boolean wasLast;
                    Snapshot<E> prev, next;
                    S snap;
                    do {
                        prev = atomicReference.get();
                        next = remove(element, prev);
                        wasLast = prev.size == 1 && next != prev;
                        snap = sSupplier.get();
                    } while (!atomicReference.compareAndSet(prev, next));
                    return new Pair.Immutables.Bool<>(wasLast, snap);
                }
                @SuppressWarnings("unchecked")
                private static<E> Snapshot<E> remove(E element, Snapshot<E> prevSnap) {
                    E[] prevArray = prevSnap.copy;
                    int prevSize = prevSnap.size;
                    int index = find(element, prevArray, prevSize);
                    if (index != -1) {
                        int finalSize = prevSize - 1;
                        if (finalSize == 0) return new Snapshot<>((E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA, prevSnap.version + 1, 0);
                        return new Snapshot<>(fastRemove(prevArray, index, prevSize, finalSize), prevSnap.version + 1, finalSize/*, true*/);
                    }
                    return prevSnap;
                }
                @SuppressWarnings("unchecked")
                private static<E> Snapshot<E> removeIf(Predicate<E> removeIf, Snapshot<E> prevSnap) {
                    E[] prevArray = prevSnap.copy;
                    int prevSize = prevSnap.size;
                    int index = findIf(removeIf, prevArray, prevSize);
                    if (index != -1) {
                        int finalSize = prevSize - 1;
                        if (finalSize == 0) return new Snapshot<>((E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA, prevSnap.version + 1, 0);
                        return new Snapshot<>(fastRemove(prevArray, index, prevSize, finalSize), prevSnap.version + 1, finalSize/*, true*/);
                    }
                    return prevSnap;
                }
                private static<E> E[] fastRemove(E[] prevArray, int i, int prevSize, int newSize) {
                    E[] dest_arr = Arrays.copyOf(prevArray, prevSize);
                    if ((newSize) > i)
                        System.arraycopy(prevArray, i + 1, dest_arr, i, newSize - i);
                    dest_arr[newSize] = null;
                    return dest_arr;
                }

                private static<E> boolean add(AtomicReference<Snapshot<E>> atomicReference, E element, E[] componentArr) {
                    boolean first;
                    Snapshot<E> prev, next;
                    do {
                        prev = atomicReference.get();
                        next = Snapshot.add(element, prev, componentArr);
                        first = prev.size == 0;
                    } while (!atomicReference.compareAndSet(prev, next));
                    return first;
                }

                private static<S, E> Pair.Immutables.Bool<S> add(AtomicReference<Snapshot<E>> atomicReference, E element, Supplier<S> sSupplier, E[] componentArr) {
                    boolean first;
                    Snapshot<E> prev, next;
                    S snap;
                    do {
                        prev = atomicReference.get();
                        next = Snapshot.add(element, prev, componentArr);
                        first = prev.size == 0;
                        snap = sSupplier.get();
                    } while (!atomicReference.compareAndSet(prev, next));
                    return new Pair.Immutables.Bool<>(first, snap);
                }

                private static<T> Snapshot<T> add(T e, Snapshot<T> snapshot, T[] componentArr) {
//                private static<T> Snapshot<T> add(T e, Snapshot<T> snapshot) {
                    T[] nextArray, prevArray = snapshot.copy;
                    int nextSize, prevSize = snapshot.size, prevLength = prevArray.length, nextVersion = snapshot.version + 1;

                    nextArray = prevSize == 0 ?
//                    nextArray = prevSize == 0 ?
                            Arrays.copyOf(componentArr, DEFAULT_CAPACITY)
                            :

                    /*nextArray =*/ prevSize == prevLength ?
                            grow(prevSize + 1, prevArray)
                            :
                            Arrays.copyOf(prevArray, prevLength);
//                            Arrays.copyOf(prevArray, prevSize);

                    nextArray[prevSize] = e;
                    nextSize = prevSize + 1;
                    return new Snapshot<>(nextArray, nextVersion, nextSize);
                }
                private static <T> T[] grow(int minCapacity, T[] array) {
                    return Arrays.copyOf(array,
                            newCapacity(minCapacity, array));
                }
                private static<T> int newCapacity(int minCapacity, T[] array) {
                    // overflow-conscious code
                    int oldCapacity = array.length;
                    int newCapacity = oldCapacity + (oldCapacity >> 1);
                    if (newCapacity - minCapacity <= 0) {
                        if (array == DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA)
                            return Math.max(DEFAULT_CAPACITY, minCapacity);
                        if (minCapacity < 0) // overflow
                            throw new OutOfMemoryError();
                        return minCapacity;
                    }
                    return (newCapacity - MAX_ARRAY_SIZE <= 0)
                            ? newCapacity
                            : hugeCapacity(minCapacity);
                }
                private static int hugeCapacity(int minCapacity) {
                    if (minCapacity < 0) // overflow
                        throw new OutOfMemoryError();
                    return (minCapacity > MAX_ARRAY_SIZE)
                            ? Integer.MAX_VALUE
                            : MAX_ARRAY_SIZE;
                }

            }
        }
    }

    private static final class SnapshottingImpl<E, S> implements SimpleList.LockFree.Snapshotting<E, S> {
        private final E[] EMPTY_ELEMENT_ARRAY;

        private final Supplier<S> sSupplier;

        private final AtomicReference<SimpleList.LockFree.Snapshot<E>> core;

        @SuppressWarnings("unchecked")
        private SnapshottingImpl(Class<? super E> componentType, Supplier<S> sSupplier) {
            this.EMPTY_ELEMENT_ARRAY = (E[]) Array.newInstance(componentType, 0);
            SimpleList.LockFree.Snapshot<E> FIRST = SimpleList.LockFree.Snapshot.initialize((E[])DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA);
//            SimpleList.LockFree.Snapshot<E> FIRST = SimpleList.LockFree.Snapshot.initialize(EMPTY_ELEMENT_ARRAY);
            this.core = new AtomicReference<>(FIRST);
            this.sSupplier = sSupplier;
        }

        @Override
        public Pair.Immutables.Bool<S> add(E element) {
            return SimpleList.LockFree.Snapshot.add(core, element, sSupplier, EMPTY_ELEMENT_ARRAY);
        }

        @Override
        public boolean remove(E element) {
            return SimpleList.LockFree.Snapshot.remove(core, element);
        }

        @Override
        public boolean removeIf(Predicate<E> removeIf) {
            return SimpleList.LockFree.Snapshot.removeIf(core, removeIf);
        }

        @Override
        public boolean isEmpty() {
            return core.get().size == 0;
        }

        @Override
        public E[] copy() {
            SimpleList.LockFree.Snapshot<E> snapshot = core.get();
            E[] res = snapshot.copy;
            return res == DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA ? EMPTY_ELEMENT_ARRAY : trimToSize(snapshot);
        }

        public E[] trimToSize(SimpleList.LockFree.Snapshot<E> snap) {
            E[] copy = snap.copy;
            int size = snap.size;
            if (size < copy.length) return Arrays.copyOf(copy, size);
            else return copy;
        }

        @Override
        public Iterator<E> iterator() {
            return new ArrayIterator<>(copy());
        }
    }
//    private static final class LockFreeImpl<E> implements SimpleList.LockFree<E> {
//
//        private final AtomicReference<Snapshot<E>> core/* = new AtomicReference<>(FIRST)*/;
//
//        @SuppressWarnings("unchecked")
//        public LockFreeImpl(Class<? super E> componentType) {
//            E[] empty = (E[]) Array.newInstance(componentType, 0);
//            Snapshot<E> FIRST = Snapshot.initialize(empty);
//            this.core = new AtomicReference<>(FIRST);
//        }
//
//        @Override
//        public boolean add(E element) {
//            return Snapshot.add(core, element);
//        }
//
//        @Override
//        public boolean remove(E element) {
//            return Snapshot.remove(core, element);
//        }
//
//        @Override
//        public boolean removeIf(Predicate<E> removeIf) {
//            return Snapshot.removeIf(core, removeIf);
//        }
//
//        @Override
//        public boolean isEmpty() {
//            return core.get().size == 0;
//        }
//
//        @Override
//        public E[] copy() {
//            return core.get().copy;
//        }
//
//        @Override
//        public Iterator<E> iterator() {
//            return new ArrayIterator<>(copy());
//        }
//    }
//    private static class SimpleListImpl<E> implements SimpleList<E> {
//        private final E[] EMPTY_ELEMENT_DATA;
//
//        @SuppressWarnings("unchecked")
//        private SimpleListImpl(Class<? super E> componentType) {
//            this.EMPTY_ELEMENT_DATA = (E[]) Array.newInstance(componentType, 0);
//        }
//
//        @SuppressWarnings("unchecked")
//        private E[] array = (E[]) DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA;
//        private int size;
//
//        @Override
//        public boolean add(E element) {
//            add(element, array, size);
//            return true;
//        }
//
//        @Override
//        public void add(int index, E element) {
//            rangeCheckForAdd(index);
//            final int s;
//            Object[] elementData;
//            if ((s = size) == (elementData = this.array).length)
//                elementData = grow();
//            System.arraycopy(elementData, index,
//                    elementData, index + 1,
//                    s - index);
//            elementData[index] = element;
//            size = s + 1;
//        }
//        private void rangeCheckForAdd(int index) {
//            if (index > size || index < 0)
//                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
//        }
//        private String outOfBoundsMsg(int index) {
//            return "Index: "+index+", Size: "+size;
//        }
//        private void add(E e, E[] elementData, int s) {
//            if (s == elementData.length)
//                elementData = grow();
//            elementData[s] = e;
//            size = s + 1;
//        }
//        private E[] grow() {
//            return grow(size + 1);
//        }
//        private E[] grow(int minCapacity) {
//            return array = Arrays.copyOf(array,
//                    newCapacity(minCapacity));
//        }
//        private int newCapacity(int minCapacity) {
//            // overflow-conscious code
//            int oldCapacity = array.length;
//            int newCapacity = oldCapacity + (oldCapacity >> 1);
//            if (newCapacity - minCapacity <= 0) {
//                if (array == DEFAULT_CAPACITY_EMPTY_ELEMENT_DATA)
//                    return Math.max(DEFAULT_CAPACITY, minCapacity);
//                if (minCapacity < 0) // overflow
//                    throw new OutOfMemoryError();
//                return minCapacity;
//            }
//            return (newCapacity - MAX_ARRAY_SIZE <= 0)
//                    ? newCapacity
//                    : hugeCapacity(minCapacity);
//        }
//        private static int hugeCapacity(int minCapacity) {
//            if (minCapacity < 0) // overflow
//                throw new OutOfMemoryError();
//            return (minCapacity > MAX_ARRAY_SIZE)
//                    ? Integer.MAX_VALUE
//                    : MAX_ARRAY_SIZE;
//        }
//        public static <X extends RuntimeException>
//        void checkIndex(
//                int index,
//                int length
//        ) {
//            if (index < 0 || index >= length)
//                throw new IndexOutOfBoundsException("Index: " + index + " out of bounds: 0 - " + length);
//        }
//        @Override
//        public E get(int index) {
//            checkIndex(index, size);
//            return elementData(index);
//        }
//
//        private E elementData(int index) {
//            return array[index];
//        }
//
//        @Override
//        public E set(int index, E element) {
//            checkIndex(index, size);
//            E oldValue = elementData(index);
//            array[index] = element;
//            return oldValue;
//        }
//
//        @Override
//        public E remove(int index) {
//            checkIndex(index, size);
//            final E[] es = array;
//
//            E oldValue = es[index];
//            fastRemove(es, index);
//
//            return oldValue;
//        }
//
//        @Override
//        public boolean remove(E element) {
//            final E[] es = array;
//            final int size = this.size;
//            int i = 0;
//            found: {
//                if (element == null) {
//                    for (; i < size; i++)
//                        if (es[i] == null)
//                            break found;
//                } else {
//                    for (; i < size; i++)
//                        if (element.equals(es[i]))
//                            break found;
//                }
//                return false;
//            }
//            fastRemove(es, i);
//            return true;
//        }
//
//        private void fastRemove(E[] es, int i) {
//            final int newSize;
//            if ((newSize = size - 1) > i)
//                System.arraycopy(es, i + 1, es, i, newSize - i);
//            es[size = newSize] = null;
//        }
//
//        @Override
//        public void clear() {
//            for (int to = size, i = size = 0; i < to; i++)
//                array[i] = null;
//        }
//
//        @Override
//        public boolean isEmpty() {
//            return size == 0;
//        }
//
//        public E[] trimmedCopyToSize(E[] target, int targetSize) {
//            return targetSize < target.length
//                    && targetSize == 0 ?
//                    EMPTY_ELEMENT_DATA
//                    :
//                    Arrays.copyOf(target, targetSize);
//        }
//        @Override
//        public E[] getArray() {
//            array =  trimmedCopyToSize(array, size);
//            return array;
//        }
//
//        @Override
//        public Iterator<E> iterator() {
//            return new ArrayIterator<>(copy());
//        }
//
//        @Override
//        public E[] copy() {
//            return trimmedCopyToSize(array, size);
//        }
//    }

    private static final class ArrayIterator<T> implements Iterator<T> {

        private int index = 0;
        private final int lastIndex;
        private final T[] copy;

        public ArrayIterator(T[] copy) {
            this.copy = copy;
            lastIndex = copy.length - 1;
        }

        @Override
        public boolean hasNext() {
            return index < lastIndex;
        }

        @Override
        public T next() {
            return copy[index++];
        }
    }
}
