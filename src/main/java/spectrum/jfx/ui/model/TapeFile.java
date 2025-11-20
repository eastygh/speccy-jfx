package spectrum.jfx.ui.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TapeFile {

    private String filePath;
    private String fileName;
    private TapeType type;
    private long fileSize;
    private LocalDateTime addedDate;
    private List<TapeSection> sections;
    private String description;

    public TapeFile() {
        this.sections = new ArrayList<>();
        this.addedDate = LocalDateTime.now();
    }

    public TapeFile(String filePath) {
        this();
        this.filePath = filePath;
        this.fileName = extractFileName(filePath);
        this.type = determineType(filePath);
    }

    private String extractFileName(String path) {
        if (path == null) return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private TapeType determineType(String path) {
        if (path == null) return TapeType.UNKNOWN;
        String extension = path.toLowerCase();
        if (extension.endsWith(".tap")) return TapeType.TAP;
        if (extension.endsWith(".tzx")) return TapeType.TZX;
        return TapeType.UNKNOWN;
    }

    public enum TapeType {
        TAP,
        TZX,
        UNKNOWN;

        public String getLocalizationKey() {
            return switch (this) {
                case TAP -> "tape.type.tap";
                case TZX -> "tape.type.tzx";
                case UNKNOWN -> "tape.type.unknown";
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
}