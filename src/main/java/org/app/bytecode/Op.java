package org.app.bytecode;

public enum Op {
    PUSH, LOAD, STORE,
    ADD, SUB, MUL, DIV,
    POW, FACT,
    LT, GT, LE, GE, EQ, NE,
    DUP, POP,
    JMP_TRUE_KEEP, JMP_FALSE_KEEP,
    PRINT, JMP, JMP_FALSE, HALT
}