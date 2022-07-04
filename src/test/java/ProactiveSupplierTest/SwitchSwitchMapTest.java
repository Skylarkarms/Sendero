package ProactiveSupplierTest;

import sendero.BasePath;
import sendero.Gate;
import sendero.Path;

import java.util.HashMap;
import java.util.Map;

public class SwitchSwitchMapTest {

    public static final class Result {
        final boolean set;
        final int version;
        final String title;

        @Override
        public String toString() {
            return "Result {" +
                    "\n set =" + set +
                    ",\n version =" + version +
                    ",\n title =" + title +
                    "}";
        }

        Result(boolean set, int version, String title) {
            this.set = set;
            this.version = version;
            this.title = title;
        }

        boolean isEqualTo(Result other) {
            return other != null &&
                    other.set == set
                    && other.title.equals(title)
                    && other.version == version;
        }
    }

    final Gate.Acceptor<Boolean> isActivePath = new Gate.Acceptor<>(
            op -> op.excludeIn(Boolean::equals).withInitial(false)
    );

    public void delayedAccept(long secs, boolean value) {
        sleepRun(secs,
                () -> isActivePath.accept(value));
    }

    private void sleepRun(long secs, Runnable runnable) {
        new Thread(
                () -> {
                    try {
                        Thread.sleep(secs);
                        runnable.run();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        ).start();
    }

    private static final String NOT_SET = "NOT_SET";
    private static final String SET = "SET";

    static final class TitleVerMapClass {
        final Map<String, Integer> titleVersionMap = new HashMap<>();
        {
            titleVersionMap.put(NOT_SET, 0);
            titleVersionMap.put(SET, 1);
        }
    }

    Path<TitleVerMapClass> titleVerMapClassPath = new Gate.Acceptor<>(new TitleVerMapClass());

    Gate.Acceptor<String> titlePath = new Gate.Acceptor<>(
            op -> op.excludeIn(String::equals).withInitial(
                    NOT_SET
            )
    );

    public enum titleType {
        set(SET), not_set(NOT_SET);
        final String title;

        titleType(String title) {
            this.title = title;
        }
    }

    public void delayedAccept(long secs, titleType titleType) {
        sleepRun(
                secs,
                () -> titlePath.accept(titleType.title)
        );
    }

    private static final String RESULT_KEY = "RESULT_KEY";

    {
        isActivePath.forkSwitch(
                Result::isEqualTo,
                isActive ->
                        titlePath.forkSwitch(title ->
                                titleVerMapClassPath.forkMap(
                                        titleVerMapClass -> {
                                            int ver = titleVerMapClass.titleVersionMap.get(title);
                                            return new Result(
                                                    isActive,
                                                    ver,
                                                    title
                                            );
                                        }
                                )
                        )
        ).store(RESULT_KEY);
    }

    public static Path<Result> getResult() {
        return BasePath.getStore().get(
                RESULT_KEY, Result.class
        );
    }
}
