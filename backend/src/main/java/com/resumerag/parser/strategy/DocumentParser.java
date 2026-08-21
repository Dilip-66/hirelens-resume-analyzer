package com.resumerag.parser.strategy;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentParser {
    String extractText(InputStream is) throws IOException;
}
