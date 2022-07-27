import sendero.Gate;
import sendero.Merge;
import sendero.Path;
import sendero.ProactiveSuppliers;

import java.util.function.UnaryOperator;

public class ForkedMultiplication {
    private final Gate.In<Integer> in = new Gate.In<>(2);
    private final Gate.Out.Single<Integer> resultOutput;

    private final ProactiveSuppliers.Unbound<Integer> activeSupplier = ProactiveSuppliers.unbound();
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
                builder -> builder.expectOut(
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
                ),
                Merge.Entry.get(
                        firstFork,
                        (ints, integer) -> {
                            System.err.println("<<<<From first fork is: " + integer);
                            System.err.println("<FROM FIRST UPDATE!!>, prev ARRAY is: " + ints);
                            int prev = ints[2];
                            System.err.println("<FROM FIRST UPDATE!!>, prev is: " + prev);
                            int[] clone = ints.clone();
                            clone[0] = integer;
                            clone[2] = solveArr(clone);
                            System.err.println("First merged total is: " + clone[2] + "\n of prev: " + prev + ", \n by adding: " + integer);
                            return clone;
                        }
                ),
                Merge.Entry.get(
                        secondFork,
                        (ints, integer) -> {
                            System.err.println("<<<<From second fork is: " + integer);
                            System.err.println("<FROM SECOND UPDATE!!>, prev ARRAY is: " + ints);
                            int prev = ints[2];
                            System.err.println("<FROM SECOND UPDATE!!>, prev is: " + prev);
                            int[] clone = ints.clone();
                            clone[1] = integer;
                            clone[2] = solveArr(clone);
                            System.err.println("Second merged total is: " + clone[2] + "\n of prev: " + prev + ", \n by adding: " + integer);
                            return clone;
                        }
                )
        );
        activeSupplier.bind(finalResult, ints -> ints[2]);
        Main.postpone(
                200,
                () -> {
                    System.out.println("SLEEPING... FOR SUPPLIER");
                    activeSupplier.get(
                            100,
                            integer -> {
                                System.out.println("Result is: " + integer);
                                activeSupplier.off();
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
        Main.postpone(
                2000,
                () -> {
                    System.err.println("<<<Unregistering>>>...");
                    resultOutput.unregister();
                }
        );
    }
}
