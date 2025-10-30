package spectrum.jfx.z80.memory;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spectrum.jfx.z80core.MemIoOps;

/**
 * Класс для управления памятью ZX Spectrum
 * Реализует карту памяти ZX Spectrum 48K:
 * 0x0000-0x3FFF: ROM (16K)
 * 0x4000-0x7FFF: Screen RAM (16K)
 * 0x8000-0xFFFF: User RAM (32K)
 */
public class Memory implements MemIoOps {
    private static final Logger logger = LoggerFactory.getLogger(Memory.class);

    // Размеры областей памяти
    private static final int ROM_SIZE = 16384;     // 16K ROM
    private static final int RAM_SIZE = 49152;     // 48K RAM (16K screen + 32K user)
    private static final int PORTS_SIZE = 65536;      // 256 портов ввода-вывода
    private static final int TOTAL_MEMORY = 65536; // 64K total

    // Адреса границ областей памяти
    private static final int ROM_START = 0x0000;
    private static final int ROM_END = 0x3FFF;
    private static final int SCREEN_RAM_START = 0x4000;
    private static final int SCREEN_RAM_END = 0x7FFF;
    private static final int USER_RAM_START = 0x8000;
    private static final int USER_RAM_END = 0xFFFF;

    // Области памяти
    private final byte[] rom;        // ROM область
    private final byte[] ram;        // RAM область (48K)
    private final byte[] ports;  // Порты ввода-вывода

    // Флаги доступности записи в ROM
    @Getter
    private boolean romWriteProtected = true;

    private long tStates = 0;

    public Memory() {
        logger.info("Initializing ZX Spectrum memory");

        rom = new byte[ROM_SIZE];
        ram = new byte[RAM_SIZE];
        ports = new byte[PORTS_SIZE];

        // Инициализация памяти нулями
        clearMemory();

        logger.info("Memory initialized: ROM={}K, RAM={}K, Ports={}K", ROM_SIZE / 1024, RAM_SIZE / 1024, PORTS_SIZE / 1024);
    }

    /**
     * Очищает всю память
     */
    public void clearMemory() {
        logger.debug("Clearing memory");

        // Очистка ROM (только если не защищена от записи)
        if (!romWriteProtected) {
            for (int i = 0; i < ROM_SIZE; i++) {
                rom[i] = 0;
            }
        }

        // Очистка RAM
        for (int i = 0; i < RAM_SIZE; i++) {
            ram[i] = 0;
        }

        // Очистка портов
        for (int i = 0; i < PORTS_SIZE; i++) {
            ports[i] = 0;
        }

        logger.debug("Memory cleared");
    }

    /**
     * Загружает ROM из массива байтов
     */
    public void loadROM(byte[] romData) {
        if (romData == null) {
            throw new IllegalArgumentException("ROM data cannot be null");
        }

        if (romData.length != ROM_SIZE) {
            throw new IllegalArgumentException(
                    String.format("ROM size must be %d bytes, got %d", ROM_SIZE, romData.length)
            );
        }

        logger.info("Loading ROM data ({} bytes)", romData.length);

        // Временно снимаем защиту от записи для загрузки ROM
        boolean wasProtected = romWriteProtected;
        romWriteProtected = false;

        System.arraycopy(romData, 0, rom, 0, ROM_SIZE);

        // Восстанавливаем защиту
        romWriteProtected = wasProtected;

        logger.info("ROM loaded successfully");
    }

    /**
     * Читает байт из памяти
     */
    public int readByte(int address) {
        address &= 0xFFFF; // Маска для 16-битного адреса

        if (address >= ROM_START && address <= ROM_END) {
            // Чтение из ROM
            return rom[address] & 0xFF;
        } else if (address >= SCREEN_RAM_START && address <= USER_RAM_END) {
            // Чтение из RAM
            int ramAddress = address - SCREEN_RAM_START;
            return ram[ramAddress] & 0xFF;
        }

        logger.warn("Invalid memory read at address: 0x{}", Integer.toHexString(address).toUpperCase());
        return 0xFF; // Возвращаем значение по умолчанию
    }

    /**
     * Записывает байт в память
     */
    public void writeByte(int address, int value) {
        address &= 0xFFFF; // Маска для 16-битного адреса
        value &= 0xFF;     // Маска для 8-битного значения

        if (address <= ROM_END) {
            // Попытка записи в ROM
            if (romWriteProtected) {
                logger.debug("Attempted write to protected ROM at 0x{}",
                        Integer.toHexString(address).toUpperCase());
                return; // Игнорируем запись в защищенную ROM
            } else {
                rom[address] = (byte) value;
            }
        } else if (address >= SCREEN_RAM_START && address <= USER_RAM_END) {
            // Запись в RAM
            int ramAddress = address - SCREEN_RAM_START;
            ram[ramAddress] = (byte) value;
        } else {
            logger.warn("Invalid memory write at address: 0x{}",
                    Integer.toHexString(address).toUpperCase());
        }
    }

    /**
     * Читает 16-битное слово из памяти (little-endian)
     */
    public int readWord(int address) {
        int low = readByte(address);
        int high = readByte(address + 1);
        return (high << 8) | low;
    }

    /**
     * Записывает 16-битное слово в память (little-endian)
     */
    public void writeWord(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    /**
     * Читает блок данных из памяти
     */
    public byte[] readBlock(int startAddress, int length) {
        byte[] block = new byte[length];
        for (int i = 0; i < length; i++) {
            block[i] = (byte) readByte(startAddress + i);
        }
        return block;
    }

    /**
     * Записывает блок данных в память
     */
    public void writeBlock(int startAddress, byte[] data) {
        for (int i = 0; i < data.length; i++) {
            writeByte(startAddress + i, data[i] & 0xFF);
        }
    }

    /**
     * Получает прямой доступ к экранной памяти для видеосистемы
     */
    public byte[] getScreenMemory() {
        // Возвращаем копию первых 16K RAM (экранная память)
        byte[] screenMem = new byte[16384];
        System.arraycopy(ram, 0, screenMem, 0, 16384);
        return screenMem;
    }

    /**
     * Проверяет, находится ли адрес в области экранной памяти
     */
    public boolean isScreenMemory(int address) {
        return address >= SCREEN_RAM_START && address <= SCREEN_RAM_END;
    }

    /**
     * Проверяет, находится ли адрес в области ROM
     */
    public boolean isROM(int address) {
        return address >= ROM_START && address <= ROM_END;
    }

    /**
     * Проверяет, находится ли адрес в области RAM
     */
    public boolean isRAM(int address) {
        return address >= SCREEN_RAM_START && address <= USER_RAM_END;
    }

    /**
     * Дамп памяти для отладки
     */
    public void dumpMemory(int startAddress, int length) {
        logger.debug("Memory dump from 0x{} (length: {})",
                Integer.toHexString(startAddress).toUpperCase(), length);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i += 16) {
            sb.setLength(0);
            sb.append(String.format("%04X: ", startAddress + i));

            // Hex values
            for (int j = 0; j < 16 && (i + j) < length; j++) {
                int value = readByte(startAddress + i + j);
                sb.append(String.format("%02X ", value));
            }

            // Padding
            for (int j = length - i; j < 16; j++) {
                sb.append("   ");
            }

            sb.append(" ");

            // ASCII representation
            for (int j = 0; j < 16 && (i + j) < length; j++) {
                int value = readByte(startAddress + i + j);
                char c = (value >= 32 && value <= 126) ? (char) value : '.';
                sb.append(c);
            }

            logger.debug(sb.toString());
        }
    }

    public void setRomWriteProtected(boolean writeProtected) {
        this.romWriteProtected = writeProtected;
        logger.debug("ROM write protection: {}", writeProtected ? "enabled" : "disabled");
    }

    public int getRomSize() {
        return ROM_SIZE;
    }

    public int getRamSize() {
        return RAM_SIZE;
    }

    public int getTotalMemorySize() {
        return TOTAL_MEMORY;
    }

    @Override
    public void setRam(byte[] ram) {
        //z80Ram = ram;
    }

    @Override
    public void setPorts(byte[] ports) {
        //z80Ram = ports;
    }

    @Override
    public int fetchOpcode(int address) {
        // 3 clocks to fetch opcode from RAM and 1 execution clock
        tStates += 4;
        return readByte(address) & 0xff;
    }

    @Override
    public int peek8(int address) {
        tStates += 3; // 3 clocks for read byte from RAM
        return readByte(address) & 0xff;
    }

    @Override
    public void poke8(int address, int value) {
        tStates += 3; // 3 clocks for write byte to RAM
        writeByte(address, (byte) value);
    }

    @Override
    public int peek16(int address) {
        int lsb = peek8(address);
        int msb = peek8(address + 1);
        return (msb << 8) | lsb;
    }

    @Override
    public void poke16(int address, int word) {
        poke8(address, word);
        poke8(address + 1, word >>> 8);
    }

    @Override
    public int inPort(int port) {
        tStates += 4; // 4 clocks for read byte from bus
        return ports[port] & 0xff;
    }

    @Override
    public void outPort(int port, int value) {
        tStates += 4; // 4 clocks for write byte to bus
        ports[port] = (byte) value;
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        // Additional clocks to be added on some instructions
        // Not to be changed, really.
        this.tStates += tstates;
    }

    @Override
    public void interruptHandlingTime(int tstates) {
        // Additional clocks to be added on INT & NMI
        // Not to be changed, really.
        this.tStates += tstates;
    }

    @Override
    public boolean isActiveINT() {
        return false;
    }

    @Override
    public long gettStates() {
        return tStates;
    }

    @Override
    public void reset() {
        tStates = 0;
    }

}