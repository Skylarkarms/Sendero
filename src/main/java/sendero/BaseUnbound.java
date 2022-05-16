package sendero;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class BaseUnbound<T> implements UnboundLink<T>, BaseLink {

    final ActivePathListener<T> activePathListener;

    BaseUnbound(BasePath<T> basePath) {
        activePathListener = new ActivePathListener<>(basePath.manager, basePath.holderAppointer);

    }

    @Override
    public boolean isBound() {
        try {
            throw new IllegalAccessException("Should use basePath's isBound instead!");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean unbound() {
        return activePathListener.unbound();
    }

//    @Override
//    public <P extends BasePath<T>> void bind(P basePath) {
//        activePathListener.bind(basePath);
//    }

    @Override
    public <S, P extends BasePath<S>> void bindMap(P basePath, Function<S, T> map) {
        activePathListener.bindMap(basePath, map);
    }

    @Override
    public <S, P extends BasePath<S>> void bindUpdate(P basePath, BiFunction<T, S, T> update) {
        activePathListener.bindUpdate(basePath, update);
    }

    static final class LinkIllegalAccessException {
        private final Supplier<IllegalAccessException> getExc;
        LinkIllegalAccessException(Class<?> aClass) {
            getExc = () -> new IllegalAccessException(
                    aClass.getSimpleName() + " is unable to listen paths. \n " +
                            "Attempting to integrate both listen and bind would greatly diminish performance on both ends.");

        }

        void throwE() {
            try {
                throw getExc.get();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
