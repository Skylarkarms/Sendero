import sendero.Builders;
import sendero.Gate;
import sendero.Merge;
import sendero.Path;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class ForkedMultiplication {
    private final Gate.In<Integer> in = new Gate.In<>(2);
    private final Gate.Out.Single<Integer> resultOutput;
    public ForkedMultiplication() {
        Path<Integer> firstFork = in.forkMap(
                (UnaryOperator<Integer>) integer -> {
                    int firstForkRes = integer * 2;
                    System.err.println("First fork result is: " + firstForkRes);
                    return firstForkRes;
                }
        );
        Path<Integer> secondFork = in.forkMap(
                (UnaryOperator<Integer>) integer -> {
                    int secondForkResult = integer * 3;
                    System.err.println("Second fork result si: " + secondForkResult);
                    return secondForkResult;
                }
        );
        Merge<int[]> finalResult = new Merge<>(
                (UnaryOperator<Builders.HolderBuilder<int[]>>) builder -> builder.expectOut(
                        ints -> {
                            System.err.println("Expect out size is: " + ints.length);
                            for (int i:ints
                            ) {
                                System.err.println("integers are: " + i);
                                if (i == 0) {
                                    System.err.println("HAS A ZERO!!!>>>");
                                    return false;
                                }
                            }
                            System.err.println("should pass???: " + true);
                            return true;
                        }
                ).withInitial(
                        new int[3]
                )
//                new int[3], ints -> {
//            System.err.println("Expect out size is: " + ints.length);
//            for (int i:ints
//                 ) {
//                System.err.println("integers are: " + i);
//                if (i == 0) {
//                    System.err.println("HAS A ZERO!!!>>>");
//                    return false;
//                }
//            }
//            System.err.println("should pass???: " + true);
//            return true;
//        }
        ).from(
                firstFork,
                integerUpdater -> integer -> {
                    System.err.println("<<<<From first fork is: " + integer + ", to updater: " + integerUpdater);
                    integerUpdater.update(
                            50,
                            ints -> {
                                System.err.println("<FROM FIRST UPDATE!!>, prev ARRAY is: " + ints + ", at updater: " + integerUpdater);
                                int prev = ints[2];
                                System.err.println("<FROM FIRST UPDATE!!>, prev is: " + prev + ", at updater: " + integerUpdater);
                                int[] clone = ints.clone();
                                clone[0] = integer;
                                clone[2] = solveArr(clone);
                                System.err.println("First merged total is: " + clone[2] + "\n of prev: " + prev + ", \n by adding: " + integer);
                                return clone;
                            }
                    );
                }
        ).from(
                secondFork,
                integerUpdater -> integer -> {
                    System.err.println("<<<<From second fork is: " + integer + ", to updater: " + integerUpdater);
                    integerUpdater.update(
                            50,
                            ints -> {
                                System.err.println("<FROM SECOND UPDATE!!>, prev ARRAY is: " + ints + ", at updater: " + integerUpdater);
                                int prev = ints[2];
                                System.err.println("<FROM SECOND UPDATE!!>, prev is: " + prev + ", at updater: " + integerUpdater);
                                int[] clone = ints.clone();
                                clone[1] = integer;
                                clone[2] = solveArr(clone);
                                System.err.println("Second merged total is: " + clone[2] + "\n of prev: " + prev + ", \n by adding: " + integer);
                                return clone;
                            }
                    );
                }
        );
        resultOutput = finalResult.out(Gate.Out.Single.class, ints -> ints[2]);
        resultOutput.register(
                integer -> System.out.println("Final result is: " + integer)
        );
    }

    private int solveArr(int[] arr) {
        return arr[0] + arr[1];
    }

    public void commence() {
        for (int i = 1; i < 6; i++) {
            int finalI = i;
            System.err.println("iteration is: " +finalI);
            in.update(
                    50,
                    integer -> {
                        int res = integer + finalI;
                        System.err.println("first sum is: " + res);
                        return res;
                    }
            );
        }
        try {
            Thread.sleep(2000);
            System.err.println("<<<Unregistering>>>...");
            resultOutput.unregister();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
