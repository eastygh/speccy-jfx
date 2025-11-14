package spectrum.jfx.hardware.cpu;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.hardware.memory.Memory;

/**
 * Эмулятор процессора Zilog Z80
 * Реализует основные регистры, флаги и инструкции процессора Z80
 */
public class Z80CPU implements CPU {

    private static final Logger logger = LoggerFactory.getLogger(Z80CPU.class);

    // Ссылка на память
    private final Memory memory;

    // Основные 8-битные регистры
    private int regA, regB, regC, regD, regE, regH, regL;

    // Альтернативные регистры (shadow registers)
    private int regA_, regB_, regC_, regD_, regE_, regH_, regL_;

    // 16-битные регистры
    private int regIX, regIY, regSP, regPC;

    // Специальные регистры
    private int regI, regR; // Interrupt vector и Memory refresh

    // Флаги (регистр F)
    private boolean flagS, flagZ, flagH, flagPV, flagN, flagC;
    private boolean flagS_, flagZ_, flagH_, flagPV_, flagN_, flagC_; // Альтернативные флаги

    // Состояние прерываний
    private boolean iff1, iff2; // Interrupt flip-flops
    private int interruptMode; // 0, 1, или 2

    // Состояние процессора
    private boolean halted;
    private long totalCycles;

    // Константы для флагов
    private static final int FLAG_S = 0x80;  // Sign
    private static final int FLAG_Z = 0x40;  // Zero
    private static final int FLAG_H = 0x10;  // Half carry
    private static final int FLAG_PV = 0x04; // Parity/Overflow
    private static final int FLAG_N = 0x02;  // Add/Subtract
    private static final int FLAG_C = 0x01;  // Carry

    public Z80CPU(Memory memory) {
        this.memory = memory;
        logger.info("Z80 CPU initialized");
        reset();
    }

    /**
     * Сброс процессора к начальному состоянию
     */
    @Override
    public void reset() {
        logger.debug("Resetting Z80 CPU");

        // Сброс регистров
        regA = regB = regC = regD = regE = regH = regL = 0;
        regA_ = regB_ = regC_ = regD_ = regE_ = regH_ = regL_ = 0;

        regIX = regIY = 0;
        regSP = 0xFFFF; // Stack pointer в верх памяти
        regPC = 0x0000; // Program counter в начало ROM

        regI = regR = 0;

        // Сброс флагов
        flagS = flagZ = flagH = flagPV = flagN = flagC = false;
        flagS_ = flagZ_ = flagH_ = flagPV_ = flagN_ = flagC_ = false;

        // Сброс прерываний
        iff1 = iff2 = false;
        interruptMode = 0;

        halted = false;
        totalCycles = 0;

        logger.debug("Z80 CPU reset complete");
    }

    /**
     * Выполняет одну инструкцию процессора
     *
     * @return количество выполненных тактов
     */
    public int executeInstruction() {
        if (halted) {
            // Если процессор остановлен, ничего не делаем
            return 4; // NOP занимает 4 такта
        }

        // Инкремент регистра R (memory refresh)
        regR = (regR + 1) & 0x7F;

        // Читаем первый байт инструкции
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Определяем тип инструкции по префиксу
        int cycles;
        switch (opcode) {
            case 0xCB: // CB prefix - bit operations
                cycles = executeCBInstruction();
                break;
            case 0xDD: // DD prefix - IX operations
                cycles = executeDDInstruction();
                break;
            case 0xED: // ED prefix - extended operations
                cycles = executeEDInstruction();
                break;
            case 0xFD: // FD prefix - IY operations
                cycles = executeFDInstruction();
                break;
            default: // Single-byte instruction
                cycles = executeOpcode(opcode);
                break;
        }

        totalCycles += cycles;
        return cycles;
    }

    /**
     * Выполняет опкод
     */
    private int executeOpcode(int opcode) {
        int offset; // Declare at method level
        int addr;   // Declare at method level

        switch (opcode) {
            case 0x00: // NOP
                return 4;

            case 0x01: // LD BC, nn
                regC = memory.readByte(regPC++);
                regB = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 10;

            case 0x02: // LD (BC), A
                memory.writeByte(getBC(), regA);
                return 7;

            case 0x03: // INC BC
                setBC((getBC() + 1) & 0xFFFF);
                return 6;

            case 0x04: // INC B
                regB = inc8(regB);
                return 4;

            case 0x05: // DEC B
                regB = dec8(regB);
                return 4;

            case 0x06: // LD B, n
                regB = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;

            case 0x07: // RLCA
                regA = rlca(regA);
                return 4;

            case 0x08: // EX AF, AF'
                exchangeAF();
                return 4;

            case 0x09: // ADD HL, BC
                setHL(add16(getHL(), getBC()));
                return 11;

            case 0x0A: // LD A, (BC)
                regA = memory.readByte(getBC());
                return 7;

            case 0x0B: // DEC BC
                setBC((getBC() - 1) & 0xFFFF);
                return 6;

            case 0x0C: // INC C
                regC = inc8(regC);
                return 4;

            case 0x0D: // DEC C
                regC = dec8(regC);
                return 4;

            case 0x0E: // LD C, n
                regC = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;

            case 0x0F: // RRCA
                regA = rrca(regA);
                return 4;

            case 0x10: // DJNZ e
                offset = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                regB = (regB - 1) & 0xFF;
                if (regB != 0) {
                    if (offset > 127) offset -= 256;
                    regPC = (regPC + offset) & 0xFFFF;
                    return 13;
                }
                return 8;

            case 0x17: // RLA
                regA = rla(regA);
                return 4;

            case 0x1F: // RRA
                regA = rra(regA);
                return 4;

            case 0x22: // LD (nn),HL
                memory.writeWord(memory.readWord(regPC), getHL());
                regPC = (regPC + 2) & 0xFFFF;
                return 16;

            case 0x2A: // LD HL,(nn)
                setHL(memory.readWord(memory.readWord(regPC)));
                regPC = (regPC + 2) & 0xFFFF;
                return 16;

            case 0x27: // DAA (Decimal Adjust Accumulator)
                regA = daa(regA);
                return 4;

            case 0x2F: // CPL (Complement)
                regA = (~regA) & 0xFF;
                flagH = flagN = true;
                return 4;

            case 0x37: // SCF (Set Carry Flag)
                flagC = true;
                flagH = flagN = false;
                return 4;

            case 0x3F: // CCF (Complement Carry Flag)
                flagH = flagC;
                flagC = !flagC;
                flagN = false;
                return 4;

            // LD r,r instructions (0x40-0x7F except 0x76 HALT)
            case 0x40:
            case 0x41:
            case 0x42:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x47: // LD B,r
            case 0x48:
            case 0x49:
            case 0x4A:
            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x4F: // LD C,r
            case 0x50:
            case 0x51:
            case 0x52:
            case 0x53:
            case 0x54:
            case 0x55:
            case 0x57: // LD D,r
            case 0x58:
            case 0x59:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5D:
            case 0x5F: // LD E,r
            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x67: // LD H,r
            case 0x68:
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6F: // LD L,r
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
            case 0x7F: // LD A,r
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                setReg8(destReg, getReg8(srcReg));
                return 4;

            // LD r,(HL) instructions
            case 0x46: // LD B,(HL)
                regB = memory.readByte(getHL());
                return 7;
            case 0x4E: // LD C,(HL)
                regC = memory.readByte(getHL());
                return 7;
            case 0x56: // LD D,(HL)
                regD = memory.readByte(getHL());
                return 7;
            case 0x5E: // LD E,(HL)
                regE = memory.readByte(getHL());
                return 7;
            case 0x66: // LD H,(HL)
                regH = memory.readByte(getHL());
                return 7;
            case 0x6E: // LD L,(HL)
                regL = memory.readByte(getHL());
                return 7;
            case 0x7E: // LD A,(HL)
                regA = memory.readByte(getHL());
                return 7;

            // LD (HL),r instructions
            case 0x70: // LD (HL),B
                memory.writeByte(getHL(), regB);
                return 7;
            case 0x71: // LD (HL),C
                memory.writeByte(getHL(), regC);
                return 7;
            case 0x72: // LD (HL),D
                memory.writeByte(getHL(), regD);
                return 7;
            case 0x73: // LD (HL),E
                memory.writeByte(getHL(), regE);
                return 7;
            case 0x74: // LD (HL),H
                memory.writeByte(getHL(), regH);
                return 7;
            case 0x75: // LD (HL),L
                memory.writeByte(getHL(), regL);
                return 7;
            case 0x77: // LD (HL),A
                memory.writeByte(getHL(), regA);
                return 7;

            // LD r,n instructions (immediate)
            case 0x16: // LD D,n
                regD = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;
            case 0x1E: // LD E,n
                regE = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;
            case 0x26: // LD H,n
                regH = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;
            case 0x2E: // LD L,n
                regL = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;
            case 0x36: // LD (HL),n
                memory.writeByte(getHL(), memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 10;
            case 0x3E: // LD A,n
                regA = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 7;

            // Extended memory loads
            case 0x1A: // LD A,(DE)
                regA = memory.readByte(getDE());
                return 7;
            case 0x3A: // LD A,(nn)
                regA = memory.readByte(memory.readWord(regPC));
                regPC = (regPC + 2) & 0xFFFF;
                return 13;
            case 0x12: // LD (DE),A
                memory.writeByte(getDE(), regA);
                return 7;
            case 0x32: // LD (nn),A
                memory.writeByte(memory.readWord(regPC), regA);
                regPC = (regPC + 2) & 0xFFFF;
                return 13;

            // 16-bit immediate loads - LD rr,nn
            case 0x11: // LD DE,nn
                regE = memory.readByte(regPC++);
                regD = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 10;
            case 0x21: // LD HL,nn
                regL = memory.readByte(regPC++);
                regH = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                return 10;
            case 0x31: // LD SP,nn
                regSP = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                return 10;

            // 16-bit stack operations - PUSH rr
            case 0xC5: // PUSH BC
                push(getBC());
                return 11;
            case 0xD5: // PUSH DE
                push(getDE());
                return 11;
            case 0xE5: // PUSH HL
                push(getHL());
                return 11;
            case 0xF5: // PUSH AF
                push(getAF());
                return 11;

            // 16-bit stack operations - POP rr
            case 0xC1: // POP BC
                setBC(pop());
                return 10;
            case 0xD1: // POP DE
                setDE(pop());
                return 10;
            case 0xE1: // POP HL
                setHL(pop());
                return 10;
            case 0xF1: // POP AF
                setAF(pop());
                return 10;

            // 16-bit arithmetic (will be expanded later)
            case 0x13: // INC DE
                setDE((getDE() + 1) & 0xFFFF);
                return 6;
            case 0x23: // INC HL
                setHL((getHL() + 1) & 0xFFFF);
                return 6;
            case 0x33: // INC SP
                regSP = (regSP + 1) & 0xFFFF;
                return 6;
            case 0x1B: // DEC DE
                setDE((getDE() - 1) & 0xFFFF);
                return 6;
            case 0x2B: // DEC HL
                setHL((getHL() - 1) & 0xFFFF);
                return 6;
            case 0x3B: // DEC SP
                regSP = (regSP - 1) & 0xFFFF;
                return 6;

            // ADD HL,rr instructions
            case 0x19: // ADD HL,DE
                setHL(add16(getHL(), getDE()));
                return 11;
            case 0x29: // ADD HL,HL
                setHL(add16(getHL(), getHL()));
                return 11;
            case 0x39: // ADD HL,SP
                setHL(add16(getHL(), regSP));
                return 11;

            // 8-bit arithmetic with register operands
            case 0x80:
            case 0x81:
            case 0x82:
            case 0x83:
            case 0x84:
            case 0x85:
            case 0x86:
            case 0x87: // ADD A,r
                regA = add8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4; // (HL) takes 7 cycles, others 4
            case 0x88:
            case 0x89:
            case 0x8A:
            case 0x8B:
            case 0x8C:
            case 0x8D:
            case 0x8E:
            case 0x8F: // ADC A,r
                regA = adc8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;
            case 0x90:
            case 0x91:
            case 0x92:
            case 0x93:
            case 0x94:
            case 0x95:
            case 0x96:
            case 0x97: // SUB r
                regA = sub8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;
            case 0x98:
            case 0x99:
            case 0x9A:
            case 0x9B:
            case 0x9C:
            case 0x9D:
            case 0x9E:
            case 0x9F: // SBC A,r
                regA = sbc8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;
            case 0xA0:
            case 0xA1:
            case 0xA2:
            case 0xA3:
            case 0xA4:
            case 0xA5:
            case 0xA6:
            case 0xA7: // AND r
                regA = and8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;
            case 0xA8:
            case 0xA9:
            case 0xAA:
            case 0xAB:
            case 0xAC:
            case 0xAD:
            case 0xAE:
            case 0xAF: // XOR r
                regA = xor8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;
            case 0xB0:
            case 0xB1:
            case 0xB2:
            case 0xB3:
            case 0xB4:
            case 0xB5:
            case 0xB6:
            case 0xB7: // OR r
                regA = or8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;
            case 0xB8:
            case 0xB9:
            case 0xBA:
            case 0xBB:
            case 0xBC:
            case 0xBD:
            case 0xBE:
            case 0xBF: // CP r
                cp8(regA, getReg8(opcode & 0x07));
                return (opcode & 0x07) == 6 ? 7 : 4;

            // 8-bit arithmetic with immediate operands
            case 0xC6: // ADD A,n
                regA = add8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xCE: // ADC A,n
                regA = adc8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xD6: // SUB n
                regA = sub8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xDE: // SBC A,n
                regA = sbc8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xE6: // AND n
                regA = and8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xEE: // XOR n
                regA = xor8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xF6: // OR n
                regA = or8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;
            case 0xFE: // CP n
                cp8(regA, memory.readByte(regPC++));
                regPC &= 0xFFFF;
                return 7;

            // 8-bit INC/DEC missing instructions
            case 0x3C: // INC A
                regA = inc8(regA);
                return 4;
            case 0x3D: // DEC A
                regA = dec8(regA);
                return 4;

            case 0x76: // HALT
                halted = true;
                return 4;

            case 0xC3: // JP nn
                regPC = memory.readWord(regPC);
                return 10;

            // Conditional jumps - JP cc,nn
            case 0xC2: // JP NZ,nn
                if (!flagZ) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xCA: // JP Z,nn
                if (flagZ) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xD2: // JP NC,nn
                if (!flagC) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xDA: // JP C,nn
                if (flagC) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xE2: // JP PO,nn
                if (!flagPV) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xEA: // JP PE,nn
                if (flagPV) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xF2: // JP P,nn
                if (!flagS) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;
            case 0xFA: // JP M,nn
                if (flagS) {
                    regPC = memory.readWord(regPC);
                } else {
                    regPC = (regPC + 2) & 0xFFFF;
                }
                return 10;

            // Relative jumps
            case 0x18: // JR e
                offset = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                // Convert to signed offset
                if (offset > 127) offset -= 256;
                regPC = (regPC + offset) & 0xFFFF;
                return 12;

            // Conditional relative jumps
            case 0x20: // JR NZ,e
                offset = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                if (!flagZ) {
                    if (offset > 127) offset -= 256;
                    regPC = (regPC + offset) & 0xFFFF;
                    return 12;
                }
                return 7;
            case 0x28: // JR Z,e
                offset = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                if (flagZ) {
                    if (offset > 127) offset -= 256;
                    regPC = (regPC + offset) & 0xFFFF;
                    return 12;
                }
                return 7;
            case 0x30: // JR NC,e
                offset = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                if (!flagC) {
                    if (offset > 127) offset -= 256;
                    regPC = (regPC + offset) & 0xFFFF;
                    return 12;
                }
                return 7;
            case 0x38: // JR C,e
                offset = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                if (flagC) {
                    if (offset > 127) offset -= 256;
                    regPC = (regPC + offset) & 0xFFFF;
                    return 12;
                }
                return 7;

            // Indirect jumps
            case 0xE9: // JP (HL)
                regPC = getHL();
                return 4;

            case 0xC9: // RET
                regPC = pop();
                return 10;

            // Conditional returns - RET cc
            case 0xC0: // RET NZ
                if (!flagZ) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xC8: // RET Z
                if (flagZ) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xD0: // RET NC
                if (!flagC) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xD8: // RET C
                if (flagC) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xE0: // RET PO
                if (!flagPV) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xE8: // RET PE
                if (flagPV) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xF0: // RET P
                if (!flagS) {
                    regPC = pop();
                    return 11;
                }
                return 5;
            case 0xF8: // RET M
                if (flagS) {
                    regPC = pop();
                    return 11;
                }
                return 5;

            case 0xCD: // CALL nn
                addr = memory.readWord(regPC);
                regPC += 2;
                push(regPC);
                regPC = addr;
                return 17;

            // Conditional calls - CALL cc,nn
            case 0xC4: // CALL NZ,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (!flagZ) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xCC: // CALL Z,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (flagZ) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xD4: // CALL NC,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (!flagC) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xDC: // CALL C,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (flagC) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xE4: // CALL PO,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (!flagPV) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xEC: // CALL PE,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (flagPV) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xF4: // CALL P,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (!flagS) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;
            case 0xFC: // CALL M,nn
                addr = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                if (flagS) {
                    push(regPC);
                    regPC = addr;
                    return 17;
                }
                return 10;

            // Restart instructions - RST nn
            case 0xC7: // RST 0x00
                push(regPC);
                regPC = 0x0000;
                return 11;
            case 0xCF: // RST 0x08
                push(regPC);
                regPC = 0x0008;
                return 11;
            case 0xD7: // RST 0x10
                push(regPC);
                regPC = 0x0010;
                return 11;
            case 0xDF: // RST 0x18
                push(regPC);
                regPC = 0x0018;
                return 11;
            case 0xE7: // RST 0x20
                push(regPC);
                regPC = 0x0020;
                return 11;
            case 0xEF: // RST 0x28
                push(regPC);
                regPC = 0x0028;
                return 11;
            case 0xF7: // RST 0x30
                push(regPC);
                regPC = 0x0030;
                return 11;
            case 0xFF: // RST 0x38
                push(regPC);
                regPC = 0x0038;
                return 11;

            // Exchange instructions
            case 0xEB: // EX DE,HL
                int temp = getDE();
                setDE(getHL());
                setHL(temp);
                return 4;

            case 0xD9: // EXX - Exchange BC,DE,HL with BC',DE',HL'
                exchangeRegs();
                return 4;

            case 0xF9: // LD SP,HL
                regSP = getHL();
                return 6;

            // Interrupt control
            case 0xF3: // DI (Disable Interrupts)
                iff1 = iff2 = false;
                return 4;

            case 0xFB: // EI (Enable Interrupts)
                iff1 = iff2 = true;
                return 4;

            default:
                logger.warn("Unimplemented opcode: 0x{}", Integer.toHexString(opcode).toUpperCase());
                return 4; // Возвращаем минимальное количество тактов
        }
    }

    /**
     * Выполняет CB-префиксную инструкцию (битовые операции)
     *
     * @return количество выполненных тактов
     */
    private int executeCBInstruction() {
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Инкремент R для префикса CB
        regR = (regR + 1) & 0x7F;

        int regIndex = opcode & 0x07; // Индекс регистра (0-7: B,C,D,E,H,L,(HL),A)
        int operation = (opcode >> 3) & 0x07; // Операция (0-7)
        int group = (opcode >> 6) & 0x03; // Группа операций (0-3)

        switch (group) {
            case 0: // Rotations and shifts (0x00-0x3F)
                return executeCBRotateShift(operation, regIndex);

            case 1: // BIT operations (0x40-0x7F)
                int bitNum = operation;
                return executeCBBit(bitNum, regIndex);

            case 2: // RES operations (0x80-0xBF)
                bitNum = operation;
                return executeCBRes(bitNum, regIndex);

            case 3: // SET operations (0xC0-0xFF)
                bitNum = operation;
                return executeCBSet(bitNum, regIndex);

            default:
                logger.warn("Invalid CB group: {}", group);
                return 8;
        }
    }

    /**
     * Выполняет операции поворота и сдвига CB-префиксных инструкций
     */
    private int executeCBRotateShift(int operation, int regIndex) {
        int value = getReg8(regIndex);
        int result;
        boolean isMemory = (regIndex == 6); // (HL)

        switch (operation) {
            case 0: // RLC r
                result = rlc(value);
                break;
            case 1: // RRC r
                result = rrc(value);
                break;
            case 2: // RL r
                result = rl(value);
                break;
            case 3: // RR r
                result = rr(value);
                break;
            case 4: // SLA r
                result = sla(value);
                break;
            case 5: // SRA r
                result = sra(value);
                break;
            case 6: // SLL r (undocumented)
                result = sll(value);
                break;
            case 7: // SRL r
                result = srl(value);
                break;
            default:
                logger.warn("Invalid CB rotate/shift operation: {}", operation);
                return 8;
        }

        setReg8(regIndex, result);
        return isMemory ? 15 : 8;
    }

    /**
     * Выполняет BIT операции
     */
    private int executeCBBit(int bitNum, int regIndex) {
        int value = getReg8(regIndex);
        boolean bitSet = (value & (1 << bitNum)) != 0;

        flagZ = !bitSet;
        flagH = true;
        flagN = false;
        flagS = (bitNum == 7) ? bitSet : flagS; // S flag only affected by bit 7
        flagPV = flagZ;

        return (regIndex == 6) ? 12 : 8; // (HL) takes more cycles
    }

    /**
     * Выполняет RES операции (сброс бита)
     */
    private int executeCBRes(int bitNum, int regIndex) {
        int value = getReg8(regIndex);
        int result = value & ~(1 << bitNum);
        setReg8(regIndex, result);

        return (regIndex == 6) ? 15 : 8; // (HL) takes more cycles
    }

    /**
     * Выполняет SET операции (установка бита)
     */
    private int executeCBSet(int bitNum, int regIndex) {
        int value = getReg8(regIndex);
        int result = value | (1 << bitNum);
        setReg8(regIndex, result);

        return (regIndex == 6) ? 15 : 8; // (HL) takes more cycles
    }

    // Битовые операции поворота и сдвига

    private int rlc(int value) {
        int result = ((value << 1) | (value >> 7)) & 0xFF;
        flagC = (value & 0x80) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int rrc(int value) {
        int result = ((value >> 1) | (value << 7)) & 0xFF;
        flagC = (value & 0x01) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int rl(int value) {
        int result = ((value << 1) | (flagC ? 1 : 0)) & 0xFF;
        flagC = (value & 0x80) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int rr(int value) {
        int result = ((value >> 1) | (flagC ? 0x80 : 0)) & 0xFF;
        flagC = (value & 0x01) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int sla(int value) {
        int result = (value << 1) & 0xFF;
        flagC = (value & 0x80) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int sra(int value) {
        int result = ((value >> 1) | (value & 0x80)) & 0xFF;
        flagC = (value & 0x01) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int sll(int value) {
        // SLL - undocumented instruction, shift left and set bit 0
        int result = ((value << 1) | 0x01) & 0xFF;
        flagC = (value & 0x80) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    private int srl(int value) {
        int result = (value >> 1) & 0xFF;
        flagC = (value & 0x01) != 0;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = flagN = false;
        flagPV = parity(result);
        return result;
    }

    /**
     * Выполняет DD-префиксную инструкцию (операции с IX)
     *
     * @return количество выполненных тактов
     */
    private int executeDDInstruction() {
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Инкремент R для префикса DD
        regR = (regR + 1) & 0x7F;

        // Проверяем на DDCB префикс (индексированные битовые операции)
        if (opcode == 0xCB) {
            return executeDDCBInstruction();
        }

        // Реализуем DD-префиксные инструкции
        switch (opcode) {
            // LD IX,nn
            case 0x21:
                regIX = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                return 14;

            // LD (nn),IX
            case 0x22:
                memory.writeWord(memory.readWord(regPC), regIX);
                regPC = (regPC + 2) & 0xFFFF;
                return 20;

            // INC IX
            case 0x23:
                regIX = (regIX + 1) & 0xFFFF;
                return 10;

            // DEC IX
            case 0x2B:
                regIX = (regIX - 1) & 0xFFFF;
                return 10;

            // LD IX,(nn)
            case 0x2A:
                regIX = memory.readWord(memory.readWord(regPC));
                regPC = (regPC + 2) & 0xFFFF;
                return 20;

            // INC (IX+d)
            case 0x34:
                int displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                int addr = (regIX + displacement) & 0xFFFF;
                int value = memory.readByte(addr);
                int result = inc8(value);
                memory.writeByte(addr, result);
                return 23;

            // DEC (IX+d)
            case 0x35:
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                value = memory.readByte(addr);
                result = dec8(value);
                memory.writeByte(addr, result);
                return 23;

            // LD (IX+d),n
            case 0x36:
                displacement = (byte) memory.readByte(regPC++);
                int immediate = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                memory.writeByte(addr, immediate);
                return 19;

            // ADD IX,BC
            case 0x09:
                regIX = add16IX(regIX, getBC());
                return 15;

            // ADD IX,DE
            case 0x19:
                regIX = add16IX(regIX, getDE());
                return 15;

            // ADD IX,IX
            case 0x29:
                regIX = add16IX(regIX, regIX);
                return 15;

            // ADD IX,SP
            case 0x39:
                regIX = add16IX(regIX, regSP);
                return 15;

            // LD r,(IX+d) instructions
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E:
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                value = memory.readByte(addr);
                int destReg = (opcode >> 3) & 0x07;
                setReg8(destReg, value);
                return 19;

            // LD (IX+d),r instructions
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77:
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                int srcReg = opcode & 0x07;
                value = getReg8(srcReg);
                memory.writeByte(addr, value);
                return 19;

            // Arithmetic operations with (IX+d)
            case 0x86: // ADD A,(IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = add8(regA, memory.readByte(addr));
                return 19;

            case 0x8E: // ADC A,(IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = adc8(regA, memory.readByte(addr));
                return 19;

            case 0x96: // SUB (IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = sub8(regA, memory.readByte(addr));
                return 19;

            case 0x9E: // SBC A,(IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = sbc8(regA, memory.readByte(addr));
                return 19;

            case 0xA6: // AND (IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = and8(regA, memory.readByte(addr));
                return 19;

            case 0xAE: // XOR (IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = xor8(regA, memory.readByte(addr));
                return 19;

            case 0xB6: // OR (IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                regA = or8(regA, memory.readByte(addr));
                return 19;

            case 0xBE: // CP (IX+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIX + displacement) & 0xFFFF;
                cp8(regA, memory.readByte(addr));
                return 19;

            // PUSH IX
            case 0xE5:
                push(regIX);
                return 15;

            // POP IX
            case 0xE1:
                regIX = pop();
                return 14;

            // EX (SP),IX
            case 0xE3:
                int temp = memory.readWord(regSP);
                memory.writeWord(regSP, regIX);
                regIX = temp;
                return 23;

            // JP (IX)
            case 0xE9:
                regPC = regIX;
                return 8;

            // LD SP,IX
            case 0xF9:
                regSP = regIX;
                return 10;

            default:
                logger.warn("Unimplemented DD opcode: 0xDD{}", Integer.toHexString(opcode).toUpperCase());
                return 8; // Базовое время для DD инструкций
        }
    }

    /**
     * 16-битное сложение для IX регистра
     */
    private int add16IX(int a, int b) {
        int result = a + b;
        flagC = (result & 0x10000) != 0;
        flagH = ((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF;
        flagN = false;
        // S, Z, PV flags не изменяются для ADD IX,rr
        return result & 0xFFFF;
    }

    /**
     * Выполняет ED-префиксную инструкцию (расширенный набор)
     *
     * @return количество выполненных тактов
     */
    private int executeEDInstruction() {
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Инкремент R для префикса ED
        regR = (regR + 1) & 0x7F;

        // Реализуем ED-префиксные инструкции
        switch (opcode) {
            // 16-bit loads - LD (nn),rr
            case 0x43: // LD (nn),BC
                memory.writeWord(memory.readWord(regPC), getBC());
                regPC = (regPC + 2) & 0xFFFF;
                return 20;
            case 0x53: // LD (nn),DE
                memory.writeWord(memory.readWord(regPC), getDE());
                regPC = (regPC + 2) & 0xFFFF;
                return 20;
            case 0x63: // LD (nn),HL
                memory.writeWord(memory.readWord(regPC), getHL());
                regPC = (regPC + 2) & 0xFFFF;
                return 20;
            case 0x73: // LD (nn),SP
                memory.writeWord(memory.readWord(regPC), regSP);
                regPC = (regPC + 2) & 0xFFFF;
                return 20;

            // 16-bit loads - LD rr,(nn)
            case 0x4B: // LD BC,(nn)
                setBC(memory.readWord(memory.readWord(regPC)));
                regPC = (regPC + 2) & 0xFFFF;
                return 20;
            case 0x5B: // LD DE,(nn)
                setDE(memory.readWord(memory.readWord(regPC)));
                regPC = (regPC + 2) & 0xFFFF;
                return 20;
            case 0x6B: // LD HL,(nn)
                setHL(memory.readWord(memory.readWord(regPC)));
                regPC = (regPC + 2) & 0xFFFF;
                return 20;
            case 0x7B: // LD SP,(nn)
                regSP = memory.readWord(memory.readWord(regPC));
                regPC = (regPC + 2) & 0xFFFF;
                return 20;

            // 16-bit arithmetic - ADC HL,rr
            case 0x4A: // ADC HL,BC
                setHL(adc16(getHL(), getBC()));
                return 15;
            case 0x5A: // ADC HL,DE
                setHL(adc16(getHL(), getDE()));
                return 15;
            case 0x6A: // ADC HL,HL
                setHL(adc16(getHL(), getHL()));
                return 15;
            case 0x7A: // ADC HL,SP
                setHL(adc16(getHL(), regSP));
                return 15;

            // 16-bit arithmetic - SBC HL,rr
            case 0x42: // SBC HL,BC
                setHL(sbc16(getHL(), getBC()));
                return 15;
            case 0x52: // SBC HL,DE
                setHL(sbc16(getHL(), getDE()));
                return 15;
            case 0x62: // SBC HL,HL
                setHL(sbc16(getHL(), getHL()));
                return 15;
            case 0x72: // SBC HL,SP
                setHL(sbc16(getHL(), regSP));
                return 15;

            // NEG - Negate accumulator
            case 0x44:
            case 0x4C:
            case 0x54:
            case 0x5C:
            case 0x64:
            case 0x6C:
            case 0x74:
            case 0x7C:
                regA = neg8(regA);
                return 8;

            // RETN - Return from non-maskable interrupt
            case 0x45:
            case 0x55:
            case 0x65:
            case 0x75:
                regPC = pop();
                iff1 = iff2;
                return 14;

            // IM - Set interrupt mode
            case 0x46:
            case 0x4E:
            case 0x66:
            case 0x6E: // IM 0
                interruptMode = 0;
                return 8;
            case 0x56:
            case 0x76: // IM 1
                interruptMode = 1;
                return 8;
            case 0x5E:
            case 0x7E: // IM 2
                interruptMode = 2;
                return 8;

            // LD I,A / LD R,A
            case 0x47: // LD I,A
                regI = regA;
                return 9;
            case 0x4F: // LD R,A
                regR = regA & 0x7F; // R register is 7-bit
                return 9;

            // LD A,I / LD A,R
            case 0x57: // LD A,I
                regA = regI;
                flagS = (regI & 0x80) != 0;
                flagZ = regI == 0;
                flagH = flagN = false;
                flagPV = iff2;
                return 9;
            case 0x5F: // LD A,R
                regA = regR;
                flagS = (regR & 0x80) != 0;
                flagZ = regR == 0;
                flagH = flagN = false;
                flagPV = iff2;
                return 9;

            // RETI - Return from interrupt
            case 0x4D:
            case 0x6D:
            case 0x7D:
                regPC = pop();
                iff1 = iff2;
                // Signal to interrupt controller that interrupt routine has finished
                return 14;

            // RRD - Rotate right decimal
            case 0x67:
                int value = memory.readByte(getHL());
                int newA = (regA & 0xF0) | (value & 0x0F);
                int newValue = ((value >> 4) & 0x0F) | ((regA & 0x0F) << 4);
                regA = newA;
                memory.writeByte(getHL(), newValue);
                flagS = (regA & 0x80) != 0;
                flagZ = regA == 0;
                flagH = flagN = false;
                flagPV = parity(regA);
                return 18;

            // RLD - Rotate left decimal
            case 0x6F:
                value = memory.readByte(getHL());
                newA = (regA & 0xF0) | ((value >> 4) & 0x0F);
                newValue = ((value << 4) & 0xF0) | (regA & 0x0F);
                regA = newA;
                memory.writeByte(getHL(), newValue);
                flagS = (regA & 0x80) != 0;
                flagZ = regA == 0;
                flagH = flagN = false;
                flagPV = parity(regA);
                return 18;

            // Block load instructions
            case 0xA0: // LDI
                return executeLDI();
            case 0xA8: // LDD
                return executeLDD();
            case 0xB0: // LDIR
                return executeLDIR();
            case 0xB8: // LDDR
                return executeLDDR();

            // Block compare instructions
            case 0xA1: // CPI
                return executeCPI();
            case 0xA9: // CPD
                return executeCPD();
            case 0xB1: // CPIR
                return executeCPIR();
            case 0xB9: // CPDR
                return executeCPDR();

            // I/O instructions
            case 0x40:
            case 0x48:
            case 0x50:
            case 0x58: // IN r,(C)
            case 0x60:
            case 0x68:
            case 0x78:
                int reg = (opcode >> 3) & 0x07;
                int portValue = ioRead(regC);
                if (reg != 6) { // Not IN (C) which doesn't store result
                    setReg8(reg, portValue);
                }
                flagS = (portValue & 0x80) != 0;
                flagZ = portValue == 0;
                flagH = flagN = false;
                flagPV = parity(portValue);
                return 12;

            case 0x41:
            case 0x49:
            case 0x51:
            case 0x59: // OUT (C),r
            case 0x61:
            case 0x69:
            case 0x71:
            case 0x79:
                reg = (opcode >> 3) & 0x07;
                int outValue = (reg == 6) ? 0 : getReg8(reg); // OUT (C),0 for invalid register
                ioWrite(regC, outValue);
                return 12;

            // Block I/O instructions
            case 0xA2: // INI
                return executeINI();
            case 0xAA: // IND
                return executeIND();
            case 0xB2: // INIR
                return executeINIR();
            case 0xBA: // INDR
                return executeINDR();

            case 0xA3: // OUTI
                return executeOUTI();
            case 0xAB: // OUTD
                return executeOUTD();
            case 0xB3: // OTIR
                return executeOTIR();
            case 0xBB: // OTDR
                return executeOTDR();

            default:
                logger.warn("Unimplemented ED opcode: 0xED{}", Integer.toHexString(opcode).toUpperCase());
                return 8; // Базовое время для ED инструкций
        }
    }

    /**
     * Выполняет FD-префиксную инструкцию (операции с IY)
     *
     * @return количество выполненных тактов
     */
    private int executeFDInstruction() {
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Инкремент R для префикса FD
        regR = (regR + 1) & 0x7F;

        // Проверяем на FDCB префикс (индексированные битовые операции)
        if (opcode == 0xCB) {
            return executeFDCBInstruction();
        }

        // Реализуем FD-префиксные инструкции (аналогично DD, но с IY)
        switch (opcode) {
            // LD IY,nn
            case 0x21:
                regIY = memory.readWord(regPC);
                regPC = (regPC + 2) & 0xFFFF;
                return 14;

            // LD (nn),IY
            case 0x22:
                memory.writeWord(memory.readWord(regPC), regIY);
                regPC = (regPC + 2) & 0xFFFF;
                return 20;

            // INC IY
            case 0x23:
                regIY = (regIY + 1) & 0xFFFF;
                return 10;

            // DEC IY
            case 0x2B:
                regIY = (regIY - 1) & 0xFFFF;
                return 10;

            // LD IY,(nn)
            case 0x2A:
                regIY = memory.readWord(memory.readWord(regPC));
                regPC = (regPC + 2) & 0xFFFF;
                return 20;

            // INC (IY+d)
            case 0x34:
                int displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                int addr = (regIY + displacement) & 0xFFFF;
                int value = memory.readByte(addr);
                int result = inc8(value);
                memory.writeByte(addr, result);
                return 23;

            // DEC (IY+d)
            case 0x35:
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                value = memory.readByte(addr);
                result = dec8(value);
                memory.writeByte(addr, result);
                return 23;

            // LD (IY+d),n
            case 0x36:
                displacement = (byte) memory.readByte(regPC++);
                int immediate = memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                memory.writeByte(addr, immediate);
                return 19;

            // ADD IY,BC
            case 0x09:
                regIY = add16IY(regIY, getBC());
                return 15;

            // ADD IY,DE
            case 0x19:
                regIY = add16IY(regIY, getDE());
                return 15;

            // ADD IY,IY
            case 0x29:
                regIY = add16IY(regIY, regIY);
                return 15;

            // ADD IY,SP
            case 0x39:
                regIY = add16IY(regIY, regSP);
                return 15;

            // LD r,(IY+d) instructions
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E:
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                value = memory.readByte(addr);
                int destReg = (opcode >> 3) & 0x07;
                setReg8(destReg, value);
                return 19;

            // LD (IY+d),r instructions
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77:
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                int srcReg = opcode & 0x07;
                value = getReg8(srcReg);
                memory.writeByte(addr, value);
                return 19;

            // Arithmetic operations with (IY+d)
            case 0x86: // ADD A,(IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = add8(regA, memory.readByte(addr));
                return 19;

            case 0x8E: // ADC A,(IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = adc8(regA, memory.readByte(addr));
                return 19;

            case 0x96: // SUB (IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = sub8(regA, memory.readByte(addr));
                return 19;

            case 0x9E: // SBC A,(IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = sbc8(regA, memory.readByte(addr));
                return 19;

            case 0xA6: // AND (IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = and8(regA, memory.readByte(addr));
                return 19;

            case 0xAE: // XOR (IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = xor8(regA, memory.readByte(addr));
                return 19;

            case 0xB6: // OR (IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                regA = or8(regA, memory.readByte(addr));
                return 19;

            case 0xBE: // CP (IY+d)
                displacement = (byte) memory.readByte(regPC++);
                regPC &= 0xFFFF;
                addr = (regIY + displacement) & 0xFFFF;
                cp8(regA, memory.readByte(addr));
                return 19;

            // PUSH IY
            case 0xE5:
                push(regIY);
                return 15;

            // POP IY
            case 0xE1:
                regIY = pop();
                return 14;

            // EX (SP),IY
            case 0xE3:
                int temp = memory.readWord(regSP);
                memory.writeWord(regSP, regIY);
                regIY = temp;
                return 23;

            // JP (IY)
            case 0xE9:
                regPC = regIY;
                return 8;

            // LD SP,IY
            case 0xF9:
                regSP = regIY;
                return 10;

            default:
                logger.warn("Unimplemented FD opcode: 0xFD{}", Integer.toHexString(opcode).toUpperCase());
                return 8; // Базовое время для FD инструкций
        }
    }

    /**
     * 16-битное сложение для IY регистра
     */
    private int add16IY(int a, int b) {
        int result = a + b;
        flagC = (result & 0x10000) != 0;
        flagH = ((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF;
        flagN = false;
        // S, Z, PV flags не изменяются для ADD IY,rr
        return result & 0xFFFF;
    }

    /**
     * Выполняет DDCB-префиксную инструкцию (битовые операции с IX+d)
     *
     * @return количество выполненных тактов
     */
    private int executeDDCBInstruction() {
        // Читаем смещение
        int displacement = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Читаем итоговый опкод
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Преобразуем смещение в signed byte
        if (displacement > 127) {
            displacement -= 256;
        }

        // Вычисляем адрес
        int addr = (regIX + displacement) & 0xFFFF;

        int regIndex = opcode & 0x07; // Индекс регистра для недокументированных операций
        int operation = (opcode >> 3) & 0x07; // Операция (0-7)
        int group = (opcode >> 6) & 0x03; // Группа операций (0-3)

        switch (group) {
            case 0: // Rotations and shifts (0x00-0x3F)
                return executeDDCBRotateShift(operation, addr, regIndex);

            case 1: // BIT operations (0x40-0x7F)
                int bitNum = operation;
                return executeDDCBBit(bitNum, addr);

            case 2: // RES operations (0x80-0xBF)
                bitNum = operation;
                return executeDDCBRes(bitNum, addr, regIndex);

            case 3: // SET operations (0xC0-0xFF)
                bitNum = operation;
                return executeDDCBSet(bitNum, addr, regIndex);

            default:
                logger.warn("Invalid DDCB group: {}", group);
                return 23;
        }
    }

    /**
     * Выполняет операции поворота и сдвига DDCB-префиксных инструкций
     */
    private int executeDDCBRotateShift(int operation, int addr, int regIndex) {
        int value = memory.readByte(addr);
        int result;

        switch (operation) {
            case 0: // RLC (IX+d)
                result = rlc(value);
                break;
            case 1: // RRC (IX+d)
                result = rrc(value);
                break;
            case 2: // RL (IX+d)
                result = rl(value);
                break;
            case 3: // RR (IX+d)
                result = rr(value);
                break;
            case 4: // SLA (IX+d)
                result = sla(value);
                break;
            case 5: // SRA (IX+d)
                result = sra(value);
                break;
            case 6: // SLL (IX+d) (undocumented)
                result = sll(value);
                break;
            case 7: // SRL (IX+d)
                result = srl(value);
                break;
            default:
                logger.warn("Invalid DDCB rotate/shift operation: {}", operation);
                return 23;
        }

        memory.writeByte(addr, result);

        // Недокументированная возможность: также записать результат в регистр
        if (regIndex != 6) { // Не для (HL)
            setReg8(regIndex, result);
        }

        return 23;
    }

    /**
     * Выполняет BIT операции для DDCB
     */
    private int executeDDCBBit(int bitNum, int addr) {
        int value = memory.readByte(addr);
        boolean bitSet = (value & (1 << bitNum)) != 0;

        flagZ = !bitSet;
        flagH = true;
        flagN = false;
        flagS = (bitNum == 7) ? bitSet : flagS;
        flagPV = flagZ;

        return 20;
    }

    /**
     * Выполняет RES операции для DDCB (сброс бита)
     */
    private int executeDDCBRes(int bitNum, int addr, int regIndex) {
        int value = memory.readByte(addr);
        int result = value & ~(1 << bitNum);
        memory.writeByte(addr, result);

        // Недокументированная возможность: также записать результат в регистр
        if (regIndex != 6) { // Не для (HL)
            setReg8(regIndex, result);
        }

        return 23;
    }

    /**
     * Выполняет SET операции для DDCB (установка бита)
     */
    private int executeDDCBSet(int bitNum, int addr, int regIndex) {
        int value = memory.readByte(addr);
        int result = value | (1 << bitNum);
        memory.writeByte(addr, result);

        // Недокументированная возможность: также записать результат в регистр
        if (regIndex != 6) { // Не для (HL)
            setReg8(regIndex, result);
        }

        return 23;
    }

    /**
     * Выполняет FDCB-префиксную инструкцию (битовые операции с IY+d)
     *
     * @return количество выполненных тактов
     */
    private int executeFDCBInstruction() {
        // Читаем смещение
        int displacement = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Читаем итоговый опкод
        int opcode = memory.readByte(regPC);
        regPC = (regPC + 1) & 0xFFFF;

        // Преобразуем смещение в signed byte
        if (displacement > 127) {
            displacement -= 256;
        }

        // Вычисляем адрес
        int addr = (regIY + displacement) & 0xFFFF;

        int regIndex = opcode & 0x07; // Индекс регистра для недокументированных операций
        int operation = (opcode >> 3) & 0x07; // Операция (0-7)
        int group = (opcode >> 6) & 0x03; // Группа операций (0-3)

        switch (group) {
            case 0: // Rotations and shifts (0x00-0x3F)
                return executeFDCBRotateShift(operation, addr, regIndex);

            case 1: // BIT operations (0x40-0x7F)
                int bitNum = operation;
                return executeFDCBBit(bitNum, addr);

            case 2: // RES operations (0x80-0xBF)
                bitNum = operation;
                return executeFDCBRes(bitNum, addr, regIndex);

            case 3: // SET operations (0xC0-0xFF)
                bitNum = operation;
                return executeFDCBSet(bitNum, addr, regIndex);

            default:
                logger.warn("Invalid FDCB group: {}", group);
                return 23;
        }
    }

    /**
     * Выполняет операции поворота и сдвига FDCB-префиксных инструкций
     */
    private int executeFDCBRotateShift(int operation, int addr, int regIndex) {
        int value = memory.readByte(addr);
        int result;

        switch (operation) {
            case 0: // RLC (IY+d)
                result = rlc(value);
                break;
            case 1: // RRC (IY+d)
                result = rrc(value);
                break;
            case 2: // RL (IY+d)
                result = rl(value);
                break;
            case 3: // RR (IY+d)
                result = rr(value);
                break;
            case 4: // SLA (IY+d)
                result = sla(value);
                break;
            case 5: // SRA (IY+d)
                result = sra(value);
                break;
            case 6: // SLL (IY+d) (undocumented)
                result = sll(value);
                break;
            case 7: // SRL (IY+d)
                result = srl(value);
                break;
            default:
                logger.warn("Invalid FDCB rotate/shift operation: {}", operation);
                return 23;
        }

        memory.writeByte(addr, result);

        // Недокументированная возможность: также записать результат в регистр
        if (regIndex != 6) { // Не для (HL)
            setReg8(regIndex, result);
        }

        return 23;
    }

    /**
     * Выполняет BIT операции для FDCB
     */
    private int executeFDCBBit(int bitNum, int addr) {
        int value = memory.readByte(addr);
        boolean bitSet = (value & (1 << bitNum)) != 0;

        flagZ = !bitSet;
        flagH = true;
        flagN = false;
        flagS = (bitNum == 7) ? bitSet : flagS;
        flagPV = flagZ;

        return 20;
    }

    /**
     * Выполняет RES операции для FDCB (сброс бита)
     */
    private int executeFDCBRes(int bitNum, int addr, int regIndex) {
        int value = memory.readByte(addr);
        int result = value & ~(1 << bitNum);
        memory.writeByte(addr, result);

        // Недокументированная возможность: также записать результат в регистр
        if (regIndex != 6) { // Не для (HL)
            setReg8(regIndex, result);
        }

        return 23;
    }

    /**
     * Выполняет SET операции для FDCB (установка бита)
     */
    private int executeFDCBSet(int bitNum, int addr, int regIndex) {
        int value = memory.readByte(addr);
        int result = value | (1 << bitNum);
        memory.writeByte(addr, result);

        // Недокументированная возможность: также записать результат в регистр
        if (regIndex != 6) { // Не для (HL)
            setReg8(regIndex, result);
        }

        return 23;
    }

    // Вспомогательные методы для работы с 16-битными регистрами

    private int getBC() {
        return (regB << 8) | regC;
    }

    private void setBC(int value) {
        regB = (value >> 8) & 0xFF;
        regC = value & 0xFF;
    }

    private int getDE() {
        return (regD << 8) | regE;
    }

    private void setDE(int value) {
        regD = (value >> 8) & 0xFF;
        regE = value & 0xFF;
    }

    private int getHL() {
        return (regH << 8) | regL;
    }

    private void setHL(int value) {
        regH = (value >> 8) & 0xFF;
        regL = value & 0xFF;
    }

    private int getIX() {
        return regIX;
    }

    private void setIX(int value) {
        regIX = value & 0xFFFF;
    }

    private int getIY() {
        return regIY;
    }

    private void setIY(int value) {
        regIY = value & 0xFFFF;
    }

    // Вспомогательные методы для работы с условиями

    /**
     * Проверяет выполнение условия
     *
     * @param condition код условия (0-7)
     * @return true если условие выполнено
     */
    private boolean checkCondition(int condition) {
        switch (condition) {
            case 0:
                return !flagZ;  // NZ
            case 1:
                return flagZ;   // Z
            case 2:
                return !flagC;  // NC
            case 3:
                return flagC;   // C
            case 4:
                return !flagPV; // PO
            case 5:
                return flagPV;  // PE
            case 6:
                return !flagS;  // P
            case 7:
                return flagS;   // M
            default:
                return false;
        }
    }

    // Вспомогательные методы для работы с условиями

    /**
     * Получает значение 8-битного регистра по индексу
     *
     * @param regIndex индекс регистра (0-7): B, C, D, E, H, L, (HL), A
     * @return значение регистра
     */
    private int getReg8(int regIndex) {
        switch (regIndex) {
            case 0:
                return regB;
            case 1:
                return regC;
            case 2:
                return regD;
            case 3:
                return regE;
            case 4:
                return regH;
            case 5:
                return regL;
            case 6:
                return memory.readByte(getHL()); // (HL)
            case 7:
                return regA;
            default:
                throw new IllegalArgumentException("Invalid register index: " + regIndex);
        }
    }

    /**
     * Устанавливает значение 8-битного регистра по индексу
     *
     * @param regIndex индекс регистра (0-7): B, C, D, E, H, L, (HL), A
     * @param value    значение для записи
     */
    private void setReg8(int regIndex, int value) {
        value &= 0xFF;
        switch (regIndex) {
            case 0:
                regB = value;
                break;
            case 1:
                regC = value;
                break;
            case 2:
                regD = value;
                break;
            case 3:
                regE = value;
                break;
            case 4:
                regH = value;
                break;
            case 5:
                regL = value;
                break;
            case 6:
                memory.writeByte(getHL(), value);
                break; // (HL)
            case 7:
                regA = value;
                break;
            default:
                throw new IllegalArgumentException("Invalid register index: " + regIndex);
        }
    }

    /**
     * Получает значение 16-битного регистра по индексу для POP/PUSH
     *
     * @param regIndex индекс регистра (0-3): BC, DE, HL, AF
     * @return значение регистра
     */
    private int getReg16Stack(int regIndex) {
        switch (regIndex) {
            case 0:
                return getBC();
            case 1:
                return getDE();
            case 2:
                return getHL();
            case 3:
                return getAF(); // AF для стековых операций
            default:
                throw new IllegalArgumentException("Invalid register index: " + regIndex);
        }
    }

    /**
     * Устанавливает значение 16-битного регистра по индексу для POP/PUSH
     *
     * @param regIndex индекс регистра (0-3): BC, DE, HL, AF
     * @param value    значение для записи
     */
    private void setReg16Stack(int regIndex, int value) {
        value &= 0xFFFF;
        switch (regIndex) {
            case 0:
                setBC(value);
                break;
            case 1:
                setDE(value);
                break;
            case 2:
                setHL(value);
                break;
            case 3:
                setAF(value);
                break; // AF для стековых операций
            default:
                throw new IllegalArgumentException("Invalid register index: " + regIndex);
        }
    }

    /**
     * Получает значение 16-битного регистра по индексу для арифметики
     *
     * @param regIndex индекс регистра (0-3): BC, DE, HL, SP
     * @return значение регистра
     */
    private int getReg16Math(int regIndex) {
        switch (regIndex) {
            case 0:
                return getBC();
            case 1:
                return getDE();
            case 2:
                return getHL();
            case 3:
                return regSP;
            default:
                throw new IllegalArgumentException("Invalid register index: " + regIndex);
        }
    }

    /**
     * Устанавливает значение 16-битного регистра по индексу для арифметики
     *
     * @param regIndex индекс регистра (0-3): BC, DE, HL, SP
     * @param value    значение для записи
     */
    private void setReg16Math(int regIndex, int value) {
        value &= 0xFFFF;
        switch (regIndex) {
            case 0:
                setBC(value);
                break;
            case 1:
                setDE(value);
                break;
            case 2:
                setHL(value);
                break;
            case 3:
                regSP = value;
                break;
            default:
                throw new IllegalArgumentException("Invalid register index: " + regIndex);
        }
    }

    private int getAF() {
        int flags = 0;
        if (flagS) flags |= FLAG_S;
        if (flagZ) flags |= FLAG_Z;
        if (flagH) flags |= FLAG_H;
        if (flagPV) flags |= FLAG_PV;
        if (flagN) flags |= FLAG_N;
        if (flagC) flags |= FLAG_C;
        return (regA << 8) | flags;
    }

    private void setAF(int value) {
        regA = (value >> 8) & 0xFF;
        int flags = value & 0xFF;
        flagS = (flags & FLAG_S) != 0;
        flagZ = (flags & FLAG_Z) != 0;
        flagH = (flags & FLAG_H) != 0;
        flagPV = (flags & FLAG_PV) != 0;
        flagN = (flags & FLAG_N) != 0;
        flagC = (flags & FLAG_C) != 0;
    }

    // Арифметические операции

    private int inc8(int value) {
        int result = (value + 1) & 0xFF;
        setFlags8(result, false, (value & 0x0F) == 0x0F, false);
        flagN = false;
        return result;
    }

    private int dec8(int value) {
        int result = (value - 1) & 0xFF;
        setFlags8(result, false, (value & 0x0F) == 0x00, false);
        flagN = true;
        return result;
    }

    private int add16(int a, int b) {
        int result = a + b;
        flagC = (result & 0x10000) != 0;
        flagH = ((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF;
        flagN = false;
        return result & 0xFFFF;
    }

    // 16-bit arithmetic operations (extended)

    private int adc16(int a, int b) {
        int carry = flagC ? 1 : 0;
        long result = (long) a + b + carry;

        flagS = (result & 0x8000L) != 0;
        flagZ = (result & 0xFFFFL) == 0;
        flagH = ((a & 0x0FFF) + (b & 0x0FFF) + carry) > 0x0FFF;
        // Overflow: result different sign from operands (both same sign)
        flagPV = ((a ^ (int) result) & (b ^ (int) result) & 0x8000) != 0;
        flagN = false;
        flagC = result > 0xFFFF;

        return (int) (result & 0xFFFF);
    }

    private int sbc16(int a, int b) {
        int carry = flagC ? 1 : 0;
        long result = (long) a - b - carry;

        flagS = (result & 0x8000L) != 0;
        flagZ = (result & 0xFFFFL) == 0;
        flagH = (a & 0x0FFF) < ((b & 0x0FFF) + carry);
        // Overflow: operands different sign, result different from first operand
        flagPV = ((a ^ b) & (a ^ (int) result) & 0x8000) != 0;
        flagN = true;
        flagC = result < 0;

        return (int) (result & 0xFFFF);
    }

    // 8-bit arithmetic operations

    private int add8(int a, int b) {
        int result = (a + b) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = ((a & 0x0F) + (b & 0x0F)) > 0x0F;
        flagPV = ((a ^ result) & (b ^ result) & 0x80) != 0; // Overflow
        flagN = false;
        flagC = (a + b) > 0xFF;
        return result;
    }

    private int adc8(int a, int b) {
        int carry = flagC ? 1 : 0;
        int result = (a + b + carry) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = ((a & 0x0F) + (b & 0x0F) + carry) > 0x0F;
        flagPV = ((a ^ result) & (b ^ result) & 0x80) != 0; // Overflow
        flagN = false;
        flagC = (a + b + carry) > 0xFF;
        return result;
    }

    private int sub8(int a, int b) {
        int result = (a - b) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (a & 0x0F) < (b & 0x0F);
        flagPV = ((a ^ b) & (a ^ result) & 0x80) != 0; // Overflow
        flagN = true;
        flagC = a < b;
        return result;
    }

    private int sbc8(int a, int b) {
        int carry = flagC ? 1 : 0;
        int result = (a - b - carry) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (a & 0x0F) < ((b & 0x0F) + carry);
        flagPV = ((a ^ b) & (a ^ result) & 0x80) != 0; // Overflow
        flagN = true;
        flagC = a < (b + carry);
        return result;
    }

    private int and8(int a, int b) {
        int result = (a & b) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = true; // Always set for AND
        flagPV = parity(result);
        flagN = false;
        flagC = false;
        return result;
    }

    private int or8(int a, int b) {
        int result = (a | b) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = false;
        flagPV = parity(result);
        flagN = false;
        flagC = false;
        return result;
    }

    private int xor8(int a, int b) {
        int result = (a ^ b) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = false;
        flagPV = parity(result);
        flagN = false;
        flagC = false;
        return result;
    }

    private void cp8(int a, int b) {
        // Compare is subtraction without storing result
        int result = (a - b) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (a & 0x0F) < (b & 0x0F);
        flagPV = ((a ^ b) & (a ^ result) & 0x80) != 0; // Overflow
        flagN = true;
        flagC = a < b;
    }

    /**
     * Calculates parity of a value (even parity = true)
     */
    private boolean parity(int value) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
            if ((value & (1 << i)) != 0) {
                count++;
            }
        }
        return (count & 1) == 0; // Even number of bits set
    }

    // Операции поворота

    private int rlca(int value) {
        int result = ((value << 1) | (value >> 7)) & 0xFF;
        flagC = (value & 0x80) != 0;
        flagH = flagN = false;
        return result;
    }

    private int rrca(int value) {
        int result = ((value >> 1) | (value << 7)) & 0xFF;
        flagC = (value & 0x01) != 0;
        flagH = flagN = false;
        return result;
    }

    private int rla(int value) {
        int result = ((value << 1) | (flagC ? 1 : 0)) & 0xFF;
        flagC = (value & 0x80) != 0;
        flagH = flagN = false;
        return result;
    }

    private int rra(int value) {
        int result = ((value >> 1) | (flagC ? 0x80 : 0)) & 0xFF;
        flagC = (value & 0x01) != 0;
        flagH = flagN = false;
        return result;
    }

    /**
     * DAA - Decimal Adjust Accumulator
     * Adjusts result of addition/subtraction for BCD arithmetic
     */
    private int daa(int value) {
        int result = value;
        int correction = 0;

        if (flagH || (!flagN && (result & 0x0F) > 9)) {
            correction |= 0x06;
        }

        if (flagC || (!flagN && result > 0x99)) {
            correction |= 0x60;
            flagC = true;
        }

        if (flagN) {
            result = (result - correction) & 0xFF;
        } else {
            result = (result + correction) & 0xFF;
        }

        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (!flagN && (value & 0x0F) > 9) || (flagN && flagH && (value & 0x0F) < 6);
        flagPV = parity(result);

        return result;
    }

    // Обмен регистров

    private void exchangeRegs() {
        // Exchange BC with BC'
        int tempB = regB, tempC = regC;
        regB = regB_;
        regC = regC_;
        regB_ = tempB;
        regC_ = tempC;

        // Exchange DE with DE'
        int tempD = regD, tempE = regE;
        regD = regD_;
        regE = regE_;
        regD_ = tempD;
        regE_ = tempE;

        // Exchange HL with HL'
        int tempH = regH, tempL = regL;
        regH = regH_;
        regL = regL_;
        regH_ = tempH;
        regL_ = tempL;
    }

    // Работа со стеком

    private void push(int value) {
        regSP = (regSP - 1) & 0xFFFF;
        memory.writeByte(regSP, (value >> 8) & 0xFF);
        regSP = (regSP - 1) & 0xFFFF;
        memory.writeByte(regSP, value & 0xFF);
    }

    private int pop() {
        int low = memory.readByte(regSP);
        regSP = (regSP + 1) & 0xFFFF;
        int high = memory.readByte(regSP);
        regSP = (regSP + 1) & 0xFFFF;
        return (high << 8) | low;
    }

    // Обмен регистров

    private void exchangeAF() {
        int tempA = regA;
        boolean tempS = flagS, tempZ = flagZ, tempH = flagH;
        boolean tempPV = flagPV, tempN = flagN, tempC = flagC;

        regA = regA_;
        flagS = flagS_;
        flagZ = flagZ_;
        flagH = flagH_;
        flagPV = flagPV_;
        flagN = flagN_;
        flagC = flagC_;

        regA_ = tempA;
        flagS_ = tempS;
        flagZ_ = tempZ;
        flagH_ = tempH;
        flagPV_ = tempPV;
        flagN_ = tempN;
        flagC_ = tempC;
    }

    // Установка флагов

    private void setFlags8(int result, boolean carryIn, boolean halfCarryIn, boolean overflow) {
        flagS = (result & 0x80) != 0;
        flagZ = (result & 0xFF) == 0;
        flagH = halfCarryIn;
        flagPV = overflow;
        flagC = carryIn;
    }

    // Дополнительные арифметические операции для ED инструкций

    private int neg8(int value) {
        int result = (-value) & 0xFF;
        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (value & 0x0F) != 0;
        flagPV = value == 0x80; // Overflow only when negating -128
        flagN = true;
        flagC = value != 0;
        return result;
    }

    // I/O операции (заглушки - должны быть переопределены в зависимости от системы)

    private int ioRead(int port) {
        // В реальной реализации здесь должно быть чтение с порта I/O
        logger.debug("I/O Read from port 0x{:02X}", port);
        return 0xFF; // Заглушка
    }

    private void ioWrite(int port, int value) {
        // В реальной реализации здесь должна быть запись в порт I/O
        logger.debug("I/O Write 0x{:02X} to port 0x{:02X}", value, port);
    }

    // Блочные операции загрузки

    private int executeLDI() {
        int value = memory.readByte(getHL());
        memory.writeByte(getDE(), value);
        setHL((getHL() + 1) & 0xFFFF);
        setDE((getDE() + 1) & 0xFFFF);
        setBC((getBC() - 1) & 0xFFFF);

        flagH = flagN = false;
        flagPV = getBC() != 0;
        return 16;
    }

    private int executeLDD() {
        int value = memory.readByte(getHL());
        memory.writeByte(getDE(), value);
        setHL((getHL() - 1) & 0xFFFF);
        setDE((getDE() - 1) & 0xFFFF);
        setBC((getBC() - 1) & 0xFFFF);

        flagH = flagN = false;
        flagPV = getBC() != 0;
        return 16;
    }

    private int executeLDIR() {
        executeLDI();
        if (getBC() != 0) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    private int executeLDDR() {
        executeLDD();
        if (getBC() != 0) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    // Блочные операции сравнения

    private int executeCPI() {
        int value = memory.readByte(getHL());
        int result = (regA - value) & 0xFF;
        setHL((getHL() + 1) & 0xFFFF);
        setBC((getBC() - 1) & 0xFFFF);

        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (regA & 0x0F) < (value & 0x0F);
        flagPV = getBC() != 0;
        flagN = true;
        return 16;
    }

    private int executeCPD() {
        int value = memory.readByte(getHL());
        int result = (regA - value) & 0xFF;
        setHL((getHL() - 1) & 0xFFFF);
        setBC((getBC() - 1) & 0xFFFF);

        flagS = (result & 0x80) != 0;
        flagZ = result == 0;
        flagH = (regA & 0x0F) < (value & 0x0F);
        flagPV = getBC() != 0;
        flagN = true;
        return 16;
    }

    private int executeCPIR() {
        executeCPI();
        if (getBC() != 0 && !flagZ) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    private int executeCPDR() {
        executeCPD();
        if (getBC() != 0 && !flagZ) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    // Блочные I/O операции

    private int executeINI() {
        int value = ioRead(regC);
        memory.writeByte(getHL(), value);
        setHL((getHL() + 1) & 0xFFFF);
        regB = (regB - 1) & 0xFF;

        flagZ = regB == 0;
        flagN = true;
        return 16;
    }

    private int executeIND() {
        int value = ioRead(regC);
        memory.writeByte(getHL(), value);
        setHL((getHL() - 1) & 0xFFFF);
        regB = (regB - 1) & 0xFF;

        flagZ = regB == 0;
        flagN = true;
        return 16;
    }

    private int executeINIR() {
        executeINI();
        if (regB != 0) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    private int executeINDR() {
        executeIND();
        if (regB != 0) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    private int executeOUTI() {
        int value = memory.readByte(getHL());
        ioWrite(regC, value);
        setHL((getHL() + 1) & 0xFFFF);
        regB = (regB - 1) & 0xFF;

        flagZ = regB == 0;
        flagN = true;
        return 16;
    }

    private int executeOUTD() {
        int value = memory.readByte(getHL());
        ioWrite(regC, value);
        setHL((getHL() - 1) & 0xFFFF);
        regB = (regB - 1) & 0xFF;

        flagZ = regB == 0;
        flagN = true;
        return 16;
    }

    private int executeOTIR() {
        executeOUTI();
        if (regB != 0) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    private int executeOTDR() {
        executeOUTD();
        if (regB != 0) {
            regPC = (regPC - 2) & 0xFFFF; // Repeat instruction
            return 21;
        }
        return 16;
    }

    // Геттеры для отладки и мониторинга

    public int getRegA() {
        return regA;
    }

    public int getRegB() {
        return regB;
    }

    public int getRegC() {
        return regC;
    }

    public int getRegD() {
        return regD;
    }

    public int getRegE() {
        return regE;
    }

    public int getRegH() {
        return regH;
    }

    public int getRegL() {
        return regL;
    }

    public int getRegBC() {
        return getBC();
    }

    public int getRegDE() {
        return getDE();
    }

    public int getRegHL() {
        return getHL();
    }

    public int getRegIX() {
        return regIX;
    }

    public int getRegIY() {
        return regIY;
    }

    public int getRegSP() {
        return regSP;
    }

    public int getRegPC() {
        return regPC;
    }

    public int getRegI() {
        return regI;
    }

    public int getRegR() {
        return regR;
    }

    public boolean isFlagS() {
        return flagS;
    }

    public boolean isFlagZ() {
        return flagZ;
    }

    public boolean isFlagH() {
        return flagH;
    }

    public boolean isFlagPV() {
        return flagPV;
    }

    public boolean isFlagN() {
        return flagN;
    }

    public boolean isFlagC() {
        return flagC;
    }

    public boolean isHalted() {
        return halted;
    }

    public long getTotalCycles() {
        return totalCycles;
    }

    public boolean isIff1() {
        return iff1;
    }

    public boolean isIff2() {
        return iff2;
    }

    public int getInterruptMode() {
        return interruptMode;
    }
}