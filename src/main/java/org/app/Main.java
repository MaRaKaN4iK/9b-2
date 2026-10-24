package org.app;

import org.app.bytecode.Instruction;
import org.app.compiler.SimpleBytecodeCompiler;
import org.app.vm.VirtualMachine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // ============================================================
        // 1. Демонстрация компилятора: степень и факториал
        // ============================================================
        String codeText =
                "int a = 2 ** 10;\n" +
                        "int b = 5!;\n" +
                        "int c = 2 ** 3 ** 2;\n" +
                        "int d = (2 + 3)!;\n" +
                        "int e = 2 * 3! + 1;\n" +
                        "print a; print b; print c; print d; print e;";

        SimpleBytecodeCompiler compiler = new SimpleBytecodeCompiler(codeText);
        List<Instruction> bytecode = compiler.compile();

        System.out.println("--- Сгенерированный байт-код (степень/факториал) ---");
        for (int i = 0; i < bytecode.size(); i++) {
            System.out.printf("[%2d] %s\n", i, bytecode.get(i));
        }

        System.out.println("\n--- Результат выполнения ---");
        new VirtualMachine(bytecode).run();

        // ============================================================
        // 2. Расчёт sin и cos через ряды Тейлора
        // ============================================================
        System.out.println("\n--- sin/cos через ряды Тейлора ---");

        double x = Math.PI / 6;
        System.out.printf("x = %.4f rad (%.1f°)%n", x, Math.toDegrees(x));

        System.out.printf("sin(%.4f) через ряд = %.10f, Math.sin = %.10f, разница = %.2e%n",
                x, sinTaylor(x, 10), Math.sin(x),
                Math.abs(sinTaylor(x, 10) - Math.sin(x)));

        System.out.printf("cos(%.4f) через ряд = %.10f, Math.cos = %.10f, разница = %.2e%n",
                x, cosTaylor(x, 10), Math.cos(x),
                Math.abs(cosTaylor(x, 10) - Math.cos(x)));

        System.out.println("\n  x(rad)     sin(ряд)      sin(Math)     cos(ряд)      cos(Math)");
        double[] angles = {0, Math.PI / 6, Math.PI / 4, Math.PI / 3, Math.PI / 2, Math.PI};
        for (double angle : angles) {
            System.out.printf("%8.4f  %12.8f  %12.8f  %12.8f  %12.8f%n",
                    angle,
                    sinTaylor(angle, 15), Math.sin(angle),
                    cosTaylor(angle, 15), Math.cos(angle));
        }

        // ============================================================
        // 3. Проверка: факториал через компилятор vs Java
        // ============================================================
        System.out.println("\n--- Проверка: факториал через компилятор vs Java ---");
        for (int n = 0; n <= 7; n++) {
            String src = "int f = " + n + "!; print f;";
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintStream old = System.out;
            System.setOut(new PrintStream(buf));
            try {
                new VirtualMachine(new SimpleBytecodeCompiler(src).compile()).run();
            } finally {
                System.setOut(old);
            }
            long compilerFact = Long.parseLong(buf.toString().trim());
            long javaFact = factorial(n);
            System.out.printf("%d! = компилятор: %d, Java: %d, совпадает: %b%n",
                    n, compilerFact, javaFact, compilerFact == javaFact);
        }
    }

    private static double sinTaylor(double x, int terms) {
        double sum = 0, term = x;
        for (int k = 0; k < terms; k++) {
            sum += term;
            term *= -x * x / ((2.0 * k + 2) * (2.0 * k + 3));
        }
        return sum;
    }

    private static double cosTaylor(double x, int terms) {
        double sum = 0, term = 1;
        for (int k = 0; k < terms; k++) {
            sum += term;
            term *= -x * x / ((2.0 * k + 1) * (2.0 * k + 2));
        }
        return sum;
    }

    private static long factorial(int n) {
        long r = 1;
        for (int i = 2; i <= n; i++) r *= i;
        return r;
    }
}

//package org.app;
//
//public class Main {
//    public static void main(String[] args) {
//
//    }
//}