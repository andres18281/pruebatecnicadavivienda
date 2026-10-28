package com.techdocs.worker.infrastructure;

import com.techdocs.worker.application.TextExtractorPort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@Component
public class TextExtractorAdapter implements TextExtractorPort {

    private static final int MAX_CHARS = 1_500_000;

    @Override
    public String extract(byte[] content, String mimeType, String fileName) {
        String text = "application/pdf".equals(mimeType) ? extractPdf(content) : extractText(content);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.length() > MAX_CHARS) {
            return text.substring(0, MAX_CHARS) + "\n\n[contenido truncado para indexacion]";
        }
        return text;
    }

    private static String extractPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pages = document.getNumberOfPages();
            StringBuilder builder = new StringBuilder();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                builder.append("--- página ").append(page).append(" ---\n");
                builder.append(stripper.getText(document).trim()).append("\n\n");
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo extraer el texto del PDF.", exception);
        }
    }

    private static String extractText(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }
}
