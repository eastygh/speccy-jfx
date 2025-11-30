package spectrum.jfx.ui.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import spectrum.jfx.model.TapeFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TapeCollection {

    private String name;
    private LocalDateTime createdDate;
    private LocalDateTime lastModified;
    private List<TapeFile> files;

    public TapeCollection() {
        this(""); // Пустое имя будет заменено на локализованное в UI
    }

    public TapeCollection(String name) {
        this.name = name;
        this.createdDate = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.files = new ArrayList<>();
    }

    public void addFile(TapeFile file) {
        if (file != null && !files.contains(file)) {
            files.add(file);
            updateLastModified();
        }
    }

    public void removeFile(TapeFile file) {
        if (files.remove(file)) {
            updateLastModified();
        }
    }

    public void removeFile(int index) {
        if (index >= 0 && index < files.size()) {
            files.remove(index);
            updateLastModified();
        }
    }

    public boolean containsFile(String filePath) {
        return files.stream()
            .anyMatch(file -> file.getFilePath().equals(filePath));
    }

    @JsonIgnore
    public int getTotalFiles() {
        return files.size();
    }

    @JsonIgnore
    public long getTotalSize() {
        return files.stream()
            .mapToLong(TapeFile::getFileSize)
            .sum();
    }

    @JsonIgnore
    public int getTotalSections() {
        return files.stream()
            .mapToInt(file -> file.getSections().size())
            .sum();
    }

    private void updateLastModified() {
        this.lastModified = LocalDateTime.now();
    }

    public void clear() {
        files.clear();
        updateLastModified();
    }
}