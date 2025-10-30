package spectrum.jfx.z80.debug;


import spectrum.jfx.z80.memory.Memory;

import java.util.ArrayList;
import java.util.List;

/**
 * Дизассемблер для процессора Z80
 * Поддерживает все инструкции Z80, включая недокументированные
 */
public class Z80Disassembler {

    /**
     * Результат дизассемблирования инструкции
     */
    public static class DisassemblyResult {
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
            StringBuilder sb = new StringBuilder();

            // Форматируем байты (фиксированная ширина для выравнивания)
            sb.append(String.format("%-12s", hexBytes));

            // Добавляем мнемонику
            sb.append(mnemonic);

            return sb.toString();
        }

        private String formatAddressedOutput(int address) {
            return String.format("%04X: %-12s %s", address, hexBytes, mnemonic);
        }

        // Getters
        public String getMnemonic() {
            return mnemonic;
        }

        public int getLength() {
            return length;
        }

        public int[] getBytes() {
            return bytes;
        }

        public String getFormattedOutput() {
            return formattedOutput;
        }

        public String getHexBytes() {
            return hexBytes;
        }

        public String getAddressedOutput() {
            return addressedOutput;
        }

        /**
         * Создает результат с указанием адреса для GUI
         */
        public DisassemblyResult withAddress(int address) {
            return new DisassemblyResult(mnemonic, length, bytes, address);
        }

        /**
         * Возвращает табличное представление для GUI
         */
        public String[] getTableRow(int address) {
            return new String[]{
                    String.format("%04X", address),  // Address
                    hexBytes,                        // Bytes
                    mnemonic                         // Instruction
            };
        }
    }

    // Массивы имен регистров для быстрого доступа
    private static final String[] REG8_NAMES = {"B", "C", "D", "E", "H", "L", "(HL)", "A"};
    private static final String[] REG16_NAMES = {"BC", "DE", "HL", "SP"};
    private static final String[] REG16_PUSH_NAMES = {"BC", "DE", "HL", "AF"};
    private static final String[] CONDITION_NAMES = {"NZ", "Z", "NC", "C", "PO", "PE", "P", "M"};

    /**
     * Дизассемблирует инструкцию по указанному адресу
     *
     * @param memory   объект памяти для чтения инструкции
     * @param position адрес инструкции
     * @return результат дизассемблирования
     */
    public DisassemblyResult disassemble(Memory memory, int position) {
        int originalPosition = position;

        // Читаем первый байт
        int opcode = memory.readByte(position) & 0xFF;
        position++;

        // Проверяем префиксы
        switch (opcode) {
            case 0xCB:
                return disassembleCB(memory, originalPosition);
            case 0xDD:
                return disassembleDD(memory, originalPosition);
            case 0xED:
                return disassembleED(memory, originalPosition);
            case 0xFD:
                return disassembleFD(memory, originalPosition);
            default:
                return disassembleMain(memory, originalPosition, opcode);
        }
    }

    /**
     * Дизассемблирует основные инструкции (без префиксов)
     */
    private DisassemblyResult disassembleMain(Memory memory, int position, int opcode) {
        int originalPosition = position;
        position++; // Skip opcode

        switch (opcode) {
            case 0x00:
                return new DisassemblyResult("NOP", 1, new int[]{opcode});

            // LD rr,nn instructions
            case 0x01: // LD BC,nn
                return formatLD16Immediate(memory, originalPosition, "BC");
            case 0x11: // LD DE,nn
                return formatLD16Immediate(memory, originalPosition, "DE");
            case 0x21: // LD HL,nn
                return formatLD16Immediate(memory, originalPosition, "HL");
            case 0x31: // LD SP,nn
                return formatLD16Immediate(memory, originalPosition, "SP");

            // LD (rr),A instructions
            case 0x02: // LD (BC),A
                return new DisassemblyResult("LD (BC),A", 1, new int[]{opcode});
            case 0x12: // LD (DE),A
                return new DisassemblyResult("LD (DE),A", 1, new int[]{opcode});

            // LD A,(rr) instructions
            case 0x0A: // LD A,(BC)
                return new DisassemblyResult("LD A,(BC)", 1, new int[]{opcode});
            case 0x1A: // LD A,(DE)
                return new DisassemblyResult("LD A,(DE)", 1, new int[]{opcode});

            // INC/DEC rr instructions
            case 0x03:
                return new DisassemblyResult("INC BC", 1, new int[]{opcode});
            case 0x13:
                return new DisassemblyResult("INC DE", 1, new int[]{opcode});
            case 0x23:
                return new DisassemblyResult("INC HL", 1, new int[]{opcode});
            case 0x33:
                return new DisassemblyResult("INC SP", 1, new int[]{opcode});
            case 0x0B:
                return new DisassemblyResult("DEC BC", 1, new int[]{opcode});
            case 0x1B:
                return new DisassemblyResult("DEC DE", 1, new int[]{opcode});
            case 0x2B:
                return new DisassemblyResult("DEC HL", 1, new int[]{opcode});
            case 0x3B:
                return new DisassemblyResult("DEC SP", 1, new int[]{opcode});

            // INC/DEC r instructions
            case 0x04:
            case 0x0C:
            case 0x14:
            case 0x1C:
            case 0x24:
            case 0x2C:
            case 0x3C: {
                int reg = (opcode >> 3) & 0x07;
                return new DisassemblyResult("INC " + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0x05:
            case 0x0D:
            case 0x15:
            case 0x1D:
            case 0x25:
            case 0x2D:
            case 0x3D: {
                int reg = (opcode >> 3) & 0x07;
                return new DisassemblyResult("DEC " + REG8_NAMES[reg], 1, new int[]{opcode});
            }

            // LD r,n instructions
            case 0x06:
            case 0x0E:
            case 0x16:
            case 0x1E:
            case 0x26:
            case 0x2E:
            case 0x36:
            case 0x3E: {
                int reg = (opcode >> 3) & 0x07;
                int immediate = memory.readByte(position) & 0xFF;
                String regName = REG8_NAMES[reg];
                if (reg == 6) regName = "(HL)"; // Special case for (HL)
                return new DisassemblyResult(
                        String.format("LD %s,0x%02X", regName, immediate),
                        2, new int[]{opcode, immediate}
                );
            }

            // Rotate instructions
            case 0x07:
                return new DisassemblyResult("RLCA", 1, new int[]{opcode});
            case 0x0F:
                return new DisassemblyResult("RRCA", 1, new int[]{opcode});
            case 0x17:
                return new DisassemblyResult("RLA", 1, new int[]{opcode});
            case 0x1F:
                return new DisassemblyResult("RRA", 1, new int[]{opcode});

            // Exchange instructions
            case 0x08:
                return new DisassemblyResult("EX AF,AF'", 1, new int[]{opcode});
            case 0xD9:
                return new DisassemblyResult("EXX", 1, new int[]{opcode});
            case 0xEB:
                return new DisassemblyResult("EX DE,HL", 1, new int[]{opcode});

            // ADD HL,rr instructions
            case 0x09:
            case 0x19:
            case 0x29:
            case 0x39: {
                int reg = (opcode >> 4) & 0x03;
                return new DisassemblyResult("ADD HL," + REG16_NAMES[reg], 1, new int[]{opcode});
            }

            // Relative jump instructions
            case 0x10: // DJNZ e
                return formatRelativeJump(memory, originalPosition, "DJNZ");
            case 0x18: // JR e
                return formatRelativeJump(memory, originalPosition, "JR");
            case 0x20:
            case 0x28:
            case 0x30:
            case 0x38: { // JR cc,e
                int condition = (opcode >> 3) & 0x03;
                return formatRelativeJump(memory, originalPosition, "JR " + CONDITION_NAMES[condition]);
            }

            // Extended memory operations
            case 0x22: // LD (nn),HL
                return formatLD16Extended(memory, originalPosition, "LD (0x%04X),HL");
            case 0x2A: // LD HL,(nn)
                return formatLD16Extended(memory, originalPosition, "LD HL,(0x%04X)");
            case 0x32: // LD (nn),A
                return formatLD16Extended(memory, originalPosition, "LD (0x%04X),A");
            case 0x3A: // LD A,(nn)
                return formatLD16Extended(memory, originalPosition, "LD A,(0x%04X)");

            // Misc instructions
            case 0x27:
                return new DisassemblyResult("DAA", 1, new int[]{opcode});
            case 0x2F:
                return new DisassemblyResult("CPL", 1, new int[]{opcode});
            case 0x37:
                return new DisassemblyResult("SCF", 1, new int[]{opcode});
            case 0x3F:
                return new DisassemblyResult("CCF", 1, new int[]{opcode});

            // LD r,r instructions (0x40-0x7F except 0x76)
            case 0x40:
            case 0x41:
            case 0x42:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x47:
            case 0x48:
            case 0x49:
            case 0x4A:
            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x4F:
            case 0x50:
            case 0x51:
            case 0x52:
            case 0x53:
            case 0x54:
            case 0x55:
            case 0x57:
            case 0x58:
            case 0x59:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5D:
            case 0x5F:
            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x67:
            case 0x68:
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6F:
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
            case 0x7F:
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E:
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77: {
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                return new DisassemblyResult(
                        String.format("LD %s,%s", REG8_NAMES[destReg], REG8_NAMES[srcReg]),
                        1, new int[]{opcode}
                );
            }

            case 0x76: // HALT
                return new DisassemblyResult("HALT", 1, new int[]{opcode});

            // Arithmetic instructions with registers
            case 0x80:
            case 0x81:
            case 0x82:
            case 0x83:
            case 0x84:
            case 0x85:
            case 0x86:
            case 0x87: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("ADD A," + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0x88:
            case 0x89:
            case 0x8A:
            case 0x8B:
            case 0x8C:
            case 0x8D:
            case 0x8E:
            case 0x8F: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("ADC A," + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0x90:
            case 0x91:
            case 0x92:
            case 0x93:
            case 0x94:
            case 0x95:
            case 0x96:
            case 0x97: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("SUB " + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0x98:
            case 0x99:
            case 0x9A:
            case 0x9B:
            case 0x9C:
            case 0x9D:
            case 0x9E:
            case 0x9F: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("SBC A," + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0xA0:
            case 0xA1:
            case 0xA2:
            case 0xA3:
            case 0xA4:
            case 0xA5:
            case 0xA6:
            case 0xA7: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("AND " + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0xA8:
            case 0xA9:
            case 0xAA:
            case 0xAB:
            case 0xAC:
            case 0xAD:
            case 0xAE:
            case 0xAF: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("XOR " + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0xB0:
            case 0xB1:
            case 0xB2:
            case 0xB3:
            case 0xB4:
            case 0xB5:
            case 0xB6:
            case 0xB7: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("OR " + REG8_NAMES[reg], 1, new int[]{opcode});
            }
            case 0xB8:
            case 0xB9:
            case 0xBA:
            case 0xBB:
            case 0xBC:
            case 0xBD:
            case 0xBE:
            case 0xBF: {
                int reg = opcode & 0x07;
                return new DisassemblyResult("CP " + REG8_NAMES[reg], 1, new int[]{opcode});
            }

            // Arithmetic instructions with immediate
            case 0xC6:
                return formatArithmeticImmediate(memory, originalPosition, "ADD A");
            case 0xCE:
                return formatArithmeticImmediate(memory, originalPosition, "ADC A");
            case 0xD6:
                return formatArithmeticImmediate(memory, originalPosition, "SUB");
            case 0xDE:
                return formatArithmeticImmediate(memory, originalPosition, "SBC A");
            case 0xE6:
                return formatArithmeticImmediate(memory, originalPosition, "AND");
            case 0xEE:
                return formatArithmeticImmediate(memory, originalPosition, "XOR");
            case 0xF6:
                return formatArithmeticImmediate(memory, originalPosition, "OR");
            case 0xFE:
                return formatArithmeticImmediate(memory, originalPosition, "CP");

            // Conditional returns
            case 0xC0:
            case 0xC8:
            case 0xD0:
            case 0xD8:
            case 0xE0:
            case 0xE8:
            case 0xF0:
            case 0xF8: {
                int condition = (opcode >> 3) & 0x07;
                return new DisassemblyResult("RET " + CONDITION_NAMES[condition], 1, new int[]{opcode});
            }

            // POP instructions
            case 0xC1:
            case 0xD1:
            case 0xE1:
            case 0xF1: {
                int reg = (opcode >> 4) & 0x03;
                return new DisassemblyResult("POP " + REG16_PUSH_NAMES[reg], 1, new int[]{opcode});
            }

            // Conditional jumps
            case 0xC2:
            case 0xCA:
            case 0xD2:
            case 0xDA:
            case 0xE2:
            case 0xEA:
            case 0xF2:
            case 0xFA: {
                int condition = (opcode >> 3) & 0x07;
                return formatAbsoluteJump(memory, originalPosition, "JP " + CONDITION_NAMES[condition]);
            }

            case 0xC3: // JP nn
                return formatAbsoluteJump(memory, originalPosition, "JP");

            // Conditional calls
            case 0xC4:
            case 0xCC:
            case 0xD4:
            case 0xDC:
            case 0xE4:
            case 0xEC:
            case 0xF4:
            case 0xFC: {
                int condition = (opcode >> 3) & 0x07;
                return formatAbsoluteJump(memory, originalPosition, "CALL " + CONDITION_NAMES[condition]);
            }

            // PUSH instructions
            case 0xC5:
            case 0xD5:
            case 0xE5:
            case 0xF5: {
                int reg = (opcode >> 4) & 0x03;
                return new DisassemblyResult("PUSH " + REG16_PUSH_NAMES[reg], 1, new int[]{opcode});
            }

            case 0xC9: // RET
                return new DisassemblyResult("RET", 1, new int[]{opcode});

            case 0xCD: // CALL nn
                return formatAbsoluteJump(memory, originalPosition, "CALL");

            // RST instructions
            case 0xC7:
            case 0xCF:
            case 0xD7:
            case 0xDF:
            case 0xE7:
            case 0xEF:
            case 0xF7:
            case 0xFF: {
                int addr = opcode & 0x38;
                return new DisassemblyResult(String.format("RST 0x%02X", addr), 1, new int[]{opcode});
            }

            // Other single-byte instructions
            case 0xE3:
                return new DisassemblyResult("EX (SP),HL", 1, new int[]{opcode});
            case 0xE9:
                return new DisassemblyResult("JP (HL)", 1, new int[]{opcode});
            case 0xF3:
                return new DisassemblyResult("DI", 1, new int[]{opcode});
            case 0xF9:
                return new DisassemblyResult("LD SP,HL", 1, new int[]{opcode});
            case 0xFB:
                return new DisassemblyResult("EI", 1, new int[]{opcode});

            default:
                return new DisassemblyResult(String.format("DB 0x%02X", opcode), 1, new int[]{opcode});
        }
    }

    /**
     * Дизассемблирует CB-префиксные инструкции (битовые операции)
     */
    private DisassemblyResult disassembleCB(Memory memory, int position) {
        int cbPrefix = memory.readByte(position) & 0xFF;     // 0xCB
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual CB opcode

        int regIndex = opcode & 0x07;  // Индекс регистра (0-7: B,C,D,E,H,L,(HL),A)
        int operation = (opcode >> 3) & 0x07; // Операция (0-7)
        int group = (opcode >> 6) & 0x03;     // Группа операций (0-3)

        String regName = REG8_NAMES[regIndex];
        String mnemonic;

        switch (group) {
            case 0: // Rotations and shifts (0x00-0x3F)
                mnemonic = formatCBRotateShift(operation, regName);
                break;

            case 1: // BIT operations (0x40-0x7F)
                int bitNum = operation;
                mnemonic = String.format("BIT %d,%s", bitNum, regName);
                break;

            case 2: // RES operations (0x80-0xBF)
                bitNum = operation;
                mnemonic = String.format("RES %d,%s", bitNum, regName);
                break;

            case 3: // SET operations (0xC0-0xFF)
                bitNum = operation;
                mnemonic = String.format("SET %d,%s", bitNum, regName);
                break;

            default:
                mnemonic = String.format("CB 0x%02X", opcode);
                break;
        }

        return new DisassemblyResult(mnemonic, 2, new int[]{cbPrefix, opcode});
    }

    /**
     * Форматирует операции поворота и сдвига для CB инструкций
     */
    private String formatCBRotateShift(int operation, String regName) {
        switch (operation) {
            case 0:
                return "RLC " + regName;
            case 1:
                return "RRC " + regName;
            case 2:
                return "RL " + regName;
            case 3:
                return "RR " + regName;
            case 4:
                return "SLA " + regName;
            case 5:
                return "SRA " + regName;
            case 6:
                return "SLL " + regName;  // Undocumented
            case 7:
                return "SRL " + regName;
            default:
                return "??? " + regName;
        }
    }

    /**
     * Дизассемблирует DD-префиксные инструкции (операции с IX)
     */
    private DisassemblyResult disassembleDD(Memory memory, int position) {
        int ddPrefix = memory.readByte(position) & 0xFF;     // 0xDD
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual DD opcode

        // Проверяем на DDCB префикс
        if (opcode == 0xCB) {
            return disassembleDDCB(memory, position);
        }

        String mnemonic;
        int length = 2;
        int[] bytes;

        switch (opcode) {
            // LD IX,nn
            case 0x21: {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                int address = (high << 8) | low;
                mnemonic = String.format("LD IX,0x%04X", address);
                return new DisassemblyResult(mnemonic, 4, new int[]{ddPrefix, opcode, low, high});
            }

            // LD (nn),IX
            case 0x22: {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                int address = (high << 8) | low;
                mnemonic = String.format("LD (0x%04X),IX", address);
                return new DisassemblyResult(mnemonic, 4, new int[]{ddPrefix, opcode, low, high});
            }

            // INC IX
            case 0x23:
                mnemonic = "INC IX";
                break;

            // DEC IX
            case 0x2B:
                mnemonic = "DEC IX";
                break;

            // LD IX,(nn)
            case 0x2A: {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                int address = (high << 8) | low;
                mnemonic = String.format("LD IX,(0x%04X)", address);
                return new DisassemblyResult(mnemonic, 4, new int[]{ddPrefix, opcode, low, high});
            }

            // INC (IX+d)
            case 0x34: {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("INC (IX%+d)", signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, displacement});
            }

            // DEC (IX+d)
            case 0x35: {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("DEC (IX%+d)", signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, displacement});
            }

            // LD (IX+d),n
            case 0x36: {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int immediate = memory.readByte(position + 3) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("LD (IX%+d),0x%02X", signedDisp, immediate);
                return new DisassemblyResult(mnemonic, 4, new int[]{ddPrefix, opcode, displacement, immediate});
            }

            // ADD IX,BC/DE/IX/SP
            case 0x09:
                mnemonic = "ADD IX,BC";
                break;
            case 0x19:
                mnemonic = "ADD IX,DE";
                break;
            case 0x29:
                mnemonic = "ADD IX,IX";
                break;
            case 0x39:
                mnemonic = "ADD IX,SP";
                break;

            // LD r,r instructions with IX/IY halves (undocumented)
            case 0x40:
            case 0x41:
            case 0x42:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x47:
            case 0x48:
            case 0x49:
            case 0x4A:
            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x4F:
            case 0x50:
            case 0x51:
            case 0x52:
            case 0x53:
            case 0x54:
            case 0x55:
            case 0x57:
            case 0x58:
            case 0x59:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5D:
            case 0x5F:
            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x67:
            case 0x68:
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6F:
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
            case 0x7F: {
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                // Map H/L to IXh/IXl for undocumented instructions
                String destName = (destReg == 4) ? "IXh" : (destReg == 5) ? "IXl" : REG8_NAMES[destReg];
                String srcName = (srcReg == 4) ? "IXh" : (srcReg == 5) ? "IXl" : REG8_NAMES[srcReg];
                // Skip (HL) variants as they're handled by indexed instructions above
                if (destReg != 6 && srcReg != 6) {
                    mnemonic = String.format("LD %s,%s", destName, srcName);
                    break;
                }
                // Fall through to default for (HL) variants
            }

            // INC/DEC IXh, IXl (undocumented)
            case 0x24:
                mnemonic = "INC IXh";
                break;
            case 0x25:
                mnemonic = "DEC IXh";
                break;
            case 0x2C:
                mnemonic = "INC IXl";
                break;
            case 0x2D:
                mnemonic = "DEC IXl";
                break;

            // LD IXh/IXl,n (undocumented)
            case 0x26: {
                int immediate = memory.readByte(position + 2) & 0xFF;
                mnemonic = String.format("LD IXh,0x%02X", immediate);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, immediate});
            }
            case 0x2E: {
                int immediate = memory.readByte(position + 2) & 0xFF;
                mnemonic = String.format("LD IXl,0x%02X", immediate);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, immediate});
            }

            // Arithmetic with IXh/IXl (undocumented)
            case 0x84:
                mnemonic = "ADD A,IXh";
                break;
            case 0x85:
                mnemonic = "ADD A,IXl";
                break;
            case 0x8C:
                mnemonic = "ADC A,IXh";
                break;
            case 0x8D:
                mnemonic = "ADC A,IXl";
                break;
            case 0x94:
                mnemonic = "SUB IXh";
                break;
            case 0x95:
                mnemonic = "SUB IXl";
                break;
            case 0x9C:
                mnemonic = "SBC A,IXh";
                break;
            case 0x9D:
                mnemonic = "SBC A,IXl";
                break;
            case 0xA4:
                mnemonic = "AND IXh";
                break;
            case 0xA5:
                mnemonic = "AND IXl";
                break;
            case 0xAC:
                mnemonic = "XOR IXh";
                break;
            case 0xAD:
                mnemonic = "XOR IXl";
                break;
            case 0xB4:
                mnemonic = "OR IXh";
                break;
            case 0xB5:
                mnemonic = "OR IXl";
                break;
            case 0xBC:
                mnemonic = "CP IXh";
                break;
            case 0xBD:
                mnemonic = "CP IXl";
                break;

            // LD r,(IX+d) instructions
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E: {
                int reg = (opcode >> 3) & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("LD %s,(IX%+d)", REG8_NAMES[reg], signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, displacement});
            }

            // LD (IX+d),r instructions
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77: {
                int reg = opcode & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("LD (IX%+d),%s", signedDisp, REG8_NAMES[reg]);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, displacement});
            }

            // Arithmetic operations with (IX+d)
            case 0x86:
            case 0x8E:
            case 0x96:
            case 0x9E:
            case 0xA6:
            case 0xAE:
            case 0xB6:
            case 0xBE: {
                String operation = getArithmeticOperation(opcode);
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("%s (IX%+d)", operation, signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{ddPrefix, opcode, displacement});
            }

            // PUSH IX
            case 0xE5:
                mnemonic = "PUSH IX";
                break;

            // POP IX
            case 0xE1:
                mnemonic = "POP IX";
                break;

            // EX (SP),IX
            case 0xE3:
                mnemonic = "EX (SP),IX";
                break;

            // JP (IX)
            case 0xE9:
                mnemonic = "JP (IX)";
                break;

            // LD SP,IX
            case 0xF9:
                mnemonic = "LD SP,IX";
                break;

            // Invalid DD opcodes - treat as NOP + next instruction
            default:
                mnemonic = String.format("DD 0x%02X", opcode);
                break;
        }

        bytes = new int[]{ddPrefix, opcode};
        return new DisassemblyResult(mnemonic, length, bytes);
    }

    /**
     * Дизассемблирует FD-префиксные инструкции (операции с IY)
     */
    private DisassemblyResult disassembleFD(Memory memory, int position) {
        int fdPrefix = memory.readByte(position) & 0xFF;     // 0xFD
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual FD opcode

        // Проверяем на FDCB префикс
        if (opcode == 0xCB) {
            return disassembleFDCB(memory, position);
        }

        String mnemonic;
        int length = 2;
        int[] bytes;

        switch (opcode) {
            // LD IY,nn
            case 0x21: {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                int address = (high << 8) | low;
                mnemonic = String.format("LD IY,0x%04X", address);
                return new DisassemblyResult(mnemonic, 4, new int[]{fdPrefix, opcode, low, high});
            }

            // LD (nn),IY
            case 0x22: {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                int address = (high << 8) | low;
                mnemonic = String.format("LD (0x%04X),IY", address);
                return new DisassemblyResult(mnemonic, 4, new int[]{fdPrefix, opcode, low, high});
            }

            // INC IY
            case 0x23:
                mnemonic = "INC IY";
                break;

            // DEC IY
            case 0x2B:
                mnemonic = "DEC IY";
                break;

            // LD IY,(nn)
            case 0x2A: {
                int low = memory.readByte(position + 2) & 0xFF;
                int high = memory.readByte(position + 3) & 0xFF;
                int address = (high << 8) | low;
                mnemonic = String.format("LD IY,(0x%04X)", address);
                return new DisassemblyResult(mnemonic, 4, new int[]{fdPrefix, opcode, low, high});
            }

            // INC (IY+d)
            case 0x34: {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("INC (IY%+d)", signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, displacement});
            }

            // DEC (IY+d)
            case 0x35: {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("DEC (IY%+d)", signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, displacement});
            }

            // LD (IY+d),n
            case 0x36: {
                int displacement = memory.readByte(position + 2) & 0xFF;
                int immediate = memory.readByte(position + 3) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("LD (IY%+d),0x%02X", signedDisp, immediate);
                return new DisassemblyResult(mnemonic, 4, new int[]{fdPrefix, opcode, displacement, immediate});
            }

            // ADD IY,BC/DE/IY/SP
            case 0x09:
                mnemonic = "ADD IY,BC";
                break;
            case 0x19:
                mnemonic = "ADD IY,DE";
                break;
            case 0x29:
                mnemonic = "ADD IY,IY";
                break;
            case 0x39:
                mnemonic = "ADD IY,SP";
                break;

            // LD r,r instructions with IX/IY halves (undocumented)
            case 0x40:
            case 0x41:
            case 0x42:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x47:
            case 0x48:
            case 0x49:
            case 0x4A:
            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x4F:
            case 0x50:
            case 0x51:
            case 0x52:
            case 0x53:
            case 0x54:
            case 0x55:
            case 0x57:
            case 0x58:
            case 0x59:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5D:
            case 0x5F:
            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x67:
            case 0x68:
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6F:
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
            case 0x7F: {
                int destReg = (opcode >> 3) & 0x07;
                int srcReg = opcode & 0x07;
                // Map H/L to IYh/IYl for undocumented instructions
                String destName = (destReg == 4) ? "IYh" : (destReg == 5) ? "IYl" : REG8_NAMES[destReg];
                String srcName = (srcReg == 4) ? "IYh" : (srcReg == 5) ? "IYl" : REG8_NAMES[srcReg];
                // Skip (HL) variants as they're handled by indexed instructions below
                if (destReg != 6 && srcReg != 6) {
                    mnemonic = String.format("LD %s,%s", destName, srcName);
                    break;
                }
                // Fall through to default for (HL) variants
            }

            // INC/DEC IYh, IYl (undocumented)
            case 0x24:
                mnemonic = "INC IYh";
                break;
            case 0x25:
                mnemonic = "DEC IYh";
                break;
            case 0x2C:
                mnemonic = "INC IYl";
                break;
            case 0x2D:
                mnemonic = "DEC IYl";
                break;

            // LD IYh/IYl,n (undocumented)
            case 0x26: {
                int immediate = memory.readByte(position + 2) & 0xFF;
                mnemonic = String.format("LD IYh,0x%02X", immediate);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, immediate});
            }
            case 0x2E: {
                int immediate = memory.readByte(position + 2) & 0xFF;
                mnemonic = String.format("LD IYl,0x%02X", immediate);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, immediate});
            }

            // Arithmetic with IYh/IYl (undocumented)
            case 0x84:
                mnemonic = "ADD A,IYh";
                break;
            case 0x85:
                mnemonic = "ADD A,IYl";
                break;
            case 0x8C:
                mnemonic = "ADC A,IYh";
                break;
            case 0x8D:
                mnemonic = "ADC A,IYl";
                break;
            case 0x94:
                mnemonic = "SUB IYh";
                break;
            case 0x95:
                mnemonic = "SUB IYl";
                break;
            case 0x9C:
                mnemonic = "SBC A,IYh";
                break;
            case 0x9D:
                mnemonic = "SBC A,IYl";
                break;
            case 0xA4:
                mnemonic = "AND IYh";
                break;
            case 0xA5:
                mnemonic = "AND IYl";
                break;
            case 0xAC:
                mnemonic = "XOR IYh";
                break;
            case 0xAD:
                mnemonic = "XOR IYl";
                break;
            case 0xB4:
                mnemonic = "OR IYh";
                break;
            case 0xB5:
                mnemonic = "OR IYl";
                break;
            case 0xBC:
                mnemonic = "CP IYh";
                break;
            case 0xBD:
                mnemonic = "CP IYl";
                break;

            // LD r,(IY+d) instructions
            case 0x46:
            case 0x4E:
            case 0x56:
            case 0x5E:
            case 0x66:
            case 0x6E:
            case 0x7E: {
                int reg = (opcode >> 3) & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("LD %s,(IY%+d)", REG8_NAMES[reg], signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, displacement});
            }

            // LD (IY+d),r instructions
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x77: {
                int reg = opcode & 0x07;
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("LD (IY%+d),%s", signedDisp, REG8_NAMES[reg]);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, displacement});
            }

            // Arithmetic operations with (IY+d)
            case 0x86:
            case 0x8E:
            case 0x96:
            case 0x9E:
            case 0xA6:
            case 0xAE:
            case 0xB6:
            case 0xBE: {
                String operation = getArithmeticOperation(opcode);
                int displacement = memory.readByte(position + 2) & 0xFF;
                int signedDisp = (displacement > 127) ? displacement - 256 : displacement;
                mnemonic = String.format("%s (IY%+d)", operation, signedDisp);
                return new DisassemblyResult(mnemonic, 3, new int[]{fdPrefix, opcode, displacement});
            }

            // PUSH IY
            case 0xE5:
                mnemonic = "PUSH IY";
                break;

            // POP IY
            case 0xE1:
                mnemonic = "POP IY";
                break;

            // EX (SP),IY
            case 0xE3:
                mnemonic = "EX (SP),IY";
                break;

            // JP (IY)
            case 0xE9:
                mnemonic = "JP (IY)";
                break;

            // LD SP,IY
            case 0xF9:
                mnemonic = "LD SP,IY";
                break;

            // Invalid FD opcodes
            default:
                mnemonic = String.format("FD 0x%02X", opcode);
                break;
        }

        bytes = new int[]{fdPrefix, opcode};
        return new DisassemblyResult(mnemonic, length, bytes);
    }

    /**
     * Возвращает название арифметической операции по опкоду
     */
    private String getArithmeticOperation(int opcode) {
        switch (opcode & 0xF8) {
            case 0x80:
                return "ADD A,";
            case 0x88:
                return "ADC A,";
            case 0x90:
                return "SUB";
            case 0x98:
                return "SBC A,";
            case 0xA0:
                return "AND";
            case 0xA8:
                return "XOR";
            case 0xB0:
                return "OR";
            case 0xB8:
                return "CP";
            default:
                return "???";
        }
    }

    /**
     * Дизассемблирует DDCB-префиксные инструкции (битовые операции с IX+d)
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

        String mnemonic;

        switch (group) {
            case 0: // Rotations and shifts
                String rotOp = formatCBRotateShift(operation, "");
                mnemonic = rotOp.trim() + String.format(" (IX%+d)", signedDisp);
                if (regIndex != 6) { // Undocumented: also load to register
                    mnemonic += "," + REG8_NAMES[regIndex];
                }
                break;
            case 1: // BIT operations
                mnemonic = String.format("BIT %d,(IX%+d)", operation, signedDisp);
                break;
            case 2: // RES operations
                mnemonic = String.format("RES %d,(IX%+d)", operation, signedDisp);
                if (regIndex != 6) { // Undocumented: also load to register
                    mnemonic += "," + REG8_NAMES[regIndex];
                }
                break;
            case 3: // SET operations
                mnemonic = String.format("SET %d,(IX%+d)", operation, signedDisp);
                if (regIndex != 6) { // Undocumented: also load to register
                    mnemonic += "," + REG8_NAMES[regIndex];
                }
                break;
            default:
                mnemonic = String.format("DDCB %02X %02X", displacement, opcode);
                break;
        }

        return new DisassemblyResult(mnemonic, 4, new int[]{ddPrefix, cbPrefix, displacement, opcode});
    }

    /**
     * Дизассемблирует FDCB-префиксные инструкции (битовые операции с IY+d)
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

        String mnemonic;

        switch (group) {
            case 0: // Rotations and shifts
                String rotOp = formatCBRotateShift(operation, "");
                mnemonic = rotOp.trim() + String.format(" (IY%+d)", signedDisp);
                if (regIndex != 6) { // Undocumented: also load to register
                    mnemonic += "," + REG8_NAMES[regIndex];
                }
                break;
            case 1: // BIT operations
                mnemonic = String.format("BIT %d,(IY%+d)", operation, signedDisp);
                break;
            case 2: // RES operations
                mnemonic = String.format("RES %d,(IY%+d)", operation, signedDisp);
                if (regIndex != 6) { // Undocumented: also load to register
                    mnemonic += "," + REG8_NAMES[regIndex];
                }
                break;
            case 3: // SET operations
                mnemonic = String.format("SET %d,(IY%+d)", operation, signedDisp);
                if (regIndex != 6) { // Undocumented: also load to register
                    mnemonic += "," + REG8_NAMES[regIndex];
                }
                break;
            default:
                mnemonic = String.format("FDCB %02X %02X", displacement, opcode);
                break;
        }

        return new DisassemblyResult(mnemonic, 4, new int[]{fdPrefix, cbPrefix, displacement, opcode});
    }

    // Утилитарные методы для GUI

    /**
     * Дизассемблирует блок памяти и возвращает список результатов
     *
     * @param memory           объект памяти
     * @param startAddress     начальный адрес
     * @param instructionCount количество инструкций для дизассемблирования
     * @return список результатов дизассемблирования
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
                // В случае ошибки, создаем DB байт и переходим к следующему
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
     * Дизассемблирует память в указанном диапазоне адресов
     *
     * @param memory       объект памяти
     * @param startAddress начальный адрес
     * @param endAddress   конечный адрес
     * @return список результатов дизассемблирования
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

                // Защита от бесконечного цикла
                if (currentAddress < startAddress && endAddress > startAddress) {
                    break; // Произошел переход через границу 0xFFFF
                }
            } catch (Exception e) {
                // В случае ошибки, создаем DB байт и переходим к следующему
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
     * Создает текстовое представление дизассемблирования для вывода в консоль или файл
     *
     * @param results список результатов дизассемблирования
     * @return текстовое представление
     */
    public String formatDisassemblyText(List<DisassemblyResult> results) {
        StringBuilder sb = new StringBuilder();
        for (DisassemblyResult result : results) {
            sb.append(result.getAddressedOutput()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Создает табличное представление для GUI таблиц
     *
     * @param results список результатов дизассемблирования
     * @return двумерный массив строк для таблицы
     */
    public String[][] formatDisassemblyTable(List<DisassemblyResult> results) {
        String[][] table = new String[results.size()][];
        for (int i = 0; i < results.size(); i++) {
            DisassemblyResult result = results.get(i);
            // Извлекаем адрес из addressedOutput или используем индекс
            int address = extractAddressFromResult(result, i);
            table[i] = result.getTableRow(address);
        }
        return table;
    }

    /**
     * Извлекает адрес из результата дизассемблирования
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
     * Возвращает заголовки колонок для табличного представления
     */
    public String[] getTableHeaders() {
        return new String[]{"Address", "Bytes", "Instruction"};
    }

    /**
     * Дизассемблирует ED-префиксные инструкции (расширенный набор)
     */
    private DisassemblyResult disassembleED(Memory memory, int position) {
        int edPrefix = memory.readByte(position) & 0xFF;     // 0xED
        int opcode = memory.readByte(position + 1) & 0xFF;   // Actual ED opcode

        String mnemonic;
        int length = 2;
        int[] bytes = new int[]{edPrefix, opcode};

        switch (opcode) {
            // 16-bit loads to/from memory - LD (nn),rr
            case 0x43: // LD (nn),BC
                return formatED16BitLoad(memory, position, "LD (0x%04X),BC");
            case 0x53: // LD (nn),DE
                return formatED16BitLoad(memory, position, "LD (0x%04X),DE");
            case 0x63: // LD (nn),HL
                return formatED16BitLoad(memory, position, "LD (0x%04X),HL");
            case 0x73: // LD (nn),SP
                return formatED16BitLoad(memory, position, "LD (0x%04X),SP");

            // 16-bit loads from memory - LD rr,(nn)
            case 0x4B: // LD BC,(nn)
                return formatED16BitLoad(memory, position, "LD BC,(0x%04X)");
            case 0x5B: // LD DE,(nn)
                return formatED16BitLoad(memory, position, "LD DE,(0x%04X)");
            case 0x6B: // LD HL,(nn)
                return formatED16BitLoad(memory, position, "LD HL,(0x%04X)");
            case 0x7B: // LD SP,(nn)
                return formatED16BitLoad(memory, position, "LD SP,(0x%04X)");

            // 16-bit arithmetic - ADC HL,rr
            case 0x4A:
                mnemonic = "ADC HL,BC";
                break;
            case 0x5A:
                mnemonic = "ADC HL,DE";
                break;
            case 0x6A:
                mnemonic = "ADC HL,HL";
                break;
            case 0x7A:
                mnemonic = "ADC HL,SP";
                break;

            // 16-bit arithmetic - SBC HL,rr
            case 0x42:
                mnemonic = "SBC HL,BC";
                break;
            case 0x52:
                mnemonic = "SBC HL,DE";
                break;
            case 0x62:
                mnemonic = "SBC HL,HL";
                break;
            case 0x72:
                mnemonic = "SBC HL,SP";
                break;

            // NEG - Negate accumulator (multiple opcodes)
            case 0x44:
            case 0x4C:
            case 0x54:
            case 0x5C:
            case 0x64:
            case 0x6C:
            case 0x74:
            case 0x7C:
                mnemonic = "NEG";
                break;

            // RETN - Return from non-maskable interrupt (multiple opcodes)
            case 0x45:
            case 0x55:
            case 0x65:
            case 0x75:
                mnemonic = "RETN";
                break;

            // RETI - Return from interrupt (multiple opcodes)
            case 0x4D:
            case 0x6D:
            case 0x7D:
                mnemonic = "RETI";
                break;

            // Interrupt modes
            case 0x46:
            case 0x4E:
            case 0x66:
            case 0x6E: // IM 0
                mnemonic = "IM 0";
                break;
            case 0x56:
            case 0x76: // IM 1
                mnemonic = "IM 1";
                break;
            case 0x5E:
            case 0x7E: // IM 2
                mnemonic = "IM 2";
                break;

            // Special register operations
            case 0x47:
                mnemonic = "LD I,A";
                break;
            case 0x4F:
                mnemonic = "LD R,A";
                break;
            case 0x57:
                mnemonic = "LD A,I";
                break;
            case 0x5F:
                mnemonic = "LD A,R";
                break;

            // Decimal rotate operations
            case 0x67:
                mnemonic = "RRD";
                break;
            case 0x6F:
                mnemonic = "RLD";
                break;

            // Block load operations
            case 0xA0:
                mnemonic = "LDI";
                break;
            case 0xA8:
                mnemonic = "LDD";
                break;
            case 0xB0:
                mnemonic = "LDIR";
                break;
            case 0xB8:
                mnemonic = "LDDR";
                break;

            // Block compare operations
            case 0xA1:
                mnemonic = "CPI";
                break;
            case 0xA9:
                mnemonic = "CPD";
                break;
            case 0xB1:
                mnemonic = "CPIR";
                break;
            case 0xB9:
                mnemonic = "CPDR";
                break;

            // I/O operations - IN r,(C)
            case 0x40:
            case 0x48:
            case 0x50:
            case 0x58:
            case 0x60:
            case 0x68:
            case 0x78: {
                int reg = (opcode >> 3) & 0x07;
                if (reg == 6) {
                    mnemonic = "IN (C)";  // Special case - doesn't store result
                } else {
                    mnemonic = "IN " + REG8_NAMES[reg] + ",(C)";
                }
                break;
            }

            // I/O operations - OUT (C),r
            case 0x41:
            case 0x49:
            case 0x51:
            case 0x59:
            case 0x61:
            case 0x69:
            case 0x71:
            case 0x79: {
                int reg = (opcode >> 3) & 0x07;
                if (reg == 6) {
                    mnemonic = "OUT (C),0";  // Special case
                } else {
                    mnemonic = "OUT (C)," + REG8_NAMES[reg];
                }
                break;
            }

            // Block I/O operations - input
            case 0xA2:
                mnemonic = "INI";
                break;
            case 0xAA:
                mnemonic = "IND";
                break;
            case 0xB2:
                mnemonic = "INIR";
                break;
            case 0xBA:
                mnemonic = "INDR";
                break;

            // Block I/O operations - output
            case 0xA3:
                mnemonic = "OUTI";
                break;
            case 0xAB:
                mnemonic = "OUTD";
                break;
            case 0xB3:
                mnemonic = "OTIR";
                break;
            case 0xBB:
                mnemonic = "OTDR";
                break;

            // Invalid/undocumented ED opcodes
            default:
                mnemonic = String.format("ED 0x%02X", opcode);
                break;
        }

        return new DisassemblyResult(mnemonic, length, bytes);
    }

    /**
     * Форматирует 16-битные загрузки ED-префикса с адресом
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

    private DisassemblyResult formatRelativeJump(Memory memory, int position, String instruction) {
        int opcode = memory.readByte(position) & 0xFF;
        int offset = memory.readByte(position + 1) & 0xFF;

        // Convert to signed byte
        if (offset > 127) {
            offset -= 256;
        }

        int target = (position + 2 + offset) & 0xFFFF;

        return new DisassemblyResult(
                String.format("%s 0x%04X", instruction, target),
                2, new int[]{opcode, memory.readByte(position + 1) & 0xFF}
        );
    }

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

    private DisassemblyResult formatArithmeticImmediate(Memory memory, int position, String operation) {
        int opcode = memory.readByte(position) & 0xFF;
        int immediate = memory.readByte(position + 1) & 0xFF;

        return new DisassemblyResult(
                String.format("%s,0x%02X", operation, immediate),
                2, new int[]{opcode, immediate}
        );
    }
}