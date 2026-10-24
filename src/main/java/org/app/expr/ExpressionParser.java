package org.app.expr;

public class ExpressionParser {

    private final String text;
    private int pos = 0;

    public ExpressionParser(String text) {
        this.text = text;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        skipWhitespace();
        if (pos >= text.length()) return '\0';
        return text.charAt(pos);
    }

    private char getChar() {
        skipWhitespace();
        if (pos >= text.length()) return '\0';
        return text.charAt(pos++);
    }

    private boolean isBinOperation(char ch) {
        return ch == '+' || ch == '-' || ch == '*' || ch == '/';
    }

    public void parseExpr() {
        char next = peek();
        if (next == '+' || next == '-') {
            getChar();
        }
        parseOperand();
        while (isBinOperation(peek())) {
            getChar();
            parseOperand();
        }
    }

    public void parseOperand() {
        char ch = peek();

        if (ch == '\0') {
            throw new RuntimeException("Ошибка: ожидался операнд в конце строки (позиция " + pos + ")");
        }

        if (ch == '(') {
            getChar();
            parseExpr();
            if (peek() != ')') {
                throw new RuntimeException("Ошибка: пропущена закрывающая скобка ')' на позиции " + pos);
            }
            getChar();
            return;
        }

        if (Character.isDigit(ch)) {
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            return;
        }

        if (Character.isLetter(ch) || ch == '_') {
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
            return;
        }

        throw new RuntimeException("Ошибка: недопустимый символ операнда '" + ch + "' на позиции " + pos);
    }

    public boolean parse() {
        parseExpr();
        skipWhitespace();
        if (pos < text.length()) {
            throw new RuntimeException("Ошибка: лишние символы после выражения на позиции " + pos + " ('" + text.substring(pos) + "')");
        }
        return true;
    }
}