package org.app.compiler;

import org.app.bytecode.Instruction;
import org.app.bytecode.Op;

import java.util.List;
import java.util.Map;

public class Parser {
    private final Lexer lex;
    private final List<Instruction> code;
    private final Map<String, Integer> varIndices;
    private int nextVarIndex = 0;

    public Parser(Lexer lex, List<Instruction> code, Map<String, Integer> varIndices) {
        this.lex = lex;
        this.code = code;
        this.varIndices = varIndices;
    }

    public void parseProgram() {
        lex.skipWhitespace();
        while (!lex.isEof()) {
            parseStatement();
            lex.skipWhitespace();
        }
    }

    private void parseStatement() {
        lex.skipWhitespace();
        if (lex.isEof()) return;

        if (lex.startsWith("int")) {
            lex.advance(3);
            do {
                lex.skipWhitespace();
                String name = lex.parseIdentifier();
                int varIndex = nextVarIndex++;
                varIndices.put(name, varIndex);

                lex.skipWhitespace();
                if (lex.peek() == '=') {
                    lex.advance(1);
                    parseExpression();
                } else {
                    code.add(new Instruction(Op.PUSH, 0));
                }
                code.add(new Instruction(Op.STORE, varIndex));
                lex.skipWhitespace();
            } while (lex.match(','));
            lex.expect(';');
        }
        else if (lex.startsWith("print")) {
            lex.advance(5);
            parseExpression();
            code.add(new Instruction(Op.PRINT, -1));
            lex.expect(';');
        }
        else if (lex.startsWith("if")) {
            lex.advance(2);
            parseExpression();

            int jumpFalseIndex = code.size();
            code.add(new Instruction(Op.JMP_FALSE, -1));

            executeBlockContent();
            lex.skipWhitespace();

            if (lex.startsWith("else")) {
                lex.advance(4);
                int jumpElseIndex = code.size();
                code.add(new Instruction(Op.JMP, -1));

                code.get(jumpFalseIndex).arg = code.size();
                executeBlockContent();
                code.get(jumpElseIndex).arg = code.size();
            } else {
                code.get(jumpFalseIndex).arg = code.size();
            }
        }
        else if (lex.startsWith("while")) {
            lex.advance(5);
            int loopStart = code.size();

            parseExpression();

            int jumpExitIndex = code.size();
            code.add(new Instruction(Op.JMP_FALSE, -1));

            executeBlockContent();
            code.add(new Instruction(Op.JMP, loopStart));

            code.get(jumpExitIndex).arg = code.size();
        }
        else {
            String name = lex.parseIdentifier();
            Integer varIndex = varIndices.get(name);
            if (varIndex == null) {
                throw new RuntimeException("Неизвестная переменная: " + name);
            }
            lex.skipWhitespace();
            lex.expect('=');
            parseExpression();
            code.add(new Instruction(Op.STORE, varIndex));
            lex.expect(';');
        }
    }

    private void executeBlockContent() {
        lex.skipWhitespace();
        if (lex.peek() == '{') lex.advance(1);
        lex.skipWhitespace();
        while (!lex.isEof() && lex.peek() != '}') {
            parseStatement();
            lex.skipWhitespace();
        }
        if (lex.peek() == '}') lex.advance(1);
    }

    private void parseExpression() {
        parseLogicalAnd();
        lex.skipWhitespace();
        while (lex.startsWith("||")) {
            lex.advance(2);
            code.add(new Instruction(Op.DUP, -1));
            int jumpTrue = code.size();
            code.add(new Instruction(Op.JMP_TRUE_KEEP, -1));
            code.add(new Instruction(Op.POP, -1));
            parseLogicalAnd();
            code.get(jumpTrue).arg = code.size();
            lex.skipWhitespace();
        }
    }

    private void parseLogicalAnd() {
        parseEquality();
        lex.skipWhitespace();
        while (lex.startsWith("&&")) {
            lex.advance(2);
            code.add(new Instruction(Op.DUP, -1));
            int jumpFalse = code.size();
            code.add(new Instruction(Op.JMP_FALSE_KEEP, -1));
            code.add(new Instruction(Op.POP, -1));
            parseEquality();
            code.get(jumpFalse).arg = code.size();
            lex.skipWhitespace();
        }
    }

    private void parseEquality() {
        parseRelational();
        lex.skipWhitespace();
        while (true) {
            if (lex.startsWith("==")) {
                lex.advance(2); parseRelational();
                code.add(new Instruction(Op.EQ, -1));
            } else if (lex.startsWith("!=")) {
                lex.advance(2); parseRelational();
                code.add(new Instruction(Op.NE, -1));
            } else if (lex.startsWith("<>")) {
                lex.advance(2); parseRelational();
                code.add(new Instruction(Op.NE, -1));
            } else break;
            lex.skipWhitespace();
        }
    }

    private void parseRelational() {
        parseAdditive();
        lex.skipWhitespace();
        while (true) {
            if (lex.startsWith("<=")) {
                lex.advance(2); parseAdditive();
                code.add(new Instruction(Op.LE, -1));
            } else if (lex.startsWith(">=")) {
                lex.advance(2); parseAdditive();
                code.add(new Instruction(Op.GE, -1));
            } else if (lex.startsWith("==") || lex.startsWith("!=") || lex.startsWith("<>")) {
                break;
            } else if (lex.match('<')) {
                parseAdditive();
                code.add(new Instruction(Op.LT, -1));
            } else if (lex.match('>')) {
                parseAdditive();
                code.add(new Instruction(Op.GT, -1));
            } else break;
            lex.skipWhitespace();
        }
    }

    private void parseAdditive() {
        parseMultiplicative();
        lex.skipWhitespace();
        while (true) {
            if (lex.match('+')) {
                parseMultiplicative();
                code.add(new Instruction(Op.ADD, -1));
            } else if (lex.match('-')) {
                parseMultiplicative();
                code.add(new Instruction(Op.SUB, -1));
            } else break;
            lex.skipWhitespace();
        }
    }

    private void parseMultiplicative() {
        parsePower();
        lex.skipWhitespace();
        while (true) {
            if (lex.startsWith("**")) break;
            if (lex.match('*')) {
                parsePower();
                code.add(new Instruction(Op.MUL, -1));
            } else if (lex.match('/')) {
                parsePower();
                code.add(new Instruction(Op.DIV, -1));
            } else break;
            lex.skipWhitespace();
        }
    }

    private void parsePower() {
        parseFactorial();
        lex.skipWhitespace();
        if (lex.startsWith("**")) {
            lex.advance(2);
            parsePower();
            code.add(new Instruction(Op.POW, -1));
        }
    }

    private void parseFactorial() {
        parseTerm();
        lex.skipWhitespace();
        while (!lex.isEof() && lex.peek() == '!') {
            if (lex.charAtRelative(1) == '=') break; // !=, а не факториал
            lex.advance(1);
            code.add(new Instruction(Op.FACT, -1));
            lex.skipWhitespace();
        }
    }

    private void parseTerm() {
        lex.skipWhitespace();
        if (lex.match('(')) {
            parseExpression();
            lex.expect(')');
            return;
        }

        char c = lex.peek();
        if (Character.isDigit(c)) {
            int val = 0;
            while (!lex.isEof() && Character.isDigit(lex.peek())) {
                val = val * 10 + (lex.peek() - '0');
                lex.advance(1);
            }
            code.add(new Instruction(Op.PUSH, val));
        } else if (Character.isLetter(c)) {
            String name = lex.parseIdentifier();
            Integer varIndex = varIndices.get(name);
            if (varIndex == null) {
                throw new RuntimeException("Переменная не найдена: " + name);
            }
            code.add(new Instruction(Op.LOAD, varIndex));
        } else {
            throw new RuntimeException("Неожиданный символ в выражении: '" + c +
                    "' на позиции " + lex.getPos());
        }
    }
}