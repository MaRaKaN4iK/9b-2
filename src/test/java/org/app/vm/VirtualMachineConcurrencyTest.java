package org.app.vm;

import org.app.bytecode.Instruction;
import org.app.bytecode.Op;
import org.app.compiler.SimpleBytecodeCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты многопоточности (потоков) для VirtualMachine / SimpleBytecodeCompiler.
 * <p>
 * Каждая VirtualMachine имеет собственное состояние (стек, память, ip),
 * поэтому несколько ВМ должны корректно выполняться параллельно в разных
 * потоках без гонок и взаимного влияния.
 */
class VirtualMachineConcurrencyTest {

    private ExecutorService pool;
    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(8);
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        pool.shutdownNow();
    }

    // ---------- утилиты ----------

    /** Запускает выполнение кода в отдельном потоке, дожидается завершения и возвращает ошибку (если была). */
    private Throwable runInOwnThread(String src) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        Thread t = new Thread(() -> {
            try {
                List<Instruction> code = new SimpleBytecodeCompiler(src).compile();
                new VirtualMachine(code).run();
            } catch (Throwable e) {
                error[0] = e;
            } finally {
                done.countDown();
            }
        }, "vm-thread");

        t.start();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Поток не завершился за 10 секунд");
        return error[0];
    }

    // ---------- тесты ----------

    @Test
    @DisplayName("Одна ВМ выполняется в отдельном потоке и даёт корректный результат")
    void singleVmRunsInSeparateThread() throws Exception {
        Throwable err = runInOwnThread("int x = 2 + 3 * 4; print x;");
        assertNull(err, "ВМ не должна бросать исключение в рабочем потоке");
        String out = captured.toString(StandardCharsets.UTF_8).trim();
        assertEquals("14", out, "Результат вычисления должен быть изолирован в потоке ВМ");
    }

    @Test
    @DisplayName("Параллельный запуск независимых ВМ: каждый поток работает со своей памятью/стеком")
    void parallelIndependentVmsUseIsolatedState() throws Exception {
        int n = 50;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int a = i;
            final int b = i * 2;
            pool.submit(() -> {
                try {
                    startGate.await();
                    // a + b -> STORE 0 -> LOAD 0 -> минус b -> должно остаться ровно a.
                    // Если бы память/стек были общими между ВМ, результат «плывёт».
                    List<Instruction> code = new ArrayList<>();
                    code.add(new Instruction(Op.PUSH, a));
                    code.add(new Instruction(Op.PUSH, b));
                    code.add(new Instruction(Op.ADD, -1));
                    code.add(new Instruction(Op.STORE, 0));
                    code.add(new Instruction(Op.LOAD, 0));
                    code.add(new Instruction(Op.PUSH, b));
                    code.add(new Instruction(Op.SUB, -1));
                    code.add(new Instruction(Op.PRINT, -1));
                    code.add(new Instruction(Op.HALT, -1));
                    new VirtualMachine(code).run();
                } catch (Throwable t) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Все потоки должны завершиться");
        assertEquals(0, failures.get(), "Ни один поток не должен упасть");

        String[] lines = captured.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertEquals(n, lines.length, "Каждая ВМ должна напечатать ровно одно число");
        int[] actual = new int[n];
        for (int i = 0; i < n; i++) actual[i] = Integer.parseInt(lines[i].trim());
        Arrays.sort(actual);
        int[] expected = new int[n];
        for (int i = 0; i < n; i++) expected[i] = i;
        assertArrayEquals(expected, actual,
                "Изоляция памяти/стека каждой ВМ нарушена: результаты перемешались");
    }

    @Test
    @Timeout(60)
    @DisplayName("Стресс-тест гонки: N потоков × M итераций компиляции и выполнения ВМ")
    void stressManyThreadsNoRace() throws InterruptedException {
        final int threads = 16;
        final int iterations = 50;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threads; t++) {
            final long seed = t;
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < iterations; i++) {
                        int a = (int) ((seed * 31 + i) % 100);
                        int b = (int) ((seed * 17 + i) % 50);
                        String src = "int x = " + a + " + " + b + "; print x;";
                        List<Instruction> code = new SimpleBytecodeCompiler(src).compile();
                        new VirtualMachine(code).run();
                    }
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Стресс-тест должен завершиться");
        assertTrue(errors.isEmpty(), "Ошибки при параллельном выполнении: " + errors);

        String[] lines = captured.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertEquals(threads * iterations, lines.length,
                "Каждая ВМ должна напечатать ровно одно число");
        for (String line : lines) {
            assertDoesNotThrow(() -> Integer.parseInt(line.trim()),
                    "Вывод должен состоять только из чисел, найден: '" + line + "'");
        }
    }

    @Test
    @DisplayName("Две ВМ с одинаковым байт-кодом в разных потоках дают идентичный вывод")
    void identicalProgramsInDifferentThreadsGiveIdenticalOutput() throws Exception {
        String src = "int a = 2 ** 10; int b = 5!; int c = a + b; print c;";

        // Эталон: временно перехватываем вывод в свой буфер
        ByteArrayOutputStream refBuf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(refBuf, true, StandardCharsets.UTF_8));
        try {
            new VirtualMachine(new SimpleBytecodeCompiler(src).compile()).run();
        } finally {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        }
        String reference = refBuf.toString(StandardCharsets.UTF_8);
        assertEquals("1144" + System.lineSeparator(), reference); // 2**10 + 5! = 1024 + 120

        // Тот же код в 10 потоках — по очереди, чтобы вывод не смешивался
        for (int i = 0; i < 10; i++) {
            captured.reset();
            Throwable err = runInOwnThread(src);
            assertNull(err);
            assertEquals(reference, captured.toString(StandardCharsets.UTF_8),
                    "Поток #" + i + " должен дать тот же результат");
        }
    }

    @Test
    @DisplayName("Исключение внутри ВМ в рабочем потоке не затрагивает другие потоки")
    void exceptionInOneVmThreadIsContained() throws Exception {
        // Деление на ноль бросает RuntimeException внутри run()
        Throwable err = runInOwnThread("int x = 10 / 0; print x;");
        assertNotNull(err, "Ошибка деления на ноль должна быть поймана в рабочем потоке");
        assertTrue(err instanceof RuntimeException);

        // После падения одного потока остальные продолжают работать нормально
        captured.reset();
        Throwable err2 = runInOwnThread("int y = 40 / 2; print y;");
        assertNull(err2);
        String out = captured.toString(StandardCharsets.UTF_8).trim();
        assertEquals("20", out, "Успешный поток должен напечатать 20");
    }

    @Test
    @DisplayName("Совместный запуск ВМ через ExecutorService: все задачи отработали штатно")
    void submitVmsToExecutorService() throws Exception {
        int tasks = 32;
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= tasks; i++) {
            final int v = i;
            futures.add(pool.submit(() -> {
                String src = "int z = " + v + " * " + v + "; print z;";
                new VirtualMachine(new SimpleBytecodeCompiler(src).compile()).run();
            }));
        }
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS); // выбросит исключение, если задача упала
        }

        String[] lines = captured.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertEquals(tasks, lines.length);
        int[] actual = new int[lines.length];
        for (int i = 0; i < lines.length; i++) actual[i] = Integer.parseInt(lines[i].trim());
        Arrays.sort(actual);
        int[] expected = new int[tasks];
        for (int i = 0; i < tasks; i++) expected[i] = (i + 1) * (i + 1);
        assertArrayEquals(expected, actual, "Каждый квадрат должен быть напечатан ровно один раз");
    }
}
