package sendero.ipci;

import androidx.annotation.NonNull;

import com.juanandrade.elements.my_dependencies.tools.ObjectUtils;
import com.juanandrade.elements.my_dependencies.tools.collection_utils.CollectionUtils;
import sendero.utils.ObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**Inter Process Communication Interface*/
@FunctionalInterface
public interface IPCI<T> {
    class ID<C> {
        private static final Map<ID<?>, Object> idInstanceMap = new ConcurrentHashMap<>();
        volatile Class<C> aClass;
        private static final AtomicReferenceFieldUpdater<ID, Class>
                classUpdater = AtomicReferenceFieldUpdater.newUpdater(ID.class, Class.class, "aClass");

        private static final Map<String, ID<?>> idMap = new ConcurrentHashMap<>();

        private final String tag;

        public ID() {
            tag = null;
        }

        public ID(String tag) {
            this.tag = tag;
            ObjectUtils.assertNull(idMap.putIfAbsent(tag, this), "This tag " + tag + " already in map = " + CollectionUtils.toString(idMap.keySet()));
        }

        @SuppressWarnings("unchecked")
        public static<T> ID<T> getID(String tag) {
            return (ID<T>) idMap.get(tag);
        }

        public boolean isSynced() {
            return aClass != null;
        }

        private boolean setClass(Class<C> aClass) {
            return cas(null, aClass);
        }

        private boolean cas(Class<C> exp, Class<C> set) {
            return classUpdater.compareAndSet(this, exp, set);
        }

        private boolean eraseClass() {
            return cas(aClass, null);
        }

        @SuppressWarnings("unchecked")
        public C get() {
            C instance = (C) idInstanceMap.get(this);
            return instance != null ? mapAs(instance) : null;
        }

        public C getNonNull() {
            return mapAs(ObjectUtils.getNonNull(idInstanceMap.get(this), () -> "this key " + this + " has not been assigned to any IPC implementation yet" +
                    ",\n " + getInstanceMapString()));
        }

        private String getInstanceMapString() {
            return CollectionUtils.toString(idInstanceMap.keySet());
        }

        @SuppressWarnings("unchecked")
        void save(C instance) {
            if (setClass((Class<C>) instance.getClass())) {
                ObjectUtils.assertNull(idInstanceMap.putIfAbsent(this, instance),
                        () -> "ID " + this + " already saved on " + getInstanceMapString());
            } else {
                throw new IllegalStateException("This key " + this  + " already set to map " + getInstanceMapString());
            }
        }

        @SuppressWarnings("unchecked")
        C swap(C instance) {
            classUpdater.set(this, instance.getClass());
            return (C) idInstanceMap.put(this, instance);
        }

        @SuppressWarnings("unchecked")
        C delete() {
//            if (eraseClass()) {
//                return (C) idInstanceMap.remove(this);
//            }
            return ObjectUtils.testGet(this::eraseClass, () -> (C) idInstanceMap.remove(this));
        }

        C mapAs(Object o) {
            return aClass.cast(o);
        }

        @Override
        public String toString() {
            return "ClassID{" +
                    "aClass=" + aClass +
                    "}@" + hashCode();
        }

        public static final class Consumer<C> extends ID<java.util.function.Consumer<C>> {
            @Override
            java.util.function.Consumer<C> mapAs(Object o) {
                return super.mapAs(o);
            }
        }
    }
    ID<T> id();
    default void create(T instance) {
        match(id(), instance);
    }

    static<T> void match(ID<T> id, T instance) {
        ObjectUtils.exist(id, tid -> tid.save(instance));
    }
    static<T> void swap(ID<T> id, T instance) {
        ObjectUtils.exist(id, tid -> tid.swap(instance));
    }
    default T destroy() {
        return unMatch(id());
    }
    static<T> T unMatch(ID<T> id) {
        return ObjectUtils.map(id, ID::delete);
    }

    static void clearAll() {
        for (ID<?> id:ID.idInstanceMap.keySet()
             ) {
            id.delete();
        }
        ID.idInstanceMap.clear();
        ID.idMap.clear();
    }
    static void clearAll(ID<?> ... ids) {
        for (ID<?> id:ids
             ) {
            id.delete();
            ID.idInstanceMap.remove(id);
            if (id.tag != null) {
                ID.idMap.remove(id.tag);
            }
        }
    }
}
