package spectrum.jfx.debug;


import spectrum.jfx.hardware.memory.Memory;
import spectrum.jfx.hardware.memory.MemoryImpl;

import java.util.List;

/**
 *
 */
public class DisassemblerExample {

    public static void main(String[] args) {
        // Создаем объекты
        Z80Disassembler disassembler = new Z80Disassembler();
        Memory memory = new MemoryImpl();

        // Пример программы Z80 (простая программа для ZX Spectrum)
        // ВАЖНО: Используем адрес 0x8000+ (RAM область), так как ROM (0x0000-0x3FFF) защищена от записи
        // Карта памяти ZX Spectrum 48K:
        // 0x0000-0x3FFF: ROM (16K)     - защищена от записи
        // 0x4000-0x7FFF: Screen RAM    - доступна для записи
        // 0x8000-0xFFFF: User RAM      - доступна для записи
        //
        // LD A,5       ; 3E 05
        // LD B,10      ; 06 0A
        // ADD A,B      ; 80
        // LD (0x4000),A; 32 00 40
        // HALT         ; 76

        int address = 0x8000;  // Используем RAM область
        memory.writeByte(address++, 0x3E);  // LD A,5
        memory.writeByte(address++, 0x05);
        memory.writeByte(address++, 0x06);  // LD B,10
        memory.writeByte(address++, 0x0A);
        memory.writeByte(address++, 0x80);  // ADD A,B
        memory.writeByte(address++, 0x32);  // LD (0x4000),A
        memory.writeByte(address++, 0x00);
        memory.writeByte(address++, 0x40);
        memory.writeByte(address++, 0x76);  // HALT

        // Демонстрируем дизассемблирование одной инструкции
        System.out.println("=== Дизассемблирование отдельных инструкций ===");

        int currentAddr = 0x8000;
        Z80Disassembler.DisassemblyResult result = disassembler.disassemble(memory, currentAddr);
        result = result.withAddress(currentAddr);

        System.out.println("Адрес: " + String.format("0x%04X", currentAddr));
        System.out.println("Мнемоника: " + result.getMnemonic());
        System.out.println("Длина: " + result.getLength() + " байт");
        System.out.println("Байты: " + result.getHexBytes());
        System.out.println("Форматированный вывод: " + result.getFormattedOutput());
        System.out.println("Адресованный вывод: " + result.getAddressedOutput());
        System.out.println();

        // Демонстрируем дизассемблирование блока
        System.out.println("=== Дизассемблирование блока инструкций ===");

        List<Z80Disassembler.DisassemblyResult> results =
                disassembler.disassembleBlock(memory, 0x8000, 5);

        for (Z80Disassembler.DisassemblyResult res : results) {
            System.out.println(res.getAddressedOutput());
        }
        System.out.println();

        // Демонстрируем табличное представление
        System.out.println("=== Табличное представление для GUI ===");

        String[] headers = disassembler.getTableHeaders();
        System.out.printf("%-8s %-12s %s%n", headers[0], headers[1], headers[2]);
        System.out.println("----------------------------------------");

        String[][] table = disassembler.formatDisassemblyTable(results);
        for (String[] row : table) {
            System.out.printf("%-8s %-12s %s%n", row[0], row[1], row[2]);
        }
        System.out.println();

        // Демонстрируем сложные инструкции
        System.out.println("=== Сложные инструкции Z80 ===");

        // CB-префиксная инструкция - используем RAM область
        memory.writeByte(0x8100, 0xCB);
        memory.writeByte(0x8101, 0x3F);  // SRL A

        result = disassembler.disassemble(memory, 0x8100).withAddress(0x8100);
        System.out.println(result.getAddressedOutput());

        // ED-префиксная инструкция
        memory.writeByte(0x8110, 0xED);
        memory.writeByte(0x8111, 0xA0);  // LDI

        result = disassembler.disassemble(memory, 0x8110).withAddress(0x8110);
        System.out.println(result.getAddressedOutput());

        // DD-префиксная инструкция
        memory.writeByte(0x8120, 0xDD);
        memory.writeByte(0x8121, 0x46);
        memory.writeByte(0x8122, 0x05);  // LD B,(IX+5)

        result = disassembler.disassemble(memory, 0x8120).withAddress(0x8120);
        System.out.println(result.getAddressedOutput());

        // DDCB комбинация
        memory.writeByte(0x8130, 0xDD);
        memory.writeByte(0x8131, 0xCB);
        memory.writeByte(0x8132, 0xFE);  // -2
        memory.writeByte(0x8133, 0x46);  // BIT 0,(IX-2)

        result = disassembler.disassemble(memory, 0x8130).withAddress(0x8130);
        System.out.println(result.getAddressedOutput());

        // Недокументированная инструкция
        memory.writeByte(0x8140, 0xDD);
        memory.writeByte(0x8141, 0x26);
        memory.writeByte(0x8142, 0x42);  // LD IXh,0x42

        result = disassembler.disassemble(memory, 0x8140).withAddress(0x8140);
        System.out.println(result.getAddressedOutput());

        System.out.println();
        System.out.println("=== Дизассемблер Z80 готов к использованию! ===");
    }
}