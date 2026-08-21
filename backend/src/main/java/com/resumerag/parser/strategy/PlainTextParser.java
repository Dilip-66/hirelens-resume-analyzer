package com.resumerag.parser.strategy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PlainTextParser implements DocumentParser {

    @Override
    public String extractText(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
