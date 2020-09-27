package com.red.app;

import sun.misc.Signal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.nio.channels.Pipe;
import java.util.function.Function;

public class App {
    private static boolean fComputed = false;
    private static boolean gComputed = false;
    private static final Ref<Integer> fResult = new Ref<>();
    private static final Ref<Integer> gResult = new Ref<>();

    private static Computations createComputations(Integer caseNo) {
        if (caseNo != null && 1 <= caseNo && caseNo <= 6) {
            return new DemoComputations(caseNo);
        }

        return new CustomComputations(x -> x, x -> x);
    }

    private static Integer getCaseNo(String[] args) {
        if (args.length == 2 && args[0].equals("-d")) {
            try {
                return Integer.valueOf(args[1]);
            }
            catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static int scanX() {
        System.out.print("x = ");
        try (var scanner = new Scanner(System.in)) {
            return scanner.nextInt();
        }
    }

    private static Thread startComputations(Function<Integer, Integer> computation, int arg, Pipe.SinkChannel sink) {
        Thread computationThread = new ComputationThread(computation, arg, sink);
        computationThread.start();
        return computationThread;
    }

    private static Pipe createNonBlockingPipe() throws IOException {
        Pipe pipe = Pipe.open();
        pipe.source().configureBlocking(false);
        return pipe;
    }

    private static boolean checkIfComputed(Pipe pipe, Ref<Integer> result) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Const.INT_SIZE);

        if (pipe.source().read(buffer) > 0) {
            result.setValue(buffer.flip().remaining() == 4 ? buffer.getInt() : null);

            return true;
        }

        return false;
    }

    private static boolean printIfComputed(Pipe pipe, Ref<Integer> result, String computationName) throws IOException {
        if (checkIfComputed(pipe, result)) {
            Integer resultValue = result.getValue();
            System.out.printf("%s has been computed. Result is %s\n",
                    computationName, resultValue != null ? resultValue : Const.UNDEFINED);

            return true;
        }

        return false;
    }

    private static boolean checkIfZero(Integer value) {
        return value != null && value == Const.OP_ZERO;
    }

    private static Integer binaryOperation(Integer fResult, Integer gResult) {
        if (checkIfZero(fResult) || checkIfZero(gResult)) {
            return Const.OP_ZERO;
        }

        if (fResult == null || gResult == null) {
            return null;
        }

        return fResult * gResult;
    }

    private static void onCancellation() {
        if (!fComputed && !gComputed) {
            System.out.println("\nAborted by user. f and g have not been computed");
        }
        else if (!fComputed) {
            System.out.printf("\nAborted by user. f has not been computed, g is not %d\n", Const.OP_ZERO);
        }
        else if (!gComputed){
            System.out.printf("\nAborted by user. g has not been computed, f is not %d\n", Const.OP_ZERO);
        }
        else {
            System.out.println("\nAborted by user");
        }
        System.exit(Const.CANCELLATION_EXIT_CODE);
    }

    public static void main(String[] args) {
        Signal.handle(new Signal(Const.CANCELLATION_SIGNAL), signal -> {
            System.out.println("\nAborted by user, x has not been provided");
            System.exit(Const.CANCELLATION_EXIT_CODE);
        });

        Computations computations = createComputations(getCaseNo(args));

        int x = scanX();
        try {
            Pipe fPipe = createNonBlockingPipe();
            Pipe gPipe = createNonBlockingPipe();
            Thread fThread = startComputations(computations::computeF, x, fPipe.sink());
            Thread gThread = startComputations(computations::computeG, x, gPipe.sink());

            Signal.handle(new Signal(Const.CANCELLATION_SIGNAL), signal -> {
                onCancellation();
            });

            while (!fComputed || !gComputed) {
                if (!fComputed) {
                    fComputed = printIfComputed(fPipe, fResult, "f");
                    if (fComputed && checkIfZero(fResult.getValue())) {
                        break;
                    }
                }

                if (!gComputed) {
                    gComputed = printIfComputed(gPipe, gResult, "g");
                    if (gComputed && checkIfZero(gResult.getValue())) {
                        break;
                    }
                }
            }
            fThread.interrupt();
            gThread.interrupt();

            Integer binaryOperationResult = binaryOperation(fResult.getValue(), gResult.getValue());
            System.out.printf("Binary operation result is %s\n",
                    binaryOperationResult != null ? binaryOperationResult : Const.UNDEFINED);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
