package org.app.bytecode;

public class Instruction {
    public Op op;
    public int arg;

    public Instruction(Op op, int arg) {
        this.op = op;
        this.arg = arg;
    }

    @Override
    public String toString() {
        return arg != -1 ? String.format("%-15s %d", op, arg) : op.toString();
    }
}