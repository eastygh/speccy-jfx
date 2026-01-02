package spectrum.jfx.ui.localization;

import lombok.extern.slf4j.Slf4j;
import spectrum.jfx.ui.settings.AppSettings;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class LocalizationManager {

    public enum Language {
        ENGLISH("en", "English", Locale.ENGLISH),
        RUSSIAN("ru", "Русский", new Locale("ru", "RU"));

        private final String code;
        private final String displayName;
        private final Locale locale;

        Language(String code, String displayName, Locale locale) {
            this.code = code;
            this.displayName = displayName;
            this.locale = locale;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Locale getLocale() {
            return locale;
        }

        public static Language fromCode(String code) {
            for (Language lang : values()) {
                if (lang.code.equals(code)) {
                    return lang;
                }
            }
            return ENGLISH; // Default fallback
        }

        public static Language fromLocale(Locale locale) {
            for (Language lang : values()) {
                if (lang.locale.getLanguage().equals(locale.getLanguage())) {
                    return lang;
                }
            }
            return ENGLISH; // Default fallback
        }
    }

    private static LocalizationManager instance;
    private ResourceBundle resourceBundle;
    private Language currentLanguage;
    private final List<LocalizationChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface LocalizationChangeListener {
        void onLanguageChanged(Language newLanguage);
    }

    private LocalizationManager() {
        // Определяем язык из настроек или системной локали
        String savedLanguage = AppSettings.getInstance().getLanguage();
        if (savedLanguage != null && !savedLanguage.isEmpty()) {
            currentLanguage = Language.fromCode(savedLanguage);
        } else {
            // Автоопределение языка системы
            Locale systemLocale = Locale.getDefault();
            currentLanguage = Language.fromLocale(systemLocale);
            log.info("Auto-detected system language: {} -> {}", systemLocale, currentLanguage);
        }

        loadResourceBundle();
    }

    public static LocalizationManager getInstance() {
        if (instance == null) {
            instance = new LocalizationManager();
        }
        return instance;
    }

    public Language getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(Language language) {
        if (language != currentLanguage) {
            currentLanguage = language;
            loadResourceBundle();

            // Сохраняем выбор языка
            AppSettings.getInstance().setLanguage(language.getCode());
            AppSettings.getInstance().saveSettings();

            // Уведомляем слушателей
            notifyLanguageChanged();

            log.info("Language changed to: {}", language.getDisplayName());
        }
    }

    private void loadResourceBundle() {
        try {
            resourceBundle = ResourceBundle.getBundle("i18n/messages", currentLanguage.getLocale());
            log.info("Loaded resource bundle for locale: {}", currentLanguage.getLocale());
        } catch (MissingResourceException e) {
            log.warn("Could not load resource bundle for {}, using default", currentLanguage);
            resourceBundle = ResourceBundle.getBundle("i18n/messages");
        }
    }

    public String getString(String key) {
        try {
            return resourceBundle.getString(key);
        } catch (MissingResourceException e) {
            log.warn("Missing translation for key: {}", key);
            return "!" + key + "!"; // Show missing keys clearly
        }
    }

    public String getString(String key, Object... args) {
        try {
            String pattern = resourceBundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            log.warn("Missing translation for key: {}", key);
            return "!" + key + "!";
        }
    }

    public void addLanguageChangeListener(LocalizationChangeListener listener) {
        listeners.add(listener);
    }

    public void removeLanguageChangeListener(LocalizationChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyLanguageChanged() {
        for (LocalizationChangeListener listener : listeners) {
            try {
                listener.onLanguageChanged(currentLanguage);
            } catch (Exception e) {
                log.error("Error notifying language change listener", e);
            }
        }
    }

    // Convenience methods for common UI strings
    public String getMenuString(String menuKey) {
        return getString("menu." + menuKey);
    }

    public String getTapeString(String tapeKey) {
        return getString("tape." + tapeKey);
    }

    public String getButtonString(String buttonKey) {
        return getString("btn." + buttonKey);
    }

    public String getTooltipString(String tooltipKey) {
        return getString("tooltip." + tooltipKey);
    }

    public String getErrorString(String errorKey) {
        return getString("error." + errorKey);
    }

    public String getWarningString(String warningKey) {
        return getString("warning." + warningKey);
    }

    // Get all available languages
    public Language[] getAvailableLanguages() {
        return Language.values();
    }
}