# Z80 Дизассемблер

Полнофункциональный дизассемблер для процессора Z80, поддерживающий все инструкции, включая недокументированные.

## Возможности

- **Полный набор инструкций Z80**: Поддержка всех документированных и недокументированных инструкций
- **Все префиксы**: CB, ED, DD, FD и их комбинации (DDCB, FDCB)
- **Индексированная адресация**: IX и IY регистры со смещениями
- **Недокументированные возможности**: IXh, IXl, IYh, IYl операции и другие
- **Удобный API**: Простой интерфейс для интеграции в эмулятор
- **GUI-поддержка**: Форматированный вывод для отображения в таблицах

## Основное использование

### Базовое дизассемблирование

```java
Z80Disassembler disassembler = new Z80Disassembler();
Memory memory = new Memory();

// Загружаем инструкцию в память
memory.writeByte(0x1000, 0x3E);  // LD A,n
memory.writeByte(0x1001, 0x42);  // 0x42

// Дизассемблируем
Z80Disassembler.DisassemblyResult result = disassembler.disassemble(memory, 0x1000);

System.out.println("Мнемоника: " + result.getMnemonic());     // "LD A,0x42"
System.out.println("Длина: " + result.getLength());           // 2
System.out.println("Байты: " + result.getHexBytes());         // "3E 42"
```

### Дизассемблирование с адресами

```java
// Создание результата с адресом
Z80Disassembler.DisassemblyResult result = disassembler
    .disassemble(memory, 0x1000)
    .withAddress(0x1000);

System.out.println(result.getAddressedOutput()); // "1000: 3E 42        LD A,0x42"
```

### Блочное дизассемблирование

```java
// Дизассемблировать 10 инструкций начиная с 0x8000
List<Z80Disassembler.DisassemblyResult> results =
    disassembler.disassembleBlock(memory, 0x8000, 10);

for (Z80Disassembler.DisassemblyResult res : results) {
    System.out.println(res.getAddressedOutput());
}
```

### Дизассемблирование диапазона

```java
// Дизассемблировать от 0x8000 до 0x8020
List<Z80Disassembler.DisassemblyResult> results =
    disassembler.disassembleRange(memory, 0x8000, 0x8020);
```

## GUI интеграция

### Табличное представление

```java
List<Z80Disassembler.DisassemblyResult> results =
    disassembler.disassembleBlock(memory, 0x8000, 20);

// Получить заголовки таблицы
String[] headers = disassembler.getTableHeaders(); // ["Address", "Bytes", "Instruction"]

// Получить данные для таблицы
String[][] tableData = disassembler.formatDisassemblyTable(results);

// Использовать в JTable или другой GUI компоненте
JTable table = new JTable(tableData, headers);
```

### Текстовый вывод

```java
// Для консольного вывода или экспорта в файл
String text = disassembler.formatDisassemblyText(results);
System.out.println(text);
```

## Поддерживаемые инструкции

### Базовые инструкции
- Все 8-битные и 16-битные загрузки (LD)
- Арифметические операции (ADD, SUB, ADC, SBC)
- Логические операции (AND, OR, XOR, CP)
- Операции инкремента/декремента (INC, DEC)
- Переходы и вызовы (JP, JR, CALL, RET)
- Стековые операции (PUSH, POP)

### CB-префиксные инструкции
- Операции поворота (RLC, RRC, RL, RR)
- Операции сдвига (SLA, SRA, SRL)
- SLL (недокументированная)
- Битовые операции (BIT, SET, RES)

### ED-префиксные инструкции
- Расширенные 16-битные загрузки
- 16-битная арифметика (ADC HL,rr; SBC HL,rr)
- Операции с специальными регистрами (I, R)
- Режимы прерываний (IM 0/1/2)
- Блочные операции (LDI, LDIR, CPI, CPIR и т.д.)
- Блочный I/O (INI, OUTI и т.д.)
- Десятичные операции (RLD, RRD)

### DD/FD-префиксные инструкции
- Операции с IX/IY регистрами
- Индексированная адресация (IX+d, IY+d)
- Недокументированные операции с IXh, IXl, IYh, IYl

### DDCB/FDCB комбинации
- Битовые операции с индексированной адресацией
- Недокументированные варианты с загрузкой в регистры

## Форматы вывода

### DisassemblyResult методы

```java
result.getMnemonic()         // "LD A,0x42"
result.getLength()           // 2
result.getBytes()            // [0x3E, 0x42]
result.getHexBytes()         // "3E 42"
result.getFormattedOutput()  // "3E 42        LD A,0x42"
result.getAddressedOutput()  // "1000: 3E 42        LD A,0x42"
result.getTableRow(0x1000)   // ["1000", "3E 42", "LD A,0x42"]
```

## Обработка ошибок

Дизассемблер обрабатывает некорректные данные, создавая DB-инструкции:

```java
// Если встречается ошибка при декодировании,
// создается псевдо-инструкция DB
// Например: "DB 0xFF" для байта 0xFF, который нельзя декодировать
```

## Интеграция в эмулятор

### В отладчике

```java
public class DebugWindow {
    private Z80Disassembler disassembler = new Z80Disassembler();

    public void updateDisassemblyView(int pc) {
        List<Z80Disassembler.DisassemblyResult> instructions =
            disassembler.disassembleBlock(memory, pc - 10, 20);

        // Обновить GUI таблицу
        String[][] data = disassembler.formatDisassemblyTable(instructions);
        disassemblyTable.setModel(new DefaultTableModel(data,
            disassembler.getTableHeaders()));
    }
}
```

### В пошаговом выполнении

```java
public void stepInstruction() {
    // Показать текущую инструкцию
    Z80Disassembler.DisassemblyResult current =
        disassembler.disassemble(memory, cpu.getPC()).withAddress(cpu.getPC());

    System.out.println("Выполняется: " + current.getAddressedOutput());

    // Выполнить инструкцию
    cpu.step();
}
```

## Примеры инструкций

```
0000: 00           NOP
0001: 3E 42        LD A,0x42
0003: 06 0A        LD B,0x0A
0005: 80           ADD A,B
0006: CB 3F        SRL A
0008: ED A0        LDI
000A: DD 46 05     LD B,(IX+5)
000D: DD CB FE 46  BIT 0,(IX-2)
0011: DD 26 42     LD IXh,0x42      ; Недокументированная
```

## Тестирование

Дизассемблер поставляется с полным набором тестов в `Z80DisassemblerTest.java`, покрывающим все типы инструкций.

Для запуска тестов:
```bash
mvn test
```