package com.resumerag.parser;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.resumerag.parser.strategy.DocumentParser;
import com.resumerag.parser.strategy.DocxParser;
import com.resumerag.parser.strategy.PdfParser;
import com.resumerag.parser.strategy.PlainTextParser;

@Service
public class DocumentParserService {

    private final PdfParser pdfParser = new PdfParser();
    private final DocxParser docxParser = new DocxParser();
    private final PlainTextParser plainTextParser = new PlainTextParser();

    public String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File name must not be null");
        }

        String extension = getExtension(filename).toLowerCase();
        DocumentParser parser = switch (extension) {
            case "pdf" -> pdfParser;
            case "docx" -> docxParser;
            case "txt" -> plainTextParser;
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };

        try (InputStream is = file.getInputStream()) {
            return parser.extractText(is);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("File has no extension: " + filename);
        }
        return filename.substring(dotIndex + 1);
    }
}
