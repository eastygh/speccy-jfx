package spectrum.jfx.ui.util;

import spectrum.jfx.model.TapeFile;
import spectrum.jfx.model.TapeSection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TapeFileParser {

    /**
     * Парсит TAP или TZX файл и заполняет список секций
     */
    public static void parseTapeFile(TapeFile tapeFile) throws IOException {
        File file = new File(tapeFile.getFilePath());
        if (!file.exists()) {
            throw new IOException("File not found: " + tapeFile.getFilePath());
        }

        tapeFile.getSections().clear();

        switch (tapeFile.getType()) {
            case TAP:
                parseTapFile(tapeFile, file);
                break;
            case TZX:
                parseTzxFile(tapeFile, file);
                break;
            default:
                throw new IOException("Unsupported file type");
        }
    }

    /**
     * Парсит TAP файл
     */
    private static void parseTapFile(TapeFile tapeFile, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            int sectionIndex = 1;
            byte[] lengthBytes = new byte[2];

            while (fis.available() > 0) {
                // Читаем длину блока (2 байта, little endian)
                if (fis.read(lengthBytes) != 2) {
                    break;
                }

                int blockLength = (lengthBytes[1] & 0xFF) << 8 | (lengthBytes[0] & 0xFF);

                if (blockLength <= 0 || blockLength > fis.available()) {
                    break;
                }

                // Читаем первый байт для определения типа блока
                int firstByte = fis.read();
                if (firstByte == -1) break;

                TapeSection.SectionType sectionType;
                String title = "";

                // Создаем буфер для данных секции
                byte[] sectionData = new byte[blockLength];
                sectionData[0] = (byte) firstByte; // Первый байт уже прочитан

                if (firstByte == 0x00) {
                    // Заголовочный блок
                    sectionType = TapeSection.SectionType.HEADER;

                    if (blockLength >= 18) {
                        // Читаем тип программы
                        int programType = fis.read();
                        sectionData[1] = (byte) programType;

                        // Читаем имя файла (10 байт)
                        byte[] nameBytes = new byte[10];
                        fis.read(nameBytes);
                        System.arraycopy(nameBytes, 0, sectionData, 2, 10);
                        title = new String(nameBytes).trim().replaceAll("[\\x00-\\x1F]", "");

                        // Определяем более точный тип
                        switch (programType) {
                            case 0: sectionType = TapeSection.SectionType.PROGRAM; break;
                            case 1: sectionType = TapeSection.SectionType.ARRAY; break;
                            case 2: sectionType = TapeSection.SectionType.ARRAY; break;
                            case 3: sectionType = TapeSection.SectionType.CODE; break;
                        }

                        // Читаем оставшиеся байты блока
                        fis.read(sectionData, 12, blockLength - 12);
                    } else {
                        fis.read(sectionData, 1, blockLength - 1);
                    }
                } else {
                    // Блок данных
                    sectionType = TapeSection.SectionType.DATA;
                    title = "Data"; // Будет локализован в UI
                    fis.read(sectionData, 1, blockLength - 1);
                }

                if (title.isEmpty()) {
                    title = sectionType.name(); // Используем enum name, локализация в UI
                }

                TapeSection section = new TapeSection(sectionIndex++, title, sectionType, blockLength);
                section.setData(sectionData); // Сохраняем данные секции
                tapeFile.getSections().add(section);
            }
        }
    }

    /**
     * Парсит TZX файл (упрощенная версия)
     */
    private static void parseTzxFile(TapeFile tapeFile, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            // Читаем заголовок TZX
            byte[] signature = new byte[7];
            fis.read(signature);

            if (!"ZXTape!".equals(new String(signature))) {
                throw new IOException("Invalid TZX file format");
            }

            // Пропускаем EOF маркер и версию
            fis.skip(3);

            int sectionIndex = 1;

            while (fis.available() > 0) {
                int blockId = fis.read();
                if (blockId == -1) break;

                TapeSection.SectionType sectionType = TapeSection.SectionType.UNKNOWN;
                String title = "Block 0x" + String.format("%02X", blockId);
                int blockSize = 0;
                byte[] sectionData = null;

                switch (blockId) {
                    case 0x10: // Standard Speed Data Block
                        fis.skip(2); // Pause length
                        blockSize = fis.read() | (fis.read() << 8);
                        sectionType = TapeSection.SectionType.DATA;
                        title = "Standard Data"; // Будет локализован в UI

                        // Читаем данные блока
                        sectionData = new byte[blockSize];
                        fis.read(sectionData);
                        break;

                    case 0x11: // Turbo Speed Data Block
                        fis.skip(15); // Pilot, sync pulses, etc.
                        int dataLen1 = fis.read();
                        int dataLen2 = fis.read();
                        int dataLen3 = fis.read();
                        blockSize = dataLen1 | (dataLen2 << 8) | (dataLen3 << 16);
                        sectionType = TapeSection.SectionType.TURBO_DATA;
                        title = "Turbo Data"; // Будет локализован в UI

                        // Читаем данные блока
                        sectionData = new byte[blockSize];
                        fis.read(sectionData);
                        break;

                    case 0x20: // Pause (silence)
                        int pauseLen = fis.read() | (fis.read() << 8);
                        sectionType = TapeSection.SectionType.PAUSE;
                        title = "Pause (" + pauseLen + " ms)"; // Будет локализован в UI
                        blockSize = 0;
                        sectionData = new byte[0]; // Пустые данные для паузы
                        break;

                    case 0x30: // Text description
                        int textLen = fis.read();
                        byte[] textBytes = new byte[textLen];
                        fis.read(textBytes);
                        title = "Description: " + new String(textBytes); // Будет локализован в UI
                        blockSize = textLen;
                        sectionData = textBytes;
                        break;

                    default:
                        // Неизвестный блок, пытаемся его пропустить
                        // Многие блоки TZX имеют переменную длину, это упрощенная обработка
                        title = "Unknown block 0x" + String.format("%02X", blockId);

                        // Пытаемся прочитать длину блока из следующих байтов
                        if (fis.available() >= 4) {
                            int len = fis.read() | (fis.read() << 8) | (fis.read() << 16) | (fis.read() << 24);
                            if (len > 0 && len <= fis.available()) {
                                blockSize = len;
                                sectionData = new byte[len];
                                fis.read(sectionData);
                            } else {
                                // Если длина некорректная, выходим
                                break;
                            }
                        } else {
                            break;
                        }
                }

                TapeSection section = new TapeSection(sectionIndex++, title, sectionType, blockSize);
                section.setData(sectionData); // Сохраняем данные секции
                // Паузы нельзя воспроизводить отдельно
                section.setPlayable(sectionType != TapeSection.SectionType.PAUSE);
                tapeFile.getSections().add(section);
            }
        }
    }
}