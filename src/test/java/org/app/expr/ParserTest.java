package org.app.expr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    @ParameterizedTest(name = "[{index}] Валидное выражение: ''{0}''")
    @ValueSource(strings = {
            "x", "42", "_var123",
            "-x", "+100", "-(a)", "+(x + y)",
            "a + b", "10 * 20", "x / y", "a - b",
            "a + b - c * d / e",
            "1 + 2 + 3 + 4 + 5",
            "(a)", "((b))", "(x + y) * z",
            "-x * (y + 10)",
            "+a - b * c / (d + e)",
            "((a + b) * (c - d)) / (e + (f * g))",
            "   x   ",
            "  -  x  *  (  y  +  10  )  "
    })
    @DisplayName("Позитивные тесты: разбор корректных выражений по РБНФ")
    void testValidExpressions(String expression) {
        ExpressionParser parser = new ExpressionParser(expression);
        assertTrue(parser.parse(), () -> "Парсер должен успешно разобрать: " + expression);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidCasesWithDescriptions")
    @DisplayName("Позитивные тесты с описанием категорий синтаксиса")
    void testValidExpressionsWithDescriptions(String description, String expression) {
        ExpressionParser parser = new ExpressionParser(expression);
        assertDoesNotThrow(parser::parse, () -> "Не должно быть исключения для категории: " + description);
    }

    private static Stream<Arguments> provideValidCasesWithDescriptions() {
        return Stream.of(
                Arguments.of("Минимальное имя", "a"),
                Arguments.of("Минимальная константа", "0"),
                Arguments.of("Унарная операция с переменной", "-a"),
                Arguments.of("Унарный плюс со скобками", "+(a - b)"),
                Arguments.of("Множественная вложенность скобок", "(((x)))"),
                Arguments.of("Длинное выражение с чередованием операторов", "a + b * c - d / e + f")
        );
    }

    @ParameterizedTest(name = "[{index}] Невалидное выражение ''{0}'': ожидается ошибка ''{1}''")
    @CsvSource({
            "'-x * (y + ',      'ожидался операнд'",
            "'',                'ожидался операнд'",
            "'(x + y',          'пропущена закрывающая скобка'",
            "'a + b )',          'лишние символы'",
            "'a + * b',          'недопустимый символ операнда'",
            "'-',               'ожидался операнд'",
            "'+ * 5',            'недопустимый символ операнда'"
    })
    @DisplayName("Негативные тесты: проверка выброса исключений при синтаксических ошибках")
    void testInvalidExpressionsCsv(String expression, String expectedErrorMessage) {
        ExpressionParser parser = new ExpressionParser(expression == null ? "" : expression);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                parser::parse,
                () -> "Парсер должен выбросить RuntimeException для: " + expression
        );

        assertTrue(
                exception.getMessage().contains(expectedErrorMessage),
                () -> String.format("Ожидалось сообщение с '%s', но получено: '%s'", expectedErrorMessage, exception.getMessage())
        );
    }
}