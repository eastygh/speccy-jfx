package spectrum.jfx.ui.model;

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
            switch (this) {
                case HEADER: return "section.type.header";
                case DATA: return "section.type.data";
                case PROGRAM: return "section.type.program";
                case CODE: return "section.type.code";
                case ARRAY: return "section.type.array";
                case PAUSE: return "section.type.pause";
                case TURBO_DATA: return "section.type.turboData";
                case UNKNOWN: return "section.type.unknown";
                default: return "section.type.unknown";
            }
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