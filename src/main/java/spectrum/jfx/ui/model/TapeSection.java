package spectrum.jfx.ui.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

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

    public enum SectionType {
        HEADER("Заголовок"),
        DATA("Данные"),
        PROGRAM("Программа"),
        CODE("Код"),
        ARRAY("Массив"),
        PAUSE("Пауза"),
        TURBO_DATA("Турбо данные"),
        UNKNOWN("Неизвестно");

        private final String displayName;

        SectionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    @Override
    public String toString() {
        return String.format("%02d. %s (%s) - %d байт",
            index, title != null ? title : "Без названия", type.getDisplayName(), length);
    }
}