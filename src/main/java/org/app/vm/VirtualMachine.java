package org.app.vm;

import org.app.bytecode.Instruction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class VirtualMachine {
    private final List<Instruction> code;
    private final int[] memory = new int[256];
    private final Deque<Integer> stack = new ArrayDeque<>();
    private int ip = 0;

    public VirtualMachine(List<Instruction> code) {
        this.code = code;
    }

    public void run() {
        while (ip < code.size()) {
            Instruction ins = code.get(ip++);
            switch (ins.op) {
                case PUSH: stack.push(ins.arg); break;
                case LOAD: stack.push(memory[ins.arg]); break;
                case STORE: memory[ins.arg] = stack.pop(); break;
                case ADD: { int b = stack.pop(), a = stack.pop(); stack.push(a + b); break; }
                case SUB: { int b = stack.pop(), a = stack.pop(); stack.push(a - b); break; }
                case MUL: { int b = stack.pop(), a = stack.pop(); stack.push(a * b); break; }
                case DIV: {
                    int b = stack.pop(), a = stack.pop();
                    if (b == 0) throw new RuntimeException("Деление на ноль");
                    stack.push(a / b);
                    break;
                }
                case POW: {
                    int b = stack.pop(), a = stack.pop();
                    if (b < 0) throw new RuntimeException("Отрицательная степень: " + b);
                    int r = 1;
                    for (int i = 0; i < b; i++) r *= a;
                    stack.push(r);
                    break;
                }
                case FACT: {
                    int n = stack.pop();
                    if (n < 0) throw new RuntimeException("Факториал отрицательного числа: " + n);
                    int r = 1;
                    for (int i = 2; i <= n; i++) r *= i;
                    stack.push(r);
                    break;
                }
                case LT: { int b = stack.pop(), a = stack.pop(); stack.push(a < b ? 1 : 0); break; }
                case GT: { int b = stack.pop(), a = stack.pop(); stack.push(a > b ? 1 : 0); break; }
                case LE: { int b = stack.pop(), a = stack.pop(); stack.push(a <= b ? 1 : 0); break; }
                case GE: { int b = stack.pop(), a = stack.pop(); stack.push(a >= b ? 1 : 0); break; }
                case EQ: { int b = stack.pop(), a = stack.pop(); stack.push(a == b ? 1 : 0); break; }
                case NE: { int b = stack.pop(), a = stack.pop(); stack.push(a != b ? 1 : 0); break; }
                case DUP: stack.push(stack.peek()); break;
                case POP: stack.pop(); break;
                case JMP_TRUE_KEEP: if (stack.peek() != 0) ip = ins.arg; break;
                case JMP_FALSE_KEEP: if (stack.peek() == 0) ip = ins.arg; break;
                case PRINT: System.out.println(stack.pop()); break;
                case JMP: ip = ins.arg; break;
                case JMP_FALSE: if (stack.pop() == 0) ip = ins.arg; break;
                case HALT: return;
            }
        }
    }
}