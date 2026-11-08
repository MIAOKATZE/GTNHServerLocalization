package ru.glitchless.serverlocalization.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minecraft .lang uses the first '=' as separator, without Java Properties escapes or continuations. */
public final class LanguageFileReader {
    private LanguageFileReader() {}

    public static Map<String, String> read(Reader reader) throws IOException {
        Map<String, String> translations = new LinkedHashMap<>();
        BufferedReader buffered = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        boolean firstLine = true;
        while ((line = buffered.readLine()) != null) {
            if (firstLine && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            firstLine = false;
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator >= 0) {
                translations.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return translations;
    }
}
