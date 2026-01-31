package spectrum.hardware.debug;


import lombok.Getter;
import spectrum.hardware.memory.Memory;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class Z80Disassembler {

    /**
     *
     */
    @Getter
    public static class DisassemblyResult {

        // Getters
        private final String mnemonic;
        private final int length;
        private final int[] bytes;
        private final String formattedOutput;
        private final String hexBytes;
        private final String addressedOutput;

        public DisassemblyResult(String mnemonic, int length, int[] bytes) {
            this.mnemonic = mnemonic;
            this.length = length;
            this.bytes = bytes;
            this.hexBytes = formatHexBytes();
            this.formattedOutput = formatOutput();
            this.addressedOutput = null; // Will be set by utility methods
        }

        public DisassemblyResult(String mnemonic, int length, int[] bytes, int address) {
            this.mnemonic = mnemonic;
            this.length = length;
            this.bytes = bytes;
            this.hexBytes = formatHexBytes();
            this.formattedOutput = formatOutput();
            this.addressedOutput = formatAddressedOutput(address);
        }

        private String formatHexBytes() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(String.format("%02X", bytes[i]));
            }
            return sb.toString();
        }

        private String formatOutput() {
            // Format bytes (fixed width for alignment)
            return String.format("%-12s", hexBytes) + mnemonic;
        }

        private String formatAddressedOutput(int address) {
            return String.format("%04X: %-12s %s", address, hexBytes, mnemonic);
        }

        /**
         * Creates a result with an address specified for GUI
         */
        public DisassemblyResult withAddress(int address) {
            return new DisassemblyResult(mnemonic, length, bytes, address);
        }

        /**
         * Returns table representation for GUI
         */
        public String[] getTableRow(int address) {
            return new String[]{
                    String.format("%04X", address),  // Address
                    hexBytes,                        // Bytes
                    mnemonic                         // Instruction
            };
        }
    }

    // Register name arrays for quick access
    private static final String[] REG8_NAMES = {"B", "C", "D", "E", "H", "L", "(HL)", "A"};
    private static final String[] REG8_IX_NAMES = {"B", "C", "D", "E", "IXH", "IXL", "(HL)", "A"};
    private static final String[] REG8_IY_NAMES = {"B", "C", "D", "E", "IYH", "IYL", "(HL)", "A"};
    private static final String[] REG16_NAMES = {"BC", "DE", "HL", "SP"};
    private static final String[] REG16_PUSH_NAMES = {"BC", "DE", "HL", "AF"};
    private static final String[] CONDITION_NAMES = {"NZ", "Z", "NC", "C", "PO", "PE", "P", "M"};

    /**
     * Disassembles instruction at the specified address
     *
     * @param memory   memory object to read the instruction from
     * @param position instruction address
     * @return disassembly result
     */
    public DisassemblyResult disassemble(Memory memory, int position) {

        // Read first byte
        int opcode = memory.readByte(position) & 0xFF;

        // Check prefixes
        return switch (opcode) {
            case 0xCB -> disassembleCB(memory, position);
            case 0xDD -> disassembleDD(memory, position);
            case 0xED -> disassembleED(memory, position);
            case 0xFD -> disassembleFD(memory, position);
            default -> disassembleMain(memory, position, opcode);
        };
    }

    /**
     * Disassembles main instructions (no prefix)
     */
    private DisassemblyResult disassembleMain(Memory memory, int position, int opcode) {
        int originalPosition = position;
        position++; // Skip opcode

        return switch (opcode) {
            case 0x00 -> new DisassemblyResult("NOP", 1, new int[]{opcode});

            // LD rr,nn instructions
            case 0x01 -> formatLD16Immediate(memory, originalPosition, "BC");
            case 0x11 -> formatLD16Immediate(memory, originalPosition, "DE");
            case 0x21 -> formatLD16Immediate(memory, originalPosition, "HL");
            case 0x31 -> formatLD16Immediate(memory, originalPosition, "SP");

            // LD (rr),A instructions
            case 0x02 -> new DisassemblyResult("LD (BC),A", 1, new int[]{opcode});
            case 0x12 -> new DisassemblyResult("LD (DE),A", 1, new int[]{opcode});

            // LD A,(rr) instructions
            case 0x0A -> new DisassemblyResult("LD A,(BC)", 1, new int[]{opcode});
            case 0x1A -> new DisassemblyResult("LD A,(DE)", 1, new int[]{opcode});

            // INC/DEC rr instructions
            case 0x03 -> new DisassemblyResult("INC BC", 1, new int[]{opcode});
            case 0x13 -> new DisassemblyResult("INC DE", 1, new int[]{opcode});
            case 0x23 -> new DisassemblyResult("INC HL", 1, new int[]{opcode});
            case 0x33 -> new DisassemblyResult("INC SP", 1, new int[]{opcode});
            case 0x0B -> new DisassemblyResult("DEC BC", 1, new int[]{opcode});
            case 0x1B -> new DisassemblyResult("DEC DE", 1, new int[]{opcode});
            case 0x2B -> new DisassemblyResult("DEC HL", 1, new int[]{opcode});
            case 0x3B -> new DisassemblyResult("DEC SP", 1, new int[]{opcode});

            // INC/DEC r instructions
            case 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C -> {
                int reg = (opcode >> 3) & 0x07;
                yield new DisassemblyResult("INC " + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D -> {
                int reg = (opcode >> 3) & 0x07;
                yield new DisassemblyResult("DEC " + REG8_NAMES[reg], 1, new int[]{opcode});
            }

            // LD r,n instructions
            case 0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x36, 0x3E -> {
                int reg = (opcode >> 3) & 0x07;
                int immediate = memory.readByte(position) & 0xFF;
                yield new DisassemblyResult(String.format("LD %s,0x%02X", REG8_NAMES[reg], immediate), 2, new int[]{opcode, immediate});
            }

            // Rotate instructions
            case 0x07 -> new DisassemblyResult("RLCA", 1, new int[]{opcode});
            case 0x0F -> new DisassemblyResult("RRCA", 1, new int[]{opcode});
            case 0x17 -> new DisassemblyResult("RLA", 1, new int[]{opcode});
            case 0x1F -> new DisassemblyResult("RRA", 1, new int[]{opcode});

            // Exchange instructions
            case 0x08 -> new DisassemblyResult("EX AF,AF'", 1, new int[]{opcode});
            case 0xD9 -> new DisassemblyResult("EXX", 1, new int[]{opcode});
            case 0xEB -> new DisassemblyResult("EX DE,HL", 1, new int[]{opcode});

            // ADD HL,rr instructions
            case 0x09, 0x19, 0x29, 0x39 -> {
                int reg = (opcode >> 4) & 0x03;
                yield new DisassemblyResult("ADD HL," + REG16_NAMES[reg], 1, new int[]{opcode});
            }

            // Relative jump instructions
            case 0x10 -> formatRelativeJump(memory, originalPosition, "DJNZ");
            case 0x18 -> formatRelativeJump(memory, originalPosition, "JR");
            case 0x20, 0x28, 0x30, 0x38 -> {
                int condition = (opcode >> 3) & 0x03;
                yield formatRelativeJump(memory, originalPosition, "JR " + CONDITION_NAMES[condition]);
            }

            // Extended memory operations
            case 0x22 -> formatLD16Extended(memory, originalPosition, "LD (0x%04X),HL");
            case 0x2A -> formatLD16Extended(memory, originalPosition, "LD HL,(0x%04X)");
            case 0x32 -> formatLD16Extended(memory, originalPosition, "LD (0x%04X),A");
            case 0x3A -> formatLD16Extended(memory, originalPosition, "LD A,(0x%04X)");

            // Misc instructions
            case 0x27 -> new DisassemblyResult("DAA", 1, new int[]{opcode});
            case 0x2F -> new DisassemblyResult("CPL", 1, new int[]{opcode});
            case 0x37 -> new DisassemblyResult("SCF", 1, new int[]{opcode});
            case 0x3F -> new DisassemblyResult("CCF", 1, new int[]{opcode});

            // LD r,r instructions
            case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
                 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F,
                 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
                 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F,
                 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,
                 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,
                 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x77,
                 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F -> {
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                yield new DisassemblyResult(String.format("LD %s,%s", REG8_NAMES[destReg], REG8_NAMES[srcReg]), 1, new int[]{opcode});
            }

            case 0x76 -> new DisassemblyResult("HALT", 1, new int[]{opcode});

            // Arithmetic instructions with registers
            case 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87 ->
                    new DisassemblyResult("ADD A," + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F ->
                    new DisassemblyResult("ADC A," + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97 ->
                    new DisassemblyResult("SUB " + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F ->
                    new DisassemblyResult("SBC A," + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7 ->
                    new DisassemblyResult("AND " + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0xA8, 0xA9, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF ->
                    new DisassemblyResult("XOR " + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7 ->
                    new DisassemblyResult("OR " + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});
            case 0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF ->
                    new DisassemblyResult("CP " + REG8_NAMES[opcode & 0x07], 1, new int[]{opcode});

            // Arithmetic instructions with immediate
            case 0xC6 -> formatArithmeticImmediate(memory, originalPosition, "ADD A");
            case 0xCE -> formatArithmeticImmediate(memory, originalPosition, "ADC A");
            case 0xD6 -> formatArithmeticImmediate(memory, originalPosition, "SUB");
            case 0xDE -> formatArithmeticImmediate(memory, originalPosition, "SBC A");
            case 0xE6 -> formatArithmeticImmediate(memory, originalPosition, "AND");
            case 0xEE -> formatArithmeticImmediate(memory, originalPosition, "XOR");
            case 0xF6 -> formatArithmeticImmediate(memory, originalPosition, "OR");
            case 0xFE -> formatArithmeticImmediate(memory, originalPosition, "CP");

            // Conditional returns
            case 0xC0, 0xC8, 0xD0, 0xD8, 0xE0, 0xE8, 0xF0, 0xF8 -> {
                int condition = (opcode >> 3) & 0x07;
                yield new DisassemblyResult("RET " + CONDITION_NAMES[condition], 1, new int[]{opcode});
            }

            // POP instructions
            case 0xC1, 0xD1, 0xE1, 0xF1 -> {
                int reg = (opcode >> 4) & 0x03;
                yield new DisassemblyResult("POP " + REG16_PUSH_NAMES[reg], 1, new int[]{opcode});
            }

            // Conditional jumps
            case 0xC2, 0xCA, 0xD2, 0xDA, 0xE2, 0xEA, 0xF2, 0xFA -> {
                int condition = (opcode >> 3) & 0x07;
                yield formatAbsoluteJump(memory, originalPosition, "JP " + CONDITION_NAMES[condition]);
            }

            case 0xC3 -> formatAbsoluteJump(memory, originalPosition, "JP");

            // Conditional calls
            case 0xC4, 0xCC, 0xD4, 0xDC, 0xE4, 0xEC, 0xF4, 0xFC -> {
                int condition = (opcode >> 3) & 0x07;
                yield formatAbsoluteJump(memory, originalPosition, "CALL " + CONDITION_NAMES[condition]);
            }

            // PUSH instructions
            case 0xC5, 0xD5, 0xE5, 0xF5 -> {
                int reg = (opcode >> 4) & 0x03;
                yield new DisassemblyResult("PUSH " + REG16_PUSH_NAMES[reg], 1, new int[]{opcode});
            }

            case 0xD3 -> {
                int n = memory.readByte(originalPosition + 1) & 0xFF;
                yield new DisassemblyResult(String.format("OUT (0x%02X),A", n), 2, new int[]{opcode, n});
            }
            case 0xDB -> {
                int port = memory.readByte(originalPosition + 1) & 0xFF;
                yield new DisassemblyResult(String.format("IN A,(0x%02X)", port), 2, new int[]{opcode, port});
            }

            case 0xC9 -> new DisassemblyResult("RET", 1, new int[]{opcode});
            case 0xCD -> formatAbsoluteJump(memory, originalPosition, "CALL");

            // RST instructions
            case 0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF -> {
                int addr = opcode & 0x38;
                yield new DisassemblyResult(String.format("RST 0x%02X", addr), 1, new int[]{opcode});
            }

            // Other single-byte instructions
            case 0xE3 -> new DisassemblyResult("EX (SP),HL", 1, new int[]{opcode});
            case 0xE9 -> new DisassemblyResult("JP (HL)", 1, new int[]{opcode});
            case 0xF3 -> new DisassemblyResult("DI", 1, new int[]{opcode});
            case 0xF9 -> new DisassemblyResult("LD SP,HL", 1, new int[]{opcode});
            case 0xFB -> new DisassemblyResult("EI", 1, new int[]{opcode});

            default -> new DisassemblyResult(String.format("DB 0x%02X", opcode), 1, new int[]{opcode});
        };
    }

    /**
     * Disassembles CB-prefixed instructions (bit operations)
     */
    private DisassemblyResult disassembleCB(Memory memory, int position) {
        int cbPrefix = memory.readByte(position) & 0xFF;     // 0xCB
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual CB opcode

        int regIndex = opcode & 0x07;  // Register index (0-7: B,C,D,E,H,L,(HL),A)
        int operation = (opcode >> 3) & 0x07; // Operation (0-7)
        int group = (opcode >> 6) & 0x03;     // Operation group (0-3)

        String regName = REG8_NAMES[regIndex];
        String mnemonic = switch (group) {
            case 0 -> formatCBRotateShift(operation, regName); // Rotations and shifts (0x00-0x3F)
            case 1 -> String.format("BIT %d,%s", operation, regName); // BIT operations (0x40-0x7F)
            case 2 -> String.format("RES %d,%s", operation, regName); // RES operations (0x80-0xBF)
            case 3 -> String.format("SET %d,%s", operation, regName); // SET operations (0xC0-0xFF)
            default -> String.format("CB 0x%02X", opcode);
        };

        return new DisassemblyResult(mnemonic, 2, new int[]{cbPrefix, opcode});
    }

    /**
     * Formats rotate and shift operations for CB instructions
     */
    private String formatCBRotateShift(int operation, String regName) {
        return switch (operation) {
            case 0 -> "RLC " + regName;
            case 1 -> "RRC " + regName;
            case 2 -> "RL " + regName;
            case 3 -> "RR " + regName;
            case 4 -> "SLA " + regName;
            case 5 -> "SRA " + regName;
            case 6 -> "SLL " + regName;  // Undocumented
            case 7 -> "SRL " + regName;
            default -> "??? " + regName;
        };
    }

    /**
     * Disassembles DD-prefixed instructions (IX operations)
     */
    private DisassemblyResult disassembleDD(Memory memory, int position) {
        int ddPrefix = memory.readByte(position) & 0xFF;     // 0xDD
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual DD opcode

        // Check for DDCB prefix
        if (opcode == 0xCB) {
            return disassembleDDCB(memory, position);
        }

        return switch (opcode) {
            // LD IX,nn
            case 0x21 -> {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                yield new DisassemblyResult(String.format("LD IX,0x%04X", (high << 8) | low), 4, new int[]{ddPrefix, opcode, low, high});
            }

            // LD (nn),IX
            case 0x22 -> {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                yield new DisassemblyResult(String.format("LD (0x%04X),IX", (high << 8) | low), 4, new int[]{ddPrefix, opcode, low, high});
            }

            case 0x23 -> new DisassemblyResult("INC IX", 2, new int[]{ddPrefix, opcode});
            case 0x2B -> new DisassemblyResult("DEC IX", 2, new int[]{ddPrefix, opcode});

            // LD IX,(nn)
            case 0x2A -> {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                yield new DisassemblyResult(String.format("LD IX,(0x%04X)", (high << 8) | low), 4, new int[]{ddPrefix, opcode, low, high});
            }

            // INC/DEC (IX+d)
            case 0x34 -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("INC (IX%+d)", signedDisp), 3, new int[]{ddPrefix, opcode, displacement});
            }
            case 0x35 -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("DEC (IX%+d)", signedDisp), 3, new int[]{ddPrefix, opcode, displacement});
            }

            // LD (IX+d),n
            case 0x36 -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int immediate = memory.readByte(position + 3) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("LD (IX%+d),0x%02X", signedDisp, immediate), 4, new int[]{ddPrefix, opcode, displacement, immediate});
            }

            // ADD IX,rr
            case 0x09, 0x19, 0x29, 0x39 -> {
                String[] regs = {"BC", "DE", "IX", "SP"};
                yield new DisassemblyResult("ADD IX," + regs[(opcode >> 4) & 0x03], 2, new int[]{ddPrefix, opcode});
            }

            // INC/DEC IXH/IXL (undocumented)
            case 0x24 -> new DisassemblyResult("INC IXH", 2, new int[]{ddPrefix, opcode});
            case 0x25 -> new DisassemblyResult("DEC IXH", 2, new int[]{ddPrefix, opcode});
            case 0x26 -> {
                int n = memory.readByte(position + 2) & 0xFF;
                yield new DisassemblyResult(String.format("LD IXH,0x%02X", n), 3, new int[]{ddPrefix, opcode, n});
            }
            case 0x2C -> new DisassemblyResult("INC IXL", 2, new int[]{ddPrefix, opcode});
            case 0x2D -> new DisassemblyResult("DEC IXL", 2, new int[]{ddPrefix, opcode});
            case 0x2E -> {
                int n = memory.readByte(position + 2) & 0xFF;
                yield new DisassemblyResult(String.format("LD IXL,0x%02X", n), 3, new int[]{ddPrefix, opcode, n});
            }

            // LD instructions with (IX+d)
            case 0x46, 0x4E, 0x56, 0x5E, 0x66, 0x6E, 0x7E -> {
                int reg = (opcode >> 3) & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("LD %s,(IX%+d)", REG8_NAMES[reg], signedDisp), 3, new int[]{ddPrefix, opcode, displacement});
            }
            case 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x77 -> {
                int reg = opcode & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("LD (IX%+d),%s", signedDisp, REG8_NAMES[reg]), 3, new int[]{ddPrefix, opcode, displacement});
            }

            // LD r,r with IXH/IXL (undocumented)
            case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x47,
                 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4F,
                 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x57,
                 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5F,
                 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x67,
                 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6F,
                 0x7C, 0x7D -> {
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                yield new DisassemblyResult(String.format("LD %s,%s", REG8_IX_NAMES[destReg], REG8_IX_NAMES[srcReg]), 2, new int[]{ddPrefix, opcode});
            }

            // ALU instructions with (IX+d)
            case 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("%s (IX%+d)", getArithmeticOperation(opcode), signedDisp), 3, new int[]{ddPrefix, opcode, displacement});
            }

            // ALU instructions with IXH/IXL (undocumented)
            case 0x84, 0x85, 0x8C, 0x8D, 0x94, 0x95, 0x9C, 0x9D,
                 0xA4, 0xA5, 0xAC, 0xAD, 0xB4, 0xB5, 0xBC, 0xBD -> {
                int reg = opcode & 0x07;
                yield new DisassemblyResult(String.format("%s %s", getArithmeticOperation(opcode), REG8_IX_NAMES[reg]), 2, new int[]{ddPrefix, opcode});
            }

            case 0xE1 -> new DisassemblyResult("POP IX", 2, new int[]{ddPrefix, opcode});
            case 0xE3 -> new DisassemblyResult("EX (SP),IX", 2, new int[]{ddPrefix, opcode});
            case 0xE5 -> new DisassemblyResult("PUSH IX", 2, new int[]{ddPrefix, opcode});
            case 0xE9 -> new DisassemblyResult("JP (IX)", 2, new int[]{ddPrefix, opcode});
            case 0xF9 -> new DisassemblyResult("LD SP,IX", 2, new int[]{ddPrefix, opcode});

            default ->
                    new DisassemblyResult(String.format("DB 0x%02X, 0x%02X", ddPrefix, opcode), 2, new int[]{ddPrefix, opcode});
        };
    }

    /**
     * Disassembles FD-prefixed instructions (IY operations)
     */
    private DisassemblyResult disassembleFD(Memory memory, int position) {
        int fdPrefix = memory.readByte(position) & 0xFF;     // 0xFD
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual FD opcode

        // Check for FDCB prefix
        if (opcode == 0xCB) {
            return disassembleFDCB(memory, position);
        }

        return switch (opcode) {
            // LD IY,nn
            case 0x21 -> {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                yield new DisassemblyResult(String.format("LD IY,0x%04X", (high << 8) | low), 4, new int[]{fdPrefix, opcode, low, high});
            }

            // LD (nn),IY
            case 0x22 -> {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                yield new DisassemblyResult(String.format("LD (0x%04X),IY", (high << 8) | low), 4, new int[]{fdPrefix, opcode, low, high});
            }

            case 0x23 -> new DisassemblyResult("INC IY", 2, new int[]{fdPrefix, opcode});
            case 0x2B -> new DisassemblyResult("DEC IY", 2, new int[]{fdPrefix, opcode});

            // LD IY,(nn)
            case 0x2A -> {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                yield new DisassemblyResult(String.format("LD IY,(0x%04X)", (high << 8) | low), 4, new int[]{fdPrefix, opcode, low, high});
            }

            // INC/DEC (IY+d)
            case 0x34 -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("INC (IY%+d)", signedDisp), 3, new int[]{fdPrefix, opcode, displacement});
            }
            case 0x35 -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("DEC (IY%+d)", signedDisp), 3, new int[]{fdPrefix, opcode, displacement});
            }

            // LD (IY+d),n
            case 0x36 -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int immediate = memory.readByte(position + 3) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("LD (IY%+d),0x%02X", signedDisp, immediate), 4, new int[]{fdPrefix, opcode, displacement, immediate});
            }

            // ADD IY,rr
            case 0x09, 0x19, 0x29, 0x39 -> {
                String[] regs = {"BC", "DE", "IY", "SP"};
                yield new DisassemblyResult("ADD IY," + regs[(opcode >> 4) & 0x03], 2, new int[]{fdPrefix, opcode});
            }

            // INC/DEC IYH/IYL (undocumented)
            case 0x24 -> new DisassemblyResult("INC IYH", 2, new int[]{fdPrefix, opcode});
            case 0x25 -> new DisassemblyResult("DEC IYH", 2, new int[]{fdPrefix, opcode});
            case 0x26 -> {
                int n = memory.readByte(position + 2) & 0xFF;
                yield new DisassemblyResult(String.format("LD IYH,0x%02X", n), 3, new int[]{fdPrefix, opcode, n});
            }
            case 0x2C -> new DisassemblyResult("INC IYL", 2, new int[]{fdPrefix, opcode});
            case 0x2D -> new DisassemblyResult("DEC IYL", 2, new int[]{fdPrefix, opcode});
            case 0x2E -> {
                int n = memory.readByte(position + 2) & 0xFF;
                yield new DisassemblyResult(String.format("LD IYL,0x%02X", n), 3, new int[]{fdPrefix, opcode, n});
            }

            // LD instructions with (IY+d)
            case 0x46, 0x4E, 0x56, 0x5E, 0x66, 0x6E, 0x7E -> {
                int reg = (opcode >> 3) & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("LD %s,(IY%+d)", REG8_NAMES[reg], signedDisp), 3, new int[]{fdPrefix, opcode, displacement});
            }
            case 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x77 -> {
                int reg = opcode & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("LD (IY%+d),%s", signedDisp, REG8_NAMES[reg]), 3, new int[]{fdPrefix, opcode, displacement});
            }

            // LD r,r with IYH/IYL (undocumented)
            case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x47,
                 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4F,
                 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x57,
                 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5F,
                 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x67,
                 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6F,
                 0x7C, 0x7D -> {
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                yield new DisassemblyResult(String.format("LD %s,%s", REG8_IY_NAMES[destReg], REG8_IY_NAMES[srcReg]), 2, new int[]{fdPrefix, opcode});
            }

            // ALU instructions with (IY+d)
            case 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE -> {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                yield new DisassemblyResult(String.format("%s (IY%+d)", getArithmeticOperation(opcode), signedDisp), 3, new int[]{fdPrefix, opcode, displacement});
            }

            // ALU instructions with IYH/IYL (undocumented)
            case 0x84, 0x85, 0x8C, 0x8D, 0x94, 0x95, 0x9C, 0x9D,
                 0xA4, 0xA5, 0xAC, 0xAD, 0xB4, 0xB5, 0xBC, 0xBD -> {
                int reg = opcode & 0x07;
                yield new DisassemblyResult(String.format("%s %s", getArithmeticOperation(opcode), REG8_IY_NAMES[reg]), 2, new int[]{fdPrefix, opcode});
            }

            case 0xE1 -> new DisassemblyResult("POP IY", 2, new int[]{fdPrefix, opcode});
            case 0xE3 -> new DisassemblyResult("EX (SP),IY", 2, new int[]{fdPrefix, opcode});
            case 0xE5 -> new DisassemblyResult("PUSH IY", 2, new int[]{fdPrefix, opcode});
            case 0xE9 -> new DisassemblyResult("JP (IY)", 2, new int[]{fdPrefix, opcode});
            case 0xF9 -> new DisassemblyResult("LD SP,IY", 2, new int[]{fdPrefix, opcode});

            default ->
                    new DisassemblyResult(String.format("DB 0x%02X, 0x%02X", fdPrefix, opcode), 2, new int[]{fdPrefix, opcode});
        };
    }

    /**
     * Returns the arithmetic operation name for a given opcode
     */
    private String getArithmeticOperation(int opcode) {
        return switch (opcode & 0xF8) {
            case 0x80 -> "ADD A,";
            case 0x88 -> "ADC A,";
            case 0x90 -> "SUB";
            case 0x98 -> "SBC A,";
            case 0xA0 -> "AND";
            case 0xA8 -> "XOR";
            case 0xB0 -> "OR";
            case 0xB8 -> "CP";
            default -> "???";
        };
    }

    /**
     * Disassembles DDCB-prefixed instructions (indexed bit operations)
     */
    private DisassemblyResult disassembleDDCB(Memory memory, int position) {
        int ddPrefix = memory.readByte(position) & 0xFF;     // 0xDD
        int cbPrefix = memory.readByte(position + 1) & 0xFF; // 0xCB
        int displacement = memory.readByte(position + 2) & 0xFF;
        int opcode = memory.readByte(position + 3) & 0xFF;

        int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
        int regIndex = opcode & 0x07;
        int operation = (opcode >> 3) & 0x07;
        int group = (opcode >> 6) & 0x03;

        String mnemonic = switch (group) {
            case 0 -> { // Rotations and shifts
                String rotOp = formatCBRotateShift(operation, "");
                String result = rotOp.trim() + String.format(" (IX%+d)", signedDisp);
                yield (regIndex != 6) ? result + "," + REG8_NAMES[regIndex] : result;
            }
            case 1 -> String.format("BIT %d,(IX%+d)", operation, signedDisp); // BIT operations
            case 2 -> { // RES operations
                String result = String.format("RES %d,(IX%+d)", operation, signedDisp);
                yield (regIndex != 6) ? result + "," + REG8_NAMES[regIndex] : result;
            }
            case 3 -> { // SET operations
                String result = String.format("SET %d,(IX%+d)", operation, signedDisp);
                yield (regIndex != 6) ? result + "," + REG8_NAMES[regIndex] : result;
            }
            default -> String.format("DDCB %02X %02X", displacement, opcode);
        };

        return new DisassemblyResult(mnemonic, 4, new int[]{ddPrefix, cbPrefix, displacement, opcode});
    }

    /**
     * Disassembles FDCB-prefixed instructions (indexed bit operations)
     */
    private DisassemblyResult disassembleFDCB(Memory memory, int position) {
        int fdPrefix = memory.readByte(position) & 0xFF;     // 0xFD
        int cbPrefix = memory.readByte(position + 1) & 0xFF; // 0xCB
        int displacement = memory.readByte(position + 2) & 0xFF;
        int opcode = memory.readByte(position + 3) & 0xFF;

        int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
        int regIndex = opcode & 0x07;
        int operation = (opcode >> 3) & 0x07;
        int group = (opcode >> 6) & 0x03;

        String mnemonic = switch (group) {
            case 0 -> { // Rotations and shifts
                String rotOp = formatCBRotateShift(operation, "");
                String result = rotOp.trim() + String.format(" (IY%+d)", signedDisp);
                yield (regIndex != 6) ? result + "," + REG8_NAMES[regIndex] : result;
            }
            case 1 -> String.format("BIT %d,(IY%+d)", operation, signedDisp); // BIT operations
            case 2 -> { // RES operations
                String result = String.format("RES %d,(IY%+d)", operation, signedDisp);
                yield (regIndex != 6) ? result + "," + REG8_NAMES[regIndex] : result;
            }
            case 3 -> { // SET operations
                String result = String.format("SET %d,(IY%+d)", operation, signedDisp);
                yield (regIndex != 6) ? result + "," + REG8_NAMES[regIndex] : result;
            }
            default -> String.format("FDCB %02X %02X", displacement, opcode);
        };

        return new DisassemblyResult(mnemonic, 4, new int[]{fdPrefix, cbPrefix, displacement, opcode});
    }

    /**
     * Disassembles memory block and returns a list of results
     *
     * @param memory           memory object
     * @param startAddress     start address
     * @param instructionCount number of instructions to disassemble
     * @return list of disassembly results
     */
    public List<DisassemblyResult> disassembleBlock(Memory memory, int startAddress, int instructionCount) {
        List<DisassemblyResult> results = new ArrayList<>();
        int currentAddress = startAddress;

        for (int i = 0; i < instructionCount; i++) {
            try {
                DisassemblyResult result = disassemble(memory, currentAddress);
                results.add(result.withAddress(currentAddress));
                currentAddress = (currentAddress + result.getLength()) & 0xFFFF;
            } catch (Exception e) {
                // In case of error, create DB byte and move to next
                int value = memory.readByte(currentAddress) & 0xFF;
                DisassemblyResult result = new DisassemblyResult(
                        String.format("DB 0x%02X", value),
                        1, new int[]{value}, currentAddress
                );
                results.add(result);
                currentAddress = (currentAddress + 1) & 0xFFFF;
            }
        }

        return results;
    }

    /**
     * Disassembles memory in the specified address range
     *
     * @param memory       memory object
     * @param startAddress start address
     * @param endAddress   end address
     * @return list of disassembly results
     */
    public List<DisassemblyResult> disassembleRange(Memory memory, int startAddress, int endAddress) {
        List<DisassemblyResult> results = new ArrayList<>();
        int currentAddress = startAddress & 0xFFFF;
        endAddress &= 0xFFFF;

        while (currentAddress != endAddress) {
            try {
                DisassemblyResult result = disassemble(memory, currentAddress);
                results.add(result.withAddress(currentAddress));
                currentAddress = (currentAddress + result.getLength()) & 0xFFFF;

                // Infinite loop protection
                if (currentAddress < startAddress && endAddress > startAddress) {
                    break; // Wrapped around 0xFFFF
                }
            } catch (Exception e) {
                // In case of error, create DB byte and move to next
                int value = memory.readByte(currentAddress) & 0xFF;
                DisassemblyResult result = new DisassemblyResult(
                        String.format("DB 0x%02X", value),
                        1, new int[]{value}, currentAddress
                );
                results.add(result);
                currentAddress = (currentAddress + 1) & 0xFFFF;
            }
        }

        return results;
    }

    /**
     * Creates a text representation of the disassembly for console or file output
     *
     * @param results list of disassembly results
     * @return text representation
     */
    public String formatDisassemblyText(List<DisassemblyResult> results) {
        StringBuilder sb = new StringBuilder();
        for (DisassemblyResult result : results) {
            sb.append(result.getAddressedOutput()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Creates a table representation for GUI tables
     *
     * @param results list of disassembly results
     * @return 2D array of strings for the table
     */
    public String[][] formatDisassemblyTable(List<DisassemblyResult> results) {
        String[][] table = new String[results.size()][];
        for (int i = 0; i < results.size(); i++) {
            DisassemblyResult result = results.get(i);
            // Extract address from addressedOutput or use index
            int address = extractAddressFromResult(result, i);
            table[i] = result.getTableRow(address);
        }
        return table;
    }

    /**
     * Extracts address from the disassembly result
     */
    private int extractAddressFromResult(DisassemblyResult result, int fallbackIndex) {
        if (result.getAddressedOutput() != null) {
            try {
                String addressStr = result.getAddressedOutput().substring(0, 4);
                return Integer.parseInt(addressStr, 16);
            } catch (Exception e) {
                return fallbackIndex;
            }
        }
        return fallbackIndex;
    }

    /**
     * Returns column headers for the table representation
     */
    public String[] getTableHeaders() {
        return new String[]{"Address", "Bytes", "Instruction"};
    }

    /**
     * Disassembles ED-prefixed instructions (extended instruction set)
     */
    private DisassemblyResult disassembleED(Memory memory, int position) {
        int edPrefix = memory.readByte(position) & 0xFF;     // 0xED
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual ED opcode

        return switch (opcode) {
            // Block load operations
            case 0xA0 -> new DisassemblyResult("LDI", 2, new int[]{edPrefix, opcode});
            case 0xA8 -> new DisassemblyResult("LDD", 2, new int[]{edPrefix, opcode});
            case 0xB0 -> new DisassemblyResult("LDIR", 2, new int[]{edPrefix, opcode});
            case 0xB8 -> new DisassemblyResult("LDDR", 2, new int[]{edPrefix, opcode});

            // Block compare operations
            case 0xA1 -> new DisassemblyResult("CPI", 2, new int[]{edPrefix, opcode});
            case 0xA9 -> new DisassemblyResult("CPD", 2, new int[]{edPrefix, opcode});
            case 0xB1 -> new DisassemblyResult("CPIR", 2, new int[]{edPrefix, opcode});
            case 0xB9 -> new DisassemblyResult("CPDR", 2, new int[]{edPrefix, opcode});

            // Block I/O operations - input
            case 0xA2 -> new DisassemblyResult("INI", 2, new int[]{edPrefix, opcode});
            case 0xAA -> new DisassemblyResult("IND", 2, new int[]{edPrefix, opcode});
            case 0xB2 -> new DisassemblyResult("INIR", 2, new int[]{edPrefix, opcode});
            case 0xBA -> new DisassemblyResult("INDR", 2, new int[]{edPrefix, opcode});

            // Block I/O operations - output
            case 0xA3 -> new DisassemblyResult("OUTI", 2, new int[]{edPrefix, opcode});
            case 0xAB -> new DisassemblyResult("OUTD", 2, new int[]{edPrefix, opcode});
            case 0xB3 -> new DisassemblyResult("OTIR", 2, new int[]{edPrefix, opcode});
            case 0xBB -> new DisassemblyResult("OTDR", 2, new int[]{edPrefix, opcode});

            // 16-bit arithmetic
            case 0x42 -> new DisassemblyResult("SBC HL,BC", 2, new int[]{edPrefix, opcode});
            case 0x52 -> new DisassemblyResult("SBC HL,DE", 2, new int[]{edPrefix, opcode});
            case 0x62 -> new DisassemblyResult("SBC HL,HL", 2, new int[]{edPrefix, opcode});
            case 0x72 -> new DisassemblyResult("SBC HL,SP", 2, new int[]{edPrefix, opcode});
            case 0x4A -> new DisassemblyResult("ADC HL,BC", 2, new int[]{edPrefix, opcode});
            case 0x5A -> new DisassemblyResult("ADC HL,DE", 2, new int[]{edPrefix, opcode});
            case 0x6A -> new DisassemblyResult("ADC HL,HL", 2, new int[]{edPrefix, opcode});
            case 0x7A -> new DisassemblyResult("ADC HL,SP", 2, new int[]{edPrefix, opcode});

            // Interrupt modes
            case 0x46, 0x4E, 0x66, 0x6E -> new DisassemblyResult("IM 0", 2, new int[]{edPrefix, opcode});
            case 0x56, 0x76 -> new DisassemblyResult("IM 1", 2, new int[]{edPrefix, opcode});
            case 0x5E, 0x7E -> new DisassemblyResult("IM 2", 2, new int[]{edPrefix, opcode});

            // Special registers
            case 0x47 -> new DisassemblyResult("LD I,A", 2, new int[]{edPrefix, opcode});
            case 0x57 -> new DisassemblyResult("LD A,I", 2, new int[]{edPrefix, opcode});
            case 0x4F -> new DisassemblyResult("LD R,A", 2, new int[]{edPrefix, opcode});
            case 0x5F -> new DisassemblyResult("LD A,R", 2, new int[]{edPrefix, opcode});

            // Return from interrupt and negate
            case 0x44, 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C ->
                    new DisassemblyResult("NEG", 2, new int[]{edPrefix, opcode});
            case 0x45, 0x55, 0x65, 0x75 -> new DisassemblyResult("RETN", 2, new int[]{edPrefix, opcode});
            case 0x4D, 0x5D, 0x6D, 0x7D -> new DisassemblyResult("RETI", 2, new int[]{edPrefix, opcode});

            // Decimal rotate operations
            case 0x67 -> new DisassemblyResult("RRD", 2, new int[]{edPrefix, opcode});
            case 0x6F -> new DisassemblyResult("RLD", 2, new int[]{edPrefix, opcode});

            // I/O operations - IN r,(C)
            case 0x40, 0x48, 0x50, 0x58, 0x60, 0x68, 0x78 -> {
                int reg = (opcode >> 3) & 0x07;
                String m = (reg == 6) ? "IN (C)" : "IN " + REG8_NAMES[reg] + ",(C)";
                yield new DisassemblyResult(m, 2, new int[]{edPrefix, opcode});
            }
            case 0x70 -> new DisassemblyResult("IN (C)", 2, new int[]{edPrefix, opcode}); // Undocumented

            // I/O operations - OUT (C),r
            case 0x41, 0x49, 0x51, 0x59, 0x61, 0x69, 0x79 -> {
                int reg = (opcode >> 3) & 0x07;
                yield new DisassemblyResult("OUT (C)," + REG8_NAMES[reg], 2, new int[]{edPrefix, opcode});
            }
            case 0x71 -> new DisassemblyResult("OUT (C),0", 2, new int[]{edPrefix, opcode}); // Undocumented

            // 16-bit loads - LD (nn),rr and LD rr,(nn)
            case 0x43 -> formatED16BitLoad(memory, position, "LD (0x%04X),BC");
            case 0x53 -> formatED16BitLoad(memory, position, "LD (0x%04X),DE");
            case 0x63 -> formatED16BitLoad(memory, position, "LD (0x%04X),HL");
            case 0x73 -> formatED16BitLoad(memory, position, "LD (0x%04X),SP");
            case 0x4B -> formatED16BitLoad(memory, position, "LD BC,(0x%04X)");
            case 0x5B -> formatED16BitLoad(memory, position, "LD DE,(0x%04X)");
            case 0x6B -> formatED16BitLoad(memory, position, "LD HL,(0x%04X)");
            case 0x7B -> formatED16BitLoad(memory, position, "LD SP,(0x%04X)");

            default -> new DisassemblyResult(String.format("ED 0x%02X", opcode), 2, new int[]{edPrefix, opcode});
        };
    }

    /**
     * Helper for ED-prefixed 16-bit load instructions
     */
    private DisassemblyResult formatED16BitLoad(Memory memory, int position, String format) {
        int edPrefix = memory.readByte(position) & 0xFF;
        int opcode = memory.readByte(position + 1) & 0xFF;
        int low = memory.readByte(position + 2) & 0xFF;
        int high = memory.readByte(position + 3) & 0xFF;
        int address = (high << 8) | low;

        String mnemonic = String.format(format, address);
        return new DisassemblyResult(mnemonic, 4, new int[]{edPrefix, opcode, low, high});
    }


    // Helper methods for formatting different instruction types

    /**
     * Helper for 16-bit immediate load instructions
     */
    private DisassemblyResult formatLD16Immediate(Memory memory, int position, String reg) {
        int opcode = memory.readByte(position) & 0xFF;
        int low = memory.readByte(position + 1) & 0xFF;
        int high = memory.readByte(position + 2) & 0xFF;
        int address = (high << 8) | low;

        return new DisassemblyResult(
                String.format("LD %s,0x%04X", reg, address),
                3, new int[]{opcode, low, high}
        );
    }

    /**
     * Helper for 16-bit extended load instructions
     */
    private DisassemblyResult formatLD16Extended(Memory memory, int position, String format) {
        int opcode = memory.readByte(position) & 0xFF;
        int low = memory.readByte(position + 1) & 0xFF;
        int high = memory.readByte(position + 2) & 0xFF;
        int address = (high << 8) | low;

        return new DisassemblyResult(
                String.format(format, address),
                3, new int[]{opcode, low, high}
        );
    }

    /**
     * Helper for relative jump instructions
     */
    private DisassemblyResult formatRelativeJump(Memory memory, int position, String instruction) {
        int opcode = memory.readByte(position) & 0xFF;
        int offset = memory.readByte(position + 1) & 0xFF;

        // Convert to signed byte
        int signedOffset = (offset > 127) ? offset - 256 : offset;
        int target = (position + 2 + signedOffset) & 0xFFFF;

        return new DisassemblyResult(
                String.format("%s 0x%04X", instruction, target),
                2, new int[]{opcode, offset}
        );
    }

    /**
     * Helper for absolute jump instructions
     */
    private DisassemblyResult formatAbsoluteJump(Memory memory, int position, String instruction) {
        int opcode = memory.readByte(position) & 0xFF;
        int low = memory.readByte(position + 1) & 0xFF;
        int high = memory.readByte(position + 2) & 0xFF;
        int address = (high << 8) | low;

        return new DisassemblyResult(
                String.format("%s 0x%04X", instruction, address),
                3, new int[]{opcode, low, high}
        );
    }

    /**
     * Helper for arithmetic instructions with immediate operand
     */
    private DisassemblyResult formatArithmeticImmediate(Memory memory, int position, String operation) {
        int opcode = memory.readByte(position) & 0xFF;
        int immediate = memory.readByte(position + 1) & 0xFF;

        return new DisassemblyResult(
                String.format("%s,0x%02X", operation, immediate),
                2, new int[]{opcode, immediate}
        );
    }
}