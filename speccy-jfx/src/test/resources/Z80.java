package spectrum.jfx.z80port;

import spectrum.jfx.z80core.MemIoOps;

/**
 * Java port scaffold of the Z80 CPU core from c-code/*.cpp.
 * <p>
 * NOTE: This is an initial port that provides the CPU state, bus interfaces,
 * helper methods, and the main instruction fetch/decode skeleton with stubs
 * for opcode handlers. The full set of opcode implementations from the C++
 * sources is large and will be filled progressively.
 */
public class Z80 {
    // Bus
    private final MemIoOps memory;
    private final MemIoOps port;

    // Main registers (8-bit)
    private int A, F, B, C, D, E, H, L;           // AF, BC, DE, HL
    // Shadow registers
    private int A_, F_, B_, C_, D_, E_, H_, L_;    // AF', BC', DE', HL'

    // Index registers and others (16-bit)
    private int IX, IY;
    private int SP, PC;   // Stack and Program Counter

    // Special registers
    private int I;        // Interrupt vector
    private int R;        // Memory refresh
    private int MEMPTR;   // Internal temp per Z80 docs

    // Interrupt flip-flops and mode
    private boolean IFF1;
    private boolean IFF2;
    private int IM;       // 0,1,2

    // Other flags
    private boolean HALT;
    private boolean interruptPending;
    private boolean isNMOS = true; // behavior of X/Y flags in SCF/CCF

    // Flag bits
    private static final int FLAG_C = 0x01;
    private static final int FLAG_N = 0x02;
    private static final int FLAG_PV = 0x04;
    private static final int FLAG_X = 0x08; // undocumented flag 3
    private static final int FLAG_H = 0x10;
    private static final int FLAG_Y = 0x20; // undocumented flag 5
    private static final int FLAG_Z = 0x40;
    private static final int FLAG_S = 0x80;

    public Z80(MemIoOps memory, MemIoOps port) {
        this.memory = memory;
        this.port = port;

        reset();
    }

    public void reset() {
        // Zero registers; SP typical power-on could be 0xFFFF per C++ code
        A = F = B = C = D = E = H = L = 0;
        A_ = F_ = B_ = C_ = D_ = E_ = H_ = L_ = 0;
        IX = IY = 0;
        SP = 0xFFFF;
        PC = 0;
        I = 0;
        R = 0;
        MEMPTR = 0;
        IFF1 = false;
        IFF2 = false;
        IM = 0;
        HALT = false;
        interruptPending = false;
        isNMOS = true;
    }

    // ============= Public API =============

    /**
     * Execute a single instruction and return consumed T-states.
     */
    public int executeOneInstruction() {
        // Interrupt check
        if (IFF1 && interruptPending) {
            interruptPending = false;
            return handleInterrupt();
        }
        interruptPending = false;

        if (HALT) {
            return 4; // Z80: HALT consumes 4 T-states until interrupt
        }

        int opcode = memory.peek8(PC) & 0xFF;
        switch (opcode) {
            case 0xDD: // IX prefix
                PC = (PC + 1) & 0xFFFF;
                return executeDDOpcode();
            case 0xFD: // IY prefix
                PC = (PC + 1) & 0xFFFF;
                return executeFDOpcode();
            case 0xCB: // CB prefix
                PC = (PC + 1) & 0xFFFF;
                return executeCBOpcode();
            case 0xED: // ED prefix
                PC = (PC + 1) & 0xFFFF;
                return executeEDOpcode();
            default:
                return executeOpcode();
        }
    }

    /**
     * Assert a non-maskable interrupt.
     */
    public void nmi() {
        IFF2 = IFF1;
        IFF1 = false;
        push(PC);
        PC = 0x0066;
    }

    /**
     * Request a maskable interrupt (will be processed if IFF1=1).
     */
    public void requestInterrupt() {
        this.interruptPending = true;
    }

    // ============= Core helpers =============

    private int handleInterrupt() {
        // Exit HALT: on Z80, PC increments before servicing
        if (HALT) {
            HALT = false;
            PC = (PC + 1) & 0xFFFF;
        }
        switch (IM) {
            case 0: // Mode 0: external device supplies opcode
                // Simplified: behave like RST 38h
                IFF1 = false;
                IFF2 = false;
                push(PC);
                PC = 0x0038;
                return 13;
            case 1: // Mode 1: RST 38h
                IFF1 = false;
                IFF2 = false;
                push(PC);
                PC = 0x0038;
                return 13;
            case 2: // Mode 2: table at (I << 8) + data
                IFF1 = false;
                IFF2 = false;
                int vector = (I << 8) | 0xFF; // Simplified; external data not modeled
                int lo = memory.peek8(vector) & 0xFF;
                int hi = memory.peek8((vector + 1) & 0xFFFF) & 0xFF;
                push(PC);
                PC = (hi << 8) | lo;
                return 19;
            default:
                return 0;
        }
    }

    private void clearAllFlags() {
        F = 0;
    }

    private int readImmediateByte() {
        int v = memory.peek8(PC) & 0xFF;
        PC = (PC + 1) & 0xFFFF;
        return v;
    }

    private int readImmediateWord() {
        int lo = memory.peek8(PC) & 0xFF;
        PC = (PC + 1) & 0xFFFF;
        int hi = memory.peek8(PC) & 0xFF;
        PC = (PC + 1) & 0xFFFF;
        return (hi << 8) | lo;
    }

    private int readDisplacement() {
        int d = memory.peek8(PC) & 0xFF;
        PC = (PC + 1) & 0xFFFF;
        return (byte) d; // sign-extend via cast to Java byte then to int
    }

    private int readOpcode() {
        int op = memory.peek8(PC) & 0xFF;
        PC = (PC + 1) & 0xFFFF;
        // R: increment lower 7 bits, keep bit 7
        R = (R & 0x80) | ((R + 1) & 0x7F);
        return op;
    }

    private void push(int value) {
        SP = (SP - 2) & 0xFFFF;
        memory.poke8(SP, value & 0xFF);
        memory.poke8((SP + 1) & 0xFFFF, (value >>> 8) & 0xFF);
    }

    private int pop() {
        int lo = memory.peek8(SP) & 0xFF;
        int hi = memory.peek8((SP + 1) & 0xFFFF) & 0xFF;
        SP = (SP + 2) & 0xFFFF;
        return (hi << 8) | lo;
    }

    // ============= ALU helpers (subset) =============

    private void updateSZXY(int value) {
        setFlag(FLAG_S, (value & 0x80) != 0);
        setFlag(FLAG_Z, (value & 0xFF) == 0);
        setFlag(FLAG_X, (value & FLAG_X) != 0);
        setFlag(FLAG_Y, (value & FLAG_Y) != 0);
    }

    private void updateXYFromAddr(int addr16) {
        setFlag(FLAG_X, ((addr16 >>> 8) & FLAG_X) != 0);
        setFlag(FLAG_Y, ((addr16 >>> 8) & FLAG_Y) != 0);
    }

    private boolean parity(int v) {
        int x = v & 0xFF;
        x ^= x >>> 4;
        x &= 0x0F;
        return (0x6996 >>> x & 1) == 1; // even parity table trick
    }

    private int inc8(int v) {
        int res = (v + 1) & 0xFF;
        setFlag(FLAG_H, (v & 0x0F) == 0x0F);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, v == 0x7F);
        return res;
    }

    private int dec8(int v) {
        int res = (v - 1) & 0xFF;
        setFlag(FLAG_H, (v & 0x0F) == 0x00);
        setFlag(FLAG_N, true);
        updateSZXY(res);
        setFlag(FLAG_PV, v == 0x80);
        return res;
    }

    private void rlca() {
        int result = ((A << 1) | (A >>> 7)) & 0xFF;
        setFlag(FLAG_C, (A & 0x80) != 0);
        A = result;
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateXYFromA();
    }

    private void rrca() {
        int result = ((A >>> 1) | (A << 7)) & 0xFF;
        setFlag(FLAG_C, (A & 0x01) != 0);
        A = result;
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateXYFromA();
    }

    private void rla() {
        boolean oldC = getFlag(FLAG_C);
        int result = (A << 1) & 0xFF;
        if (oldC) result |= 0x01;
        setFlag(FLAG_C, (A & 0x80) != 0);
        A = result;
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateXYFromA();
    }

    private void rra() {
        boolean oldC = getFlag(FLAG_C);
        int result = (A >>> 1) & 0xFF;
        if (oldC) result |= 0x80;
        setFlag(FLAG_C, (A & 0x01) != 0);
        A = result;
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateXYFromA();
    }

    private void daa() {
        int correction = 0;
        if (getFlag(FLAG_H) || (A & 0x0F) > 9) correction += 0x06;
        if (A > 0x99 || getFlag(FLAG_C)) {
            correction += 0x60;
            setFlag(FLAG_C, true);
        }
        boolean sub = getFlag(FLAG_N);
        if (sub) {
            setFlag(FLAG_H, getFlag(FLAG_H) && (A & 0x0F) < 0x06);
            A = (A - correction) & 0xFF;
        } else {
            setFlag(FLAG_H, (A & 0x0F) > 9);
            A = (A + correction) & 0xFF;
        }
        setFlag(FLAG_S, (A & 0x80) != 0);
        setFlag(FLAG_Z, A == 0);
        setFlag(FLAG_PV, parity(A));
        updateXYFromA();
    }

    private void cpl() {
        A = (~A) & 0xFF;
        setFlag(FLAG_H, true);
        setFlag(FLAG_N, true);
        updateXYFromA();
    }

    private void scf() {
        setFlag(FLAG_C, true);
        setFlag(FLAG_N, false);
        setFlag(FLAG_H, false);
        if (isNMOS) updateXYFromA();
        else if ((A & FLAG_Y) == FLAG_Y && (A & FLAG_X) == FLAG_X) {
            setFlag(FLAG_Y, true);
            setFlag(FLAG_X, true);
        }
    }

    private void ccf() {
        boolean oldC = getFlag(FLAG_C);
        setFlag(FLAG_C, !oldC);
        setFlag(FLAG_H, oldC);
        setFlag(FLAG_N, false);
        if (isNMOS) updateXYFromA();
        else if ((A & FLAG_Y) == FLAG_Y && (A & FLAG_X) == FLAG_X) {
            setFlag(FLAG_Y, true);
            setFlag(FLAG_X, true);
        }
    }

    private int add16(int a, int b) {
        int res = (a + b) & 0xFFFF;
        setFlag(FLAG_C, (a + b) > 0xFFFF);
        setFlag(FLAG_H, ((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF);
        setFlag(FLAG_N, false);
        updateXYFromAddr(res);
        return res;
    }

    private void add8(int v) {
        int a = A;
        int res = a + v;
        setFlag(FLAG_C, res > 0xFF);
        setFlag(FLAG_H, ((a & 0x0F) + (v & 0x0F)) > 0x0F);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        boolean sameSign = ((a ^ v) & 0x80) == 0;
        boolean diffResSign = ((a ^ res) & 0x80) != 0;
        setFlag(FLAG_PV, sameSign && diffResSign);
        A = res & 0xFF;
    }

    private void adc8(int v) {
        int a = A;
        boolean c = getFlag(FLAG_C);
        int res = a + v + (c ? 1 : 0);
        setFlag(FLAG_C, res > 0xFF);
        setFlag(FLAG_H, ((a & 0x0F) + (v & 0x0F) + (c ? 1 : 0)) > 0x0F);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        boolean sameSign = ((a ^ v) & 0x80) == 0;
        boolean diffResSign = ((a ^ res) & 0x80) != 0;
        setFlag(FLAG_PV, sameSign && diffResSign);
        A = res & 0xFF;
    }

    private void sub8(int v) {
        int a = A;
        int res = (a - v) & 0x1FF;
        setFlag(FLAG_C, a < v);
        setFlag(FLAG_H, (a & 0x0F) < (v & 0x0F));
        setFlag(FLAG_N, true);
        updateSZXY(res);
        boolean diffSign = ((a ^ v) & 0x80) != 0;
        boolean changedSign = ((a ^ res) & 0x80) != 0;
        setFlag(FLAG_PV, diffSign && changedSign);
        A = res & 0xFF;
    }

    private void sbc8(int v) {
        int a = A;
        int c = getFlag(FLAG_C) ? 1 : 0;
        int t = v + c;
        int res = a - t;
        setFlag(FLAG_C, (a & 0xFF) < (t & 0xFF));
        setFlag(FLAG_H, (a & 0x0F) < ((v & 0x0F) + c));
        setFlag(FLAG_N, true);
        updateSZXY(res);
        boolean diffSign = ((a ^ v) & 0x80) != 0;
        boolean changedSign = ((a ^ res) & 0x80) != 0;
        setFlag(FLAG_PV, diffSign && changedSign);
        A = res & 0xFF;
    }

    private void and8(int v) {
        A = (A & v) & 0xFF;
        setFlag(FLAG_C, false);
        setFlag(FLAG_N, false);
        setFlag(FLAG_H, true);
        updateSZXY(A);
        setFlag(FLAG_PV, parity(A));
    }

    private void xor8(int v) {
        A = (A ^ v) & 0xFF;
        setFlag(FLAG_C, false);
        setFlag(FLAG_N, false);
        setFlag(FLAG_H, false);
        updateSZXY(A);
        setFlag(FLAG_PV, parity(A));
    }

    private void or8(int v) {
        A = (A | v) & 0xFF;
        setFlag(FLAG_C, false);
        setFlag(FLAG_N, false);
        setFlag(FLAG_H, false);
        updateSZXY(A);
        setFlag(FLAG_PV, parity(A));
    }

    private void cp8(int v) {
        int a = A;
        int res = (a - v) & 0x1FF;
        setFlag(FLAG_S, (res & 0x80) != 0);
        setFlag(FLAG_Z, (res & 0xFF) == 0);
        setFlag(FLAG_H, (a & 0x0F) < (v & 0x0F));
        setFlag(FLAG_PV, (((a ^ v) & 0x80) != 0) && (((a ^ res) & 0x80) != 0));
        setFlag(FLAG_N, true);
        setFlag(FLAG_X, (res & FLAG_X) != 0);
        setFlag(FLAG_Y, (res & FLAG_Y) != 0);
    }

    private void updateXYFromA() {
        setFlag(FLAG_X, (A & FLAG_X) != 0);
        setFlag(FLAG_Y, (A & FLAG_Y) != 0);
    }

    private int executeOpcode() { // Base opcode decoder (subset ported)
        int opcode = readOpcode();
        switch (opcode & 0xFF) {
            // NOP / EX AF,AF'
            case 0x00:
                return 4; // NOP
            case 0x08: {
                int ta = A, tf = F;
                A = A_;
                F = F_;
                A_ = ta;
                F_ = tf;
                return 4;
            }

            // 8-bit INC/DEC
            case 0x04:
                B = inc8(B);
                return 4;
            case 0x05:
                B = dec8(B);
                return 4;
            case 0x0C:
                C = inc8(C);
                return 4;
            case 0x0D:
                C = dec8(C);
                return 4;
            case 0x14:
                D = inc8(D);
                return 4;
            case 0x15:
                D = dec8(D);
                return 4;
            case 0x1C:
                E = inc8(E);
                return 4;
            case 0x1D:
                E = dec8(E);
                return 4;
            case 0x24:
                H = inc8(H);
                return 4;
            case 0x25:
                H = dec8(H);
                return 4;
            case 0x2C:
                L = inc8(L);
                return 4;
            case 0x2D:
                L = dec8(L);
                return 4;
            case 0x34: {
                int addr = HL();
                int v = memory.peek8(addr) & 0xFF;
                v = inc8(v);
                memory.poke8(addr, v);
                MEMPTR = addr;
                return 11;
            }
            case 0x35: {
                int addr = HL();
                int v = memory.peek8(addr) & 0xFF;
                v = dec8(v);
                memory.poke8(addr, v);
                MEMPTR = addr;
                return 11;
            }
            case 0x3C:
                A = inc8(A);
                return 4;
            case 0x3D:
                A = dec8(A);
                return 4;

            // 8-bit accumulator operations (register)
            case 0x80:
                add8(B);
                return 4;
            case 0x81:
                add8(C);
                return 4;
            case 0x82:
                add8(D);
                return 4;
            case 0x83:
                add8(E);
                return 4;
            case 0x84:
                add8(H);
                return 4;
            case 0x85:
                add8(L);
                return 4;
            case 0x86: {
                int v = memory.peek8(HL()) & 0xFF;
                add8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0x87:
                add8(A);
                return 4;

            case 0x88:
                adc8(B);
                return 4;
            case 0x89:
                adc8(C);
                return 4;
            case 0x8A:
                adc8(D);
                return 4;
            case 0x8B:
                adc8(E);
                return 4;
            case 0x8C:
                adc8(H);
                return 4;
            case 0x8D:
                adc8(L);
                return 4;
            case 0x8E: {
                int v = memory.peek8(HL()) & 0xFF;
                adc8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0x8F:
                adc8(A);
                return 4;

            case 0x90:
                sub8(B);
                return 4;
            case 0x91:
                sub8(C);
                return 4;
            case 0x92:
                sub8(D);
                return 4;
            case 0x93:
                sub8(E);
                return 4;
            case 0x94:
                sub8(H);
                return 4;
            case 0x95:
                sub8(L);
                return 4;
            case 0x96: {
                int v = memory.peek8(HL()) & 0xFF;
                sub8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0x97:
                sub8(A);
                return 4;

            case 0x98:
                sbc8(B);
                return 4;
            case 0x99:
                sbc8(C);
                return 4;
            case 0x9A:
                sbc8(D);
                return 4;
            case 0x9B:
                sbc8(E);
                return 4;
            case 0x9C:
                sbc8(H);
                return 4;
            case 0x9D:
                sbc8(L);
                return 4;
            case 0x9E: {
                int v = memory.peek8(HL()) & 0xFF;
                sbc8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0x9F:
                sbc8(A);
                return 4;

            case 0xA0:
                and8(B);
                return 4;
            case 0xA1:
                and8(C);
                return 4;
            case 0xA2:
                and8(D);
                return 4;
            case 0xA3:
                and8(E);
                return 4;
            case 0xA4:
                and8(H);
                return 4;
            case 0xA5:
                and8(L);
                return 4;
            case 0xA6: {
                int v = memory.peek8(HL()) & 0xFF;
                and8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0xA7:
                and8(A);
                return 4;

            case 0xA8:
                xor8(B);
                return 4;
            case 0xA9:
                xor8(C);
                return 4;
            case 0xAA:
                xor8(D);
                return 4;
            case 0xAB:
                xor8(E);
                return 4;
            case 0xAC:
                xor8(H);
                return 4;
            case 0xAD:
                xor8(L);
                return 4;
            case 0xAE: {
                int v = memory.peek8(HL()) & 0xFF;
                xor8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0xAF:
                xor8(A);
                return 4;

            case 0xB0:
                or8(B);
                return 4;
            case 0xB1:
                or8(C);
                return 4;
            case 0xB2:
                or8(D);
                return 4;
            case 0xB3:
                or8(E);
                return 4;
            case 0xB4:
                or8(H);
                return 4;
            case 0xB5:
                or8(L);
                return 4;
            case 0xB6: {
                int v = memory.peek8(HL()) & 0xFF;
                or8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0xB7:
                or8(A);
                return 4;

            case 0xB8:
                cp8(B);
                return 4;
            case 0xB9:
                cp8(C);
                return 4;
            case 0xBA:
                cp8(D);
                return 4;
            case 0xBB:
                cp8(E);
                return 4;
            case 0xBC:
                cp8(H);
                return 4;
            case 0xBD:
                cp8(L);
                return 4;
            case 0xBE: {
                int v = memory.peek8(HL()) & 0xFF;
                cp8(v);
                MEMPTR = HL();
                return 7;
            }
            case 0xBF:
                cp8(A);
                return 4;

            // ALU immediate
            case 0xC6: {
                int n = readImmediateByte();
                add8(n);
                return 7;
            }
            case 0xCE: {
                int n = readImmediateByte();
                adc8(n);
                return 7;
            }
            case 0xD6: {
                int n = readImmediateByte();
                sub8(n);
                return 7;
            }
            case 0xDE: {
                int n = readImmediateByte();
                sbc8(n);
                return 7;
            }
            case 0xE6: {
                int n = readImmediateByte();
                and8(n);
                return 7;
            }
            case 0xEE: {
                int n = readImmediateByte();
                xor8(n);
                return 7;
            }
            case 0xF6: {
                int n = readImmediateByte();
                or8(n);
                return 7;
            }
            case 0xFE: {
                int n = readImmediateByte();
                cp8(n);
                return 7;
            }

            // Rotates/flags related
            case 0x07:
                rlca();
                return 4;
            case 0x0F:
                rrca();
                return 4;
            case 0x17:
                rla();
                return 4;
            case 0x1F:
                rra();
                return 4;
            case 0x27:
                daa();
                return 4;
            case 0x2F:
                cpl();
                return 4;
            case 0x37:
                scf();
                return 4;
            case 0x3F:
                ccf();
                return 4;

            // 8-bit loads: LD r, r and LD r, n
            default: {
                int x = (opcode >>> 6) & 3;
                int y = (opcode >>> 3) & 7;
                int z = opcode & 7;
                if (x == 1) { // LD r[y], r[z]
                    // (HL) case z==6 or y==6 uses memory
                    if (y == 6 && z == 6) { // HALT
                        HALT = true;
                        return 4;
                    }
                    int val;
                    if (z == 6) {
                        int addr = HL();
                        val = memory.peek8(addr) & 0xFF;
                        MEMPTR = addr;
                    } else {
                        val = getReg(z);
                    }
                    if (y == 6) {
                        int addr = HL();
                        memory.poke8(addr, val);
                        MEMPTR = addr;
                        return 7;
                    } else {
                        setReg(y, val);
                        return 4;
                    }
                }
                if (x == 0) {
                    switch (y) {
                        case 0: // Relative jumps and NOP group
                            if (z == 0) return 4; // NOP (already matched), but keep
                            if (z == 2) { // DJNZ e
                                B = (B - 1) & 0xFF;
                                if (B != 0) {
                                    int e = readDisplacement();
                                    int newPC = (PC + e) & 0xFFFF;
                                    MEMPTR = newPC;
                                    PC = newPC;
                                    return 13;
                                } else {
                                    PC = (PC + 1) & 0xFFFF;
                                    return 8;
                                }
                            }
                            if (z == 3) { // JR e
                                int e = readDisplacement();
                                int newPC = (PC + e) & 0xFFFF;
                                MEMPTR = newPC;
                                PC = newPC;
                                return 12;
                            }
                            if (z >= 4) { // JR cc,e
                                boolean cc = switch (z) {
                                    case 4 -> !getFlag(FLAG_Z); // NZ
                                    case 5 -> getFlag(FLAG_Z);  // Z
                                    case 6 -> !getFlag(FLAG_C); // NC
                                    case 7 -> getFlag(FLAG_C);  // C
                                    default -> false;
                                };
                                int disp = readDisplacement();
                                if (cc) {
                                    int newPC = (PC + disp) & 0xFFFF;
                                    MEMPTR = newPC;
                                    PC = newPC;
                                    return 12;
                                } else {
                                    return 7;
                                }
                            }
                            break;
                        case 1: // 16-bit LD rp,nn and ADD HL,rp
                            if (z == 1) { // ADD HL, rp[y>>1]
                                int rp = switch (y & 3) {
                                    case 0 -> BC();
                                    case 1 -> DE();
                                    case 2 -> HL();
                                    default -> SP;
                                };
                                int res = add16(HL(), rp);
                                MEMPTR = (HL() + 1) & 0xFFFF;
                                setHL(res);
                                return 11;
                            } else { // LD rp, nn
                                int nn = readImmediateWord();
                                switch (y & 3) {
                                    case 0 -> setBC(nn);
                                    case 1 -> setDE(nn);
                                    case 2 -> setHL(nn);
                                    default -> SP = nn;
                                }
                                return 10;
                            }
                        case 2: // LD (BC),(DE),A and LD (nn),HL / LD HL,(nn)
                            switch (z) {
                                case 0:
                                    memory.poke8(BC(), A);
                                    MEMPTR = ((A & 0xFF) << 8) | ((BC() + 1) & 0xFF);
                                    return 7; // LD (BC),A
                                case 1:
                                    A = memory.peek8(BC()) & 0xFF;
                                    MEMPTR = (BC() + 1) & 0xFFFF;
                                    return 7; // LD A,(BC)
                                case 2:
                                    memory.poke8(DE(), A);
                                    MEMPTR = ((A & 0xFF) << 8) | ((DE() + 1) & 0xFF);
                                    return 7; // LD (DE),A
                                case 3:
                                    A = memory.peek8(DE()) & 0xFF;
                                    MEMPTR = (DE() + 1) & 0xFFFF;
                                    return 7; // LD A,(DE)
                                case 4: {
                                    int nn = readImmediateWord();
                                    int hl = HL();
                                    memory.poke8(nn, hl & 0xFF);
                                    memory.poke8((nn + 1) & 0xFFFF, (hl >>> 8) & 0xFF);
                                    MEMPTR = (nn + 1) & 0xFFFF;
                                    return 16;
                                }
                                case 5: {
                                    int nn = readImmediateWord();
                                    int lo = memory.peek8(nn) & 0xFF;
                                    int hi = memory.peek8((nn + 1) & 0xFFFF) & 0xFF;
                                    setHL((hi << 8) | lo);
                                    MEMPTR = (nn + 1) & 0xFFFF;
                                    return 16;
                                }
                                case 6: {
                                    int n = readImmediateByte();
                                    int addr = HL();
                                    memory.poke8(addr, n);
                                    MEMPTR = addr;
                                    return 10;
                                } // LD (HL), n
                                case 7: { // LD A, I/R variants handled in ED set; here it's LD A, (nn)
                                    int nn = readImmediateWord();
                                    A = memory.peek8(nn) & 0xFF;
                                    MEMPTR = (nn + 1) & 0xFFFF;
                                    return 13;
                                }
                            }
                            break;
                        case 3: // INC/DEC rp and others
                            if (z == 0) {
                                setBC((BC() + 1) & 0xFFFF);
                                return 6;
                            }
                            if (z == 1) {
                                setBC((BC() - 1) & 0xFFFF);
                                return 6;
                            }
                            if (z == 2) {
                                setDE((DE() + 1) & 0xFFFF);
                                return 6;
                            }
                            if (z == 3) {
                                setDE((DE() - 1) & 0xFFFF);
                                return 6;
                            }
                            if (z == 4) {
                                setHL((HL() + 1) & 0xFFFF);
                                return 6;
                            }
                            if (z == 5) {
                                setHL((HL() - 1) & 0xFFFF);
                                return 6;
                            }
                            if (z == 6) {
                                SP = (SP + 1) & 0xFFFF;
                                return 6;
                            }
                            if (z == 7) {
                                SP = (SP - 1) & 0xFFFF;
                                return 6;
                            }
                            break;
                    }
                }
                // If not handled, fall back to simple ops we know
                break;
            }
        }
        // Default: treat as NOP timing to keep running
        return 4;
    }

    private int executeCBOpcode() {
        int opcode = readOpcode();
        R++; // As in C++: do not count prefix twice
        int reg = opcode & 0x07;
        int op = (opcode >>> 3) & 0x07;
        if (opcode <= 0x3F) {
            // RLC/RRC/RL/RR/SLA/SRA/SLL/SRL
            if (reg == 6) {
                int addr = HL();
                int val = memory.peek8(addr) & 0xFF;
                int res = switch (op) {
                    case 0 -> rlc(val);
                    case 1 -> rrc(val);
                    case 2 -> rl(val);
                    case 3 -> rr(val);
                    case 4 -> sla(val);
                    case 5 -> sra(val);
                    case 6 -> sll(val);
                    case 7 -> srl(val);
                    default -> val;
                };
                memory.poke8(addr, res & 0xFF);
                return 15;
            } else {
                int val = getReg(reg);
                int res = switch (op) {
                    case 0 -> rlc(val);
                    case 1 -> rrc(val);
                    case 2 -> rl(val);
                    case 3 -> rr(val);
                    case 4 -> sla(val);
                    case 5 -> sra(val);
                    case 6 -> sll(val);
                    case 7 -> srl(val);
                    default -> val;
                };
                setReg(reg, res);
                return 8;
            }
        }
        if (opcode >= 0x40 && opcode <= 0x7F) {
            // BIT b, r/(HL)
            int bitNum = (opcode >>> 3) & 0x07;
            if (reg == 6) {
                int addr = HL();
                int val = memory.peek8(addr) & 0xFF;
                bitMem(bitNum, val, (MEMPTR >>> 8) & 0xFF);
                return 12;
            } else {
                int val = getReg(reg);
                bit(bitNum, val);
                return 8;
            }
        }
        if (opcode >= 0x80 && opcode <= 0xBF) {
            // RES b, r/(HL)
            int bitNum = (opcode >>> 3) & 0x07;
            if (reg == 6) {
                int addr = HL();
                int val = memory.peek8(addr) & 0xFF;
                int res = res(bitNum, val);
                memory.poke8(addr, res);
                return 15;
            } else {
                int val = getReg(reg);
                int res = res(bitNum, val);
                setReg(reg, res);
                return 8;
            }
        }
        // SET b, r/(HL)
        int bitNum = (opcode >>> 3) & 0x07;
        if (reg == 6) {
            int addr = HL();
            int val = memory.peek8(addr) & 0xFF;
            int res = set(bitNum, val);
            memory.poke8(addr, res);
            return 15;
        } else {
            int val = getReg(reg);
            int res = set(bitNum, val);
            setReg(reg, res);
            return 8;
        }
    }

    private int executeDDOpcode() {
        int opcode = readOpcode();
        R++; // Prefix accounted
        if (opcode == 0xCB) {
            return executeDDCBOpcode();
        }
        switch (opcode) {
            // 16-bit
            case 0x09: {
                int old = IX;
                IX = add16(IX, BC());
                MEMPTR = (old + 1) & 0xFFFF;
                return 15;
            }
            case 0x19: {
                int old = IX;
                IX = add16(IX, DE());
                MEMPTR = (old + 1) & 0xFFFF;
                return 15;
            }
            case 0x21:
                IX = readImmediateWord();
                return 14;
            case 0x22: {
                int addr = readImmediateWord();
                memory.poke8(addr, IX & 0xFF);
                memory.poke8((addr + 1) & 0xFFFF, (IX >>> 8) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x23:
                IX = (IX + 1) & 0xFFFF;
                return 10;
            case 0x24:
                setIXH(inc8(getIXH()));
                return 8;
            case 0x25:
                setIXH(dec8(getIXH()));
                return 8;
            case 0x26:
                setIXH(readImmediateByte());
                return 11;
            case 0x29: {
                int old = IX;
                IX = add16(IX, IX);
                MEMPTR = (old + 1) & 0xFFFF;
                return 15;
            }
            case 0x2A: {
                int addr = readImmediateWord();
                IX = ((memory.peek8((addr + 1) & 0xFFFF) & 0xFF) << 8) | (memory.peek8(addr) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x2B:
                IX = (IX - 1) & 0xFFFF;
                return 10;
            case 0x2C:
                setIXL(inc8(getIXL()));
                return 8;
            case 0x2D:
                setIXL(dec8(getIXL()));
                return 8;
            case 0x2E:
                setIXL(readImmediateByte());
                return 11;
            case 0x34:
                return executeIncDecIndexedIX(true);
            case 0x35:
                return executeIncDecIndexedIX(false);
            case 0x36: {
                int d = readDisplacement();
                int v = readImmediateByte();
                int addr = (IX + d) & 0xFFFF;
                memory.poke8(addr, v);
                MEMPTR = addr;
                return 19;
            }
            case 0x39: {
                int old = IX;
                IX = add16(IX, SP);
                MEMPTR = (old + 1) & 0xFFFF;
                return 15;
            }

            // LD r, (IX+d)
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E:
                return executeLoadFromIndexedIX((opcode >>> 3) & 7);
            // LD (IX+d), r
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77: {
                int d = readDisplacement();
                int addr = (IX + d) & 0xFFFF;
                int rcode = opcode & 7;
                int val = (rcode == 0 ? B : rcode == 1 ? C : rcode == 2 ? D : rcode == 3 ? E : rcode == 4 ? H : rcode == 5 ? L : A);
                memory.poke8(addr, val & 0xFF);
                MEMPTR = addr;
                return 19;
            }
            // ALU A,(IX+d)
            case 0x86:
            case 0x8E:
            case 0x96:
            case 0x9E:
            case 0xA6:
            case 0xAE:
            case 0xB6:
            case 0xBE:
                return executeALUIndexedIX((opcode >>> 3) & 7);
            default:
                return 8;
        }
    }

    private int executeEDOpcode() {
        int opcode = readOpcode();
        R++;
        switch (opcode & 0xFF) {
            // Block transfer
            case 0xA0:
                ldi();
                return 16;
            case 0xA8:
                ldd();
                return 16;
            case 0xB0:
                return ldir();
            case 0xB8:
                return lddr();
            // Block compare
            case 0xA1:
                cpi();
                return 16;
            case 0xA9:
                cpd();
                return 16;
            case 0xB1:
                return cpir();
            case 0xB9:
                return cpdr();
            // Block I/O
            case 0xA2:
                ini();
                return 16;
            case 0xAA:
                ind();
                return 16;
            case 0xB2:
                return inir();
            case 0xBA:
                return indr();
            case 0xA3:
                outi();
                return 16;
            case 0xAB:
                outd();
                return 16;
            case 0xB3:
                return otir();
            case 0xBB:
                return otdr();

            // IN r,(C) and OUT (C),r
            case 0x40:
                return executeIN(0);
            case 0x48:
                return executeIN(1);
            case 0x50:
                return executeIN(2);
            case 0x58:
                return executeIN(3);
            case 0x60:
                return executeIN(4);
            case 0x68:
                return executeIN(5);
            case 0x78:
                return executeIN(7);
            case 0x41:
                return executeOUT(0);
            case 0x49:
                return executeOUT(1);
            case 0x51:
                return executeOUT(2);
            case 0x59:
                return executeOUT(3);
            case 0x61:
                return executeOUT(4);
            case 0x69:
                return executeOUT(5);
            case 0x79:
                return executeOUT(7);

            // 16-bit SBC/ADC HL, rp
            case 0x42: {
                HL_SBC(BC());
                return 15;
            }
            case 0x4A: {
                HL_ADC(BC());
                return 15;
            }
            case 0x52: {
                HL_SBC(DE());
                return 15;
            }
            case 0x5A: {
                HL_ADC(DE());
                return 15;
            }
            case 0x62: {
                HL_SBC(HL());
                return 15;
            }
            case 0x6A: {
                HL_ADC(HL());
                return 15;
            }
            case 0x72: {
                HL_SBC(SP);
                return 15;
            }
            case 0x7A: {
                HL_ADC(SP);
                return 15;
            }

            // LD (nn), rp and rp, (nn)
            case 0x43: {
                int addr = readImmediateWord();
                int v = BC();
                memory.poke8(addr, v & 0xFF);
                memory.poke8((addr + 1) & 0xFFFF, (v >>> 8) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x53: {
                int addr = readImmediateWord();
                int v = DE();
                memory.poke8(addr, v & 0xFF);
                memory.poke8((addr + 1) & 0xFFFF, (v >>> 8) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x73: {
                int addr = readImmediateWord();
                int v = SP;
                memory.poke8(addr, v & 0xFF);
                memory.poke8((addr + 1) & 0xFFFF, (v >>> 8) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x4B: {
                int addr = readImmediateWord();
                int lo = memory.peek8(addr) & 0xFF;
                int hi = memory.peek8((addr + 1) & 0xFFFF) & 0xFF;
                setBC((hi << 8) | lo);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x5B: {
                int addr = readImmediateWord();
                int lo = memory.peek8(addr) & 0xFF;
                int hi = memory.peek8((addr + 1) & 0xFFFF) & 0xFF;
                setDE((hi << 8) | lo);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x7B: {
                int addr = readImmediateWord();
                int lo = memory.peek8(addr) & 0xFF;
                int hi = memory.peek8((addr + 1) & 0xFFFF) & 0xFF;
                SP = (hi << 8) | lo;
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }

            // Misc: NEG, RETN/RETI, IM 0/1/2, LD I,A/A,I/R
            case 0x44:
            case 0x4C:
            case 0x54:
            case 0x5C:
            case 0x64:
            case 0x6C:
            case 0x74:
            case 0x7C:
                neg();
                return 8;
            case 0x45:
            case 0x55:
            case 0x5D:
            case 0x65:
            case 0x6D:
            case 0x75:
            case 0x7D:
                retn();
                return 14;
            case 0x46:
            case 0x4E:
            case 0x66:
                IM = 0;
                return 8;
            case 0x56:
            case 0x76:
                IM = 1;
                return 8;
            case 0x5E:
            case 0x7E:
                IM = 2;
                return 8;
            case 0x47:
                I = A;
                return 9;
            case 0x57: // LD A, I
                A = I & 0xFF;
                updateSZXY(A);
                setFlag(FLAG_PV, IFF2);
                setFlag(FLAG_H, false);
                setFlag(FLAG_N, false);
                return 9;
            case 0x4F:
                R = A & 0xFF;
                return 9;
            case 0x5F: // LD A, R
                A = R & 0xFF;
                updateSZXY(A);
                setFlag(FLAG_PV, IFF2);
                setFlag(FLAG_H, false);
                setFlag(FLAG_N, false);
                return 9;
            case 0x67:
                rrd();
                return 18;
            case 0x6F:
                rld();
                return 18;
        }
        return 8;
    }

    private int executeFDOpcode() {
        int opcode = readOpcode();
        R++;
        if (opcode == 0xCB) {
            return executeFDCBOpcode();
        }
        switch (opcode) {
            case 0x21:
                IY = readImmediateWord();
                return 14;
            case 0x22: {
                int addr = readImmediateWord();
                memory.poke8(addr, IY & 0xFF);
                memory.poke8((addr + 1) & 0xFFFF, (IY >>> 8) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x2A: {
                int addr = readImmediateWord();
                IY = ((memory.peek8((addr + 1) & 0xFFFF) & 0xFF) << 8) | (memory.peek8(addr) & 0xFF);
                MEMPTR = (addr + 1) & 0xFFFF;
                return 20;
            }
            case 0x23:
                IY = (IY + 1) & 0xFFFF;
                return 10;
            case 0x2B:
                IY = (IY - 1) & 0xFFFF;
                return 10;
            case 0x24:
                setIYH(inc8(getIYH()));
                return 8;
            case 0x25:
                setIYH(dec8(getIYH()));
                return 8;
            case 0x26:
                setIYH(readImmediateByte());
                return 11;
            case 0x2C:
                setIYL(inc8(getIYL()));
                return 8;
            case 0x2D:
                setIYL(dec8(getIYL()));
                return 8;
            case 0x2E:
                setIYL(readImmediateByte());
                return 11;
            case 0x34:
                return executeIncDecIndexedIY(true);
            case 0x35:
                return executeIncDecIndexedIY(false);
            case 0x36: {
                int d = readDisplacement();
                int v = readImmediateByte();
                int addr = (IY + d) & 0xFFFF;
                memory.poke8(addr, v);
                MEMPTR = addr;
                return 19;
            }
            // LD r,(IY+d)
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E:
                return executeLoadFromIndexedIY((opcode >>> 3) & 7);
            // (IY+d),r
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77: {
                int d = readDisplacement();
                int addr = (IY + d) & 0xFFFF;
                int rcode = opcode & 7;
                int val = (rcode == 0 ? B : rcode == 1 ? C : rcode == 2 ? D : rcode == 3 ? E : rcode == 4 ? H : rcode == 5 ? L : A);
                memory.poke8(addr, val & 0xFF);
                MEMPTR = addr;
                return 19;
            }
            // ALU A,(IY+d)
            case 0x86:
            case 0x8E:
            case 0x96:
            case 0x9E:
            case 0xA6:
            case 0xAE:
            case 0xB6:
            case 0xBE:
                return executeALUIndexedIY((opcode >>> 3) & 7);
            default:
                return 8;
        }
    }

    private int executeDDCBOpcode() {
        int originalR = R;
        int d = readDisplacement();
        int op = readOpcode();
        R = originalR; // Adjust to count 2 in total
        int addr = (IX + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        if (op <= 0x3F) {
            int opt = (op >>> 3) & 7;
            int reg = op & 7;
            int result = switch (opt) {
                case 0 -> rlc(value);
                case 1 -> rrc(value);
                case 2 -> rl(value);
                case 3 -> rr(value);
                case 4 -> sla(value);
                case 5 -> sra(value);
                case 6 -> sll(value);
                case 7 -> srl(value);
                default -> value;
            };
            memory.poke8(addr, result);
            if (reg != 6) setReg(reg, result);
            MEMPTR = addr;
            return 23;
        }
        if (op <= 0x7F) {
            int bitNum = (op >>> 3) & 7;
            bitMem(bitNum, value, (addr >>> 8) & 0xFF);
            MEMPTR = addr;
            return 20;
        }
        if (op <= 0xBF) {
            int bitNum = (op >>> 3) & 7;
            int reg = op & 7;
            int result = res(bitNum, value);
            memory.poke8(addr, result);
            if (reg != 6) setReg(reg, result);
            MEMPTR = addr;
            return 23;
        }
        {
            int bitNum = (op >>> 3) & 7;
            int reg = op & 7;
            int result = set(bitNum, value);
            memory.poke8(addr, result);
            if (reg != 6) setReg(reg, result);
            MEMPTR = addr;
            return 23;
        }
    }

    private int executeFDCBOpcode() {
        int d = readDisplacement();
        int op = readOpcode();
        R--; // adjust as in C++
        int addr = (IY + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        MEMPTR = addr;
        if (op <= 0x3F) {
            int opt = (op >>> 3) & 7;
            int reg = op & 7;
            int result = switch (opt) {
                case 0 -> rlc(value);
                case 1 -> rrc(value);
                case 2 -> rl(value);
                case 3 -> rr(value);
                case 4 -> sla(value);
                case 5 -> sra(value);
                case 6 -> sll(value);
                case 7 -> srl(value);
                default -> value;
            };
            memory.poke8(addr, result);
            if (reg != 6) setReg(reg, result);
            return 23;
        }
        if (op <= 0x7F) {
            int bitNum = (op >>> 3) & 7;
            bitMem(bitNum, value, (addr >>> 8) & 0xFF);
            return 20;
        }
        if (op <= 0xBF) {
            int bitNum = (op >>> 3) & 7;
            int reg = op & 7;
            int result = res(bitNum, value);
            memory.poke8(addr, result);
            if (reg != 6) setReg(reg, result);
            return 23;
        }
        {
            int bitNum = (op >>> 3) & 7;
            int reg = op & 7;
            int result = set(bitNum, value);
            memory.poke8(addr, result);
            if (reg != 6) setReg(reg, result);
            return 23;
        }
    }

    // ============= IX/IY helpers =============

    private int getIXH() {
        return (IX >>> 8) & 0xFF;
    }

    private int getIXL() {
        return IX & 0xFF;
    }

    private void setIXH(int v) {
        IX = ((v & 0xFF) << 8) | (IX & 0x00FF);
    }

    private void setIXL(int v) {
        IX = (IX & 0xFF00) | (v & 0xFF);
    }

    private int getIYH() {
        return (IY >>> 8) & 0xFF;
    }

    private int getIYL() {
        return IY & 0xFF;
    }

    private void setIYH(int v) {
        IY = ((v & 0xFF) << 8) | (IY & 0x00FF);
    }

    private void setIYL(int v) {
        IY = (IY & 0xFF00) | (v & 0xFF);
    }

    private int executeIncDecIndexedIX(boolean inc) {
        int d = readDisplacement();
        int addr = (IX + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        int res = inc ? inc8(value) : dec8(value);
        memory.poke8(addr, res);
        MEMPTR = addr;
        return 23;
    }

    private int executeIncDecIndexedIY(boolean inc) {
        int d = readDisplacement();
        int addr = (IY + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        int res = inc ? inc8(value) : dec8(value);
        memory.poke8(addr, res);
        MEMPTR = addr;
        return 23;
    }

    private int executeLoadFromIndexedIX(int reg) {
        int d = readDisplacement();
        int addr = (IX + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        switch (reg) {
            case 0 -> B = value;
            case 1 -> C = value;
            case 2 -> D = value;
            case 3 -> E = value;
            case 4 -> H = value;
            case 5 -> L = value;
            case 7 -> A = value;
        }
        MEMPTR = addr;
        return 19;
    }

    private int executeLoadFromIndexedIY(int reg) {
        int d = readDisplacement();
        int addr = (IY + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        switch (reg) {
            case 0 -> B = value;
            case 1 -> C = value;
            case 2 -> D = value;
            case 3 -> E = value;
            case 4 -> H = value;
            case 5 -> L = value;
            case 7 -> A = value;
        }
        MEMPTR = addr;
        return 19;
    }

    private int executeALUIndexedIX(int opType) {
        int d = readDisplacement();
        int addr = (IX + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        switch (opType) {
            case 0 -> add8(value);
            case 1 -> adc8(value);
            case 2 -> sub8(value);
            case 3 -> sbc8(value);
            case 4 -> and8(value);
            case 5 -> xor8(value);
            case 6 -> or8(value);
            case 7 -> cp8(value);
        }
        MEMPTR = addr;
        return 19;
    }

    private int executeALUIndexedIY(int opType) {
        int d = readDisplacement();
        int addr = (IY + d) & 0xFFFF;
        int value = memory.peek8(addr) & 0xFF;
        switch (opType) {
            case 0 -> add8(value);
            case 1 -> adc8(value);
            case 2 -> sub8(value);
            case 3 -> sbc8(value);
            case 4 -> and8(value);
            case 5 -> xor8(value);
            case 6 -> or8(value);
            case 7 -> cp8(value);
        }
        MEMPTR = addr;
        return 19;
    }

    // ============= ED helpers and operations =============

    private void HL_ADC(int rp) {
        int a = HL();
        int carry = getFlag(FLAG_C) ? 1 : 0;
        int res = a + rp + carry;
        setFlag(FLAG_C, (res & 0x10000) != 0);
        setFlag(FLAG_H, ((a & 0x0FFF) + (rp & 0x0FFF) + carry) > 0x0FFF);
        setFlag(FLAG_N, false);
        setFlag(FLAG_S, (res & 0x8000) != 0);
        setFlag(FLAG_Z, (res & 0xFFFF) == 0);
        // overflow for 16-bit add
        boolean overflow = (((a ^ ~rp) & (a ^ res)) & 0x8000) != 0;
        setFlag(FLAG_PV, overflow);
        updateXYFromAddr(res & 0xFFFF);
        setHL(res & 0xFFFF);
        MEMPTR = (a + 1) & 0xFFFF;
    }

    private void HL_SBC(int rp) {
        int a = HL();
        int carry = getFlag(FLAG_C) ? 1 : 0;
        int res = a - rp - carry;
        setFlag(FLAG_C, (res & 0x10000) != 0 ? false : (a < (rp + carry))); // alt safe
        setFlag(FLAG_H, ((a & 0x0FFF) - (rp & 0x0FFF) - carry) < 0);
        setFlag(FLAG_N, true);
        setFlag(FLAG_S, (res & 0x8000) != 0);
        setFlag(FLAG_Z, (res & 0xFFFF) == 0);
        boolean overflow = (((a ^ rp) & (a ^ res)) & 0x8000) != 0;
        setFlag(FLAG_PV, overflow);
        updateXYFromAddr(res & 0xFFFF);
        setHL(res & 0xFFFF);
        MEMPTR = (a + 1) & 0xFFFF;
    }

    private void neg() {
        int oldA = A;
        int res = (0 - oldA) & 0xFF;
        setFlag(FLAG_C, oldA != 0);
        setFlag(FLAG_H, (oldA & 0x0F) != 0);
        setFlag(FLAG_N, true);
        setFlag(FLAG_S, (res & 0x80) != 0);
        setFlag(FLAG_Z, res == 0);
        setFlag(FLAG_PV, oldA == 0x80);
        A = res;
        updateXYFromA();
    }

    private void retn() {
        IFF1 = IFF2;
        PC = pop();
    }

    private void rrd() {
        int addr = HL();
        int m = memory.peek8(addr) & 0xFF;
        int newM = ((A & 0x0F) << 4) | (m >>> 4);
        int newA = (A & 0xF0) | (m & 0x0F);
        memory.poke8(addr, newM);
        A = newA;
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(A);
        setFlag(FLAG_PV, parity(A));
    }

    private void rld() {
        int addr = HL();
        int m = memory.peek8(addr) & 0xFF;
        int newM = ((m << 4) | (A & 0x0F)) & 0xFF;
        int newA = (A & 0xF0) | ((m >>> 4) & 0x0F);
        memory.poke8(addr, newM);
        A = newA;
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(A);
        setFlag(FLAG_PV, parity(A));
    }

    private int executeIN(int reg) {
        int addr = ((B & 0xFF) << 8) | (C & 0xFF);
        int value = port.inPort(addr) & 0xFF;
        switch (reg) {
            case 0 -> B = value;
            case 1 -> C = value;
            case 2 -> D = value;
            case 3 -> E = value;
            case 4 -> H = value;
            case 5 -> L = value;
            case 7 -> A = value;
        }
        updateSZXY(value);
        setFlag(FLAG_PV, parity(value));
        setFlag(FLAG_N, false);
        setFlag(FLAG_H, false);
        return 12;
    }

    private int executeOUT(int reg) {
        int addr = ((B & 0xFF) << 8) | (C & 0xFF);
        int value = switch (reg) {
            case 0 -> B;
            case 1 -> C;
            case 2 -> D;
            case 3 -> E;
            case 4 -> H;
            case 5 -> L;
            case 7 -> A;
            default -> 0;
        };
        port.outPort(addr, value & 0xFF);
        return 12;
    }

    // LDI/LDD
    private void ldi() {
        int v = memory.peek8(HL()) & 0xFF;
        memory.poke8(DE(), v);
        setHL((HL() + 1) & 0xFFFF);
        setDE((DE() + 1) & 0xFFFF);
        setBC((BC() - 1) & 0xFFFF);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        setFlag(FLAG_PV, BC() != 0);
        int temp = (A + v) & 0xFF;
        setFlag(FLAG_X, (temp & FLAG_X) != 0);
        setFlag(FLAG_Y, (temp & FLAG_Y) != 0);
        MEMPTR = (HL()) & 0xFFFF;
    }

    private void ldd() {
        int v = memory.peek8(HL()) & 0xFF;
        memory.poke8(DE(), v);
        setHL((HL() - 1) & 0xFFFF);
        setDE((DE() - 1) & 0xFFFF);
        setBC((BC() - 1) & 0xFFFF);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        setFlag(FLAG_PV, BC() != 0);
        int temp = (A + v) & 0xFF;
        setFlag(FLAG_X, (temp & FLAG_X) != 0);
        setFlag(FLAG_Y, (temp & FLAG_Y) != 0);
        MEMPTR = (HL()) & 0xFFFF;
    }

    private int ldir() {
        ldi();
        if (BC() != 0) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private int lddr() {
        ldd();
        if (BC() != 0) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    // CPI/CPD
    private void cpi() {
        int v = memory.peek8(HL()) & 0xFF;
        cp8(v);
        setHL((HL() + 1) & 0xFFFF);
        setBC((BC() - 1) & 0xFFFF);
        setFlag(FLAG_PV, BC() != 0);
        MEMPTR = HL();
    }

    private void cpd() {
        int v = memory.peek8(HL()) & 0xFF;
        cp8(v);
        setHL((HL() - 1) & 0xFFFF);
        setBC((BC() - 1) & 0xFFFF);
        setFlag(FLAG_PV, BC() != 0);
        MEMPTR = HL();
    }

    private int cpir() {
        cpi();
        if (BC() != 0 && !getFlag(FLAG_Z)) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private int cpdr() {
        cpd();
        if (BC() != 0 && !getFlag(FLAG_Z)) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    // INI/IND/INIR/INDR
    private void ini() {
        int addr = ((B & 0xFF) << 8) | (C & 0xFF);
        int data = port.inPort(addr) & 0xFF;
        memory.poke8(HL(), data);
        setHL((HL() + 1) & 0xFFFF);
        B = (B - 1) & 0xFF;
        setFlag(FLAG_N, true);
        setFlag(FLAG_Z, B == 0);
        // Simplified flags; real Z80 has complex H/C
        MEMPTR = (addr + 1) & 0xFFFF;
    }

    private void ind() {
        int addr = ((B & 0xFF) << 8) | (C & 0xFF);
        int data = port.inPort(addr) & 0xFF;
        memory.poke8(HL(), data);
        setHL((HL() - 1) & 0xFFFF);
        B = (B - 1) & 0xFF;
        setFlag(FLAG_N, true);
        setFlag(FLAG_Z, B == 0);
        MEMPTR = (addr - 1) & 0xFFFF;
    }

    private int inir() {
        ini();
        if (B != 0) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private int indr() {
        ind();
        if (B != 0) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    // OUTI/OUTD/OTIR/OTDR
    private void outi() {
        int data = memory.peek8(HL()) & 0xFF;
        int addr = ((B & 0xFF) << 8) | (C & 0xFF);
        port.outPort(addr, data);
        setHL((HL() + 1) & 0xFFFF);
        B = (B - 1) & 0xFF;
        setFlag(FLAG_N, true);
        setFlag(FLAG_Z, B == 0);
        MEMPTR = (addr + 1) & 0xFFFF;
    }

    private void outd() {
        int data = memory.peek8(HL()) & 0xFF;
        int addr = ((B & 0xFF) << 8) | (C & 0xFF);
        port.outPort(addr, data);
        setHL((HL() - 1) & 0xFFFF);
        B = (B - 1) & 0xFF;
        setFlag(FLAG_N, true);
        setFlag(FLAG_Z, B == 0);
        MEMPTR = (addr - 1) & 0xFFFF;
    }

    private int otir() {
        outi();
        if (B != 0) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private int otdr() {
        outd();
        if (B != 0) {
            PC = (PC - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    // ============= CB helper operations on 8-bit values =============

    private int rlc(int v) {
        int res = ((v << 1) | (v >>> 7)) & 0xFF;
        setFlag(FLAG_C, (v & 0x80) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int rrc(int v) {
        int res = ((v >>> 1) | (v << 7)) & 0xFF;
        setFlag(FLAG_C, (v & 0x01) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int rl(int v) {
        boolean c = getFlag(FLAG_C);
        int res = ((v << 1) & 0xFF) | (c ? 1 : 0);
        setFlag(FLAG_C, (v & 0x80) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int rr(int v) {
        boolean c = getFlag(FLAG_C);
        int res = ((v >>> 1) & 0x7F) | (c ? 0x80 : 0);
        setFlag(FLAG_C, (v & 0x01) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int sla(int v) {
        int res = (v << 1) & 0xFF;
        setFlag(FLAG_C, (v & 0x80) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int sra(int v) {
        int res = ((v & 0x80) | (v >>> 1)) & 0xFF;
        setFlag(FLAG_C, (v & 0x01) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int sll(int v) { // Undocumented on NMOS
        int res = ((v << 1) | 1) & 0xFF;
        setFlag(FLAG_C, (v & 0x80) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    private int srl(int v) {
        int res = (v >>> 1) & 0x7F;
        setFlag(FLAG_C, (v & 0x01) != 0);
        setFlag(FLAG_H, false);
        setFlag(FLAG_N, false);
        updateSZXY(res);
        setFlag(FLAG_PV, parity(res));
        return res;
    }

    // BIT/RES/SET helpers
    private void bit(int bitNum, int value) {
        int mask = 1 << bitNum;
        int result = value & mask;
        setFlag(FLAG_Z, result == 0);
        setFlag(FLAG_Y, (value & (1 << 5)) != 0);
        setFlag(FLAG_X, (value & (1 << 3)) != 0);
        setFlag(FLAG_H, true);
        setFlag(FLAG_N, false);
        if (result == 0) {
            setFlag(FLAG_PV, true);
            setFlag(FLAG_S, false);
        } else {
            setFlag(FLAG_PV, false);
            if (bitNum == 7) setFlag(FLAG_S, (value & 0x80) != 0);
            else setFlag(FLAG_S, false);
        }
    }

    private void bitMem(int bitNum, int value, int addrHi) {
        int mask = 1 << bitNum;
        int result = value & mask;
        setFlag(FLAG_Z, result == 0);
        setFlag(FLAG_Y, (addrHi & (1 << 5)) != 0);
        setFlag(FLAG_X, (addrHi & (1 << 3)) != 0);
        setFlag(FLAG_H, true);
        setFlag(FLAG_N, false);
        if (result == 0) {
            setFlag(FLAG_PV, true);
            setFlag(FLAG_S, false);
        } else {
            setFlag(FLAG_PV, false);
            if (bitNum == 7) setFlag(FLAG_S, (value & 0x80) != 0);
            else setFlag(FLAG_S, false);
        }
    }

    private int res(int bitNum, int value) {
        return value & ~(1 << bitNum);
    }

    private int set(int bitNum, int value) {
        return value | (1 << bitNum);
    }

    // ============= Reg file helpers for CB ops =============

    // 16-bit pair helpers
    private int BC() {
        return ((B & 0xFF) << 8) | (C & 0xFF);
    }

    private void setBC(int v) {
        B = (v >>> 8) & 0xFF;
        C = v & 0xFF;
    }

    private int DE() {
        return ((D & 0xFF) << 8) | (E & 0xFF);
    }

    private void setDE(int v) {
        D = (v >>> 8) & 0xFF;
        E = v & 0xFF;
    }

    private int HL() {
        return ((H & 0xFF) << 8) | (L & 0xFF);
    }

    private void setHL(int v) {
        H = (v >>> 8) & 0xFF;
        L = v & 0xFF;
    }

    private int getReg(int code) {
        return switch (code & 7) {
            case 0 -> B;
            case 1 -> C;
            case 2 -> D;
            case 3 -> E;
            case 4 -> H;
            case 5 -> L;
            case 7 -> A;
            default -> 0;
        };
    }

    private void setReg(int code, int value) {
        value &= 0xFF;
        switch (code & 7) {
            case 0 -> B = value;
            case 1 -> C = value;
            case 2 -> D = value;
            case 3 -> E = value;
            case 4 -> H = value;
            case 5 -> L = value;
            case 7 -> A = value;
            default -> {
            }
        }
    }

    // ============= Flags =============

    private boolean getFlag(int mask) {
        return (F & mask) != 0;
    }

    private void setFlag(int mask, boolean v) {
        F = v ? (F | mask) : (F & ~mask);
    }

    // ============= Accessors (optional) =============

    public int getPC() {
        return PC;
    }

    public void setPC(int pc) {
        PC = pc & 0xFFFF;
    }

    public int getSP() {
        return SP;
    }

    public void setSP(int sp) {
        SP = sp & 0xFFFF;
    }

    public int getA() {
        return A;
    }

    public void setA(int a) {
        A = a & 0xFF;
    }

    public int getF() {
        return F;
    }

    public void setF(int f) {
        F = f & 0xFF;
    }
}
