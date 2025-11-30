package spectrum.jfx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TapeSection {

    private int index;
    private String title;
    private SectionType type;
    private int length;
    private String description;
    private boolean isPlayable;
    private byte[] data; // Данные секции для HEX редактора

    public TapeSection() {
        // Конструктор по умолчанию для Jackson
    }

    public TapeSection(int index, String title, SectionType type, int length) {
        this.index = index;
        this.title = title;
        this.type = type;
        this.length = length;
        this.isPlayable = true;
        this.description = "";
    }

    @Getter
    public enum SectionType {
        HEADER,
        DATA,
        PROGRAM,
        CODE,
        ARRAY,
        PAUSE,
        TURBO_DATA,
        UNKNOWN;

        public String getLocalizationKey() {
            return switch (this) {
                case HEADER -> "section.type.header";
                case DATA -> "section.type.data";
                case PROGRAM -> "section.type.program";
                case CODE -> "section.type.code";
                case ARRAY -> "section.type.array";
                case PAUSE -> "section.type.pause";
                case TURBO_DATA -> "section.type.turboData";
                default -> "section.type.unknown";
            };
        }

        public String getDisplayName() {
            // Этот метод не должен использоваться во время сериализации
            // Используйте LocalizationManager.getInstance().getString(getLocalizationKey()) в UI
            return name();
        }

        @Override
        public String toString() {
            return name(); // Возвращаем enum name для безопасной сериализации
        }
    }

    @Override
    public String toString() {
        // Простое toString для сериализации - локализация должна происходить в UI
        String titleText = title != null ? title : "Untitled";
        return String.format("%02d. %s (%s) - %d bytes", index, titleText, type.name(), length);
    }
}