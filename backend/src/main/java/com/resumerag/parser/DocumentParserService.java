package com.resumerag.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.resumerag.parser.strategy.DocParser;
import com.resumerag.parser.strategy.DocxParser;
import com.resumerag.parser.strategy.DocumentParser;
import com.resumerag.parser.strategy.PdfParser;
import com.resumerag.parser.strategy.PlainTextParser;

@Service
public class DocumentParserService {

    private final DocumentParser pdfParser = new PdfParser();
    private final DocumentParser docxParser = new DocxParser();
    private final DocumentParser docParser = new DocParser();
    private final DocumentParser plainTextParser = new PlainTextParser();

    public String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("File name must not be null");
        }

        String extension = getExtension(filename).toLowerCase(Locale.ROOT);
        DocumentParser parser = switch (extension) {
            case "pdf" -> pdfParser;
            case "docx" -> docxParser;
            case "doc" -> docParser;
            case "txt", "md", "text" -> plainTextParser;
            default -> throw new IllegalArgumentException(
                    "Unsupported file type: ." + extension + ". Upload a PDF, DOC, DOCX or TXT file.");
        };

        try (InputStream is = file.getInputStream()) {
            return parser.extractText(is);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException(
                    "File has no extension: " + filename + ". Upload a PDF, DOC, DOCX or TXT file.");
        }
        return filename.substring(dotIndex + 1);
    }
}
