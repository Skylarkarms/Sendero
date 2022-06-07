import sendero.Builders;
import sendero.Gate;
import sendero.Inputs;
import sendero.Path;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SwitchMapTest {
    private static final String TAG = "SwitchMapTest: ";
    public static final int ONE = 1, TWO = 2;
    private final IdPath id = new IdPath();
    private static final String NOT_SET_STRING = "NOT_SET_STRING";
    private final Gate.In<String> stringInOne = new Gate.In<>(NOT_SET_STRING), stringInTwo = new Gate.In<>("Hello");
    private final Map<Integer, Path<String>> base = new HashMap<>();
    {
        base.put(ONE, stringInOne);
        base.put(TWO, stringInTwo);
    }

    public enum State {
        not_set, set, changed
    }
    public static final class Obj {
        final State state;
        final String content;
        public static Obj NOT_SET = new Obj(State.not_set, NOT_SET_STRING);

        private State inferState(String newContent) {
            if (newContent.equals(NOT_SET_STRING)) return State.not_set;
            else {
                if (!newContent.equals(content)) return State.changed;
                else return State.set;
            }

        }

        public Obj update(String newContent) {
            System.err.println(TAG + "update: " + newContent);
            return new Obj(inferState(newContent), newContent);
        }

        private Obj(Obj obj) {
            this(obj.state, obj.content);
        }

        private Obj(State state, String content) {
            this.state = state;
            this.content = content;
        }

        boolean areEqual(Obj other) {
            if (this == other) return true;
            return other != null
                    && other.state.equals(state)
                    && other.content.equals(content);
        }

        @Override
        public String toString() {
            return "Obj{" +
                    "\n is set? =" + (this != NOT_SET) +
                    ",\n state=" + state +
                    ",\n content='" + content + '\'' +
                    '}';
        }
    }

    private final Path<Obj> composed = buildCompose(id, base);
    private final Path<Obj> forkOne = composed.forkMap(
            obj -> {
                System.err.println(TAG + "Fork ONE to occur<><>!!");
                return new Obj(obj.state, obj.content +
                        ",\n fork [ONE]");
            }
    );

    {
        System.err.println(TAG + "Fork ONE is: " + forkOne);
    }

    private final Path<Obj> forkTwo = forkOne.forkMap(
            obj -> {
                System.err.println(TAG + "Fork TWO to occur<><>!!");
                return new Obj(obj.state, obj.content +
                        ",\n fork [TWO].");
            }
    );

    private final Gate.Out.Many<Obj> many = forkTwo.out(Gate.Out.Many.class);

    public void observe(Consumer<Obj> objConsumer) {
        many.register(objConsumer);
    }

    public void unregister(Consumer<Obj> objConsumer) {
        many.unregister(objConsumer);
    }

    private static class IdPath extends Gate.In<Integer> {
        @Override
        protected void onStateChange(boolean isActive) {
            System.err.println("IdPath State changed is active? [[[" + isActive + "]]]");
        }

    }

    Path<Obj> buildCompose(
            Path<Integer> idSource,
            Map<Integer, Path<String>> base) {
        Path<Obj> res = idSource.forkSwitch(
                Obj::areEqual,
                id -> {
                    System.err.println("buildCompose: id is: " + id);
                    return base.get(id).forkUpdate(
                            Builders.withInitial(Obj.NOT_SET),
                            Obj::update
                    );
                }
        );
        System.err.println(TAG + "composed is: " + res);
        return res;
    }

    public void setSource(int sourceNumber) {
        id.accept(sourceNumber);
    }
}
