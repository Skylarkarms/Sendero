import sendero.Gates;
import sendero.Merge;
import sendero.Path;

import java.util.function.UnaryOperator;

public class ForkedMultiplication {
    private final Gates.In<Integer> in = new Gates.In<>(2);
    private final Gates.Out.Single<Integer> resultOutput;
    public ForkedMultiplication() {
        Path<Integer> firstFork = in.forkMap(
                (UnaryOperator<Integer>) integer -> {
                    int firstForkRes = integer * 2;
                    System.out.println("First fork result is: " + firstForkRes);
                    return firstForkRes;
                }
        );
        Path<Integer> secondFork = in.forkMap(
                (UnaryOperator<Integer>) integer -> {
                    int secondForkResult = integer * 3;
                    System.out.println("Second fork result si: " + secondForkResult);
                    return secondForkResult;
                }
        );
        Merge<Integer> finalResult = new Merge<>(2, integer -> true).from(
                firstFork,
                integerUpdater -> integer -> integerUpdater.update(
                        mergeInteger -> {
                            int firstMerge = mergeInteger + integer;
                            System.out.println("First merge is: " + firstMerge + "\n of prev: " + mergeInteger + ", \n and next: " + integer);
                            return firstMerge;
                        }
                )
        ).from(
                secondFork,
                integerUpdater -> integer -> integerUpdater.update(
                        mergedInteger -> {
                            int secondMerge = mergedInteger + integer;
                            System.out.println("Second merge is: " + secondMerge + "\n of prev: " + mergedInteger + ", \n and next: " + integer);
                            return secondMerge;
                        }
                )
        );
        resultOutput = finalResult.out(Gates.Out.Single.class);
        resultOutput.register(
                System.out::println
        );
    }

    public void commence() {
        for (int i = 1; i < 6; i++) {
            int finalI = i;
            System.out.println("integer is: " +finalI);
            in.update(
                    integer -> {
                        int res = integer + finalI;
                        System.out.println("first sum is: " + res);
                        return res;
                    }
            );
        }
        try {
            Thread.sleep(2000);
            resultOutput.unregister();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
