package com.resumerag.parser.strategy;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hwpf.extractor.WordExtractor;

/**
 * Legacy binary .doc (Word 97-2003) support. HWPF lives in poi-scratchpad rather
 * than poi-ooxml, which is why it needs its own dependency.
 */
public class DocParser implements DocumentParser {

    @Override
    public String extractText(InputStream is) throws IOException {
        WordExtractor extractor = new WordExtractor(is);
        return extractor.getText();
    }
}
