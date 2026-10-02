package br.com.taas.saas.gestaoproducao.operations.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.PdfDocumentProcessor;
import br.com.taas.saas.gestaoproducao.operations.application.importing.PdfProcessingException;
import br.com.taas.saas.gestaoproducao.operations.infrastructure.integration.pdf.PdfBoxDocumentProcessor;

class PdfBoxDocumentProcessorTests {
    private final PdfBoxDocumentProcessor processor = new PdfBoxDocumentProcessor();

    @Test
    void extractsDigitalTextWithoutRenderingAnOcrPage() throws IOException {
        var result = processor.process(pdfWithText("Invoice item total"));

        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.pages().getFirst().extractedText()).contains("Invoice item total");
        assertThat(result.pagesRequiringOcr()).isEmpty();
    }

    @Test
    void rendersOnlyScannedPagesOfMixedPdfAsPng() throws IOException {
        var result = processor.process(mixedPdf());

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.pages().get(0).extractedText()).contains("digital page");
        assertThat(result.pages().get(0).ocrImagePng()).isNull();
        assertThat(result.pages().get(1).requiresOcr()).isTrue();
        assertThat(result.pages().get(1).ocrImagePng()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(result.pagesRequiringOcr()).extracting(page -> page.pageNumber()).containsExactly(2);
    }

    @Test
    void rejectsPdfWithMoreThanFivePagesBeforeProcessing() throws IOException {
        assertThatThrownBy(() -> processor.process(pdfWithPages(PdfDocumentProcessor.MAX_PAGES + 1,
                PDRectangle.A4)))
                .isInstanceOf(PdfProcessingException.class)
                .hasMessage("PDF could not be processed within the configured limits");
    }

    @Test
    void rejectsMalformedPdfWithoutLeakingParserDetails() {
        assertThatThrownBy(() -> processor.process("%PDF-not a valid document".getBytes()))
                .isInstanceOf(PdfProcessingException.class)
                .hasNoCause();
    }

    @Test
    void rejectsOversizedPageBeforeAllocatingRenderedImage() throws IOException {
        assertThatThrownBy(() -> processor.process(pdfWithPages(1, new PDRectangle(10_000, 10_000))))
                .isInstanceOf(PdfProcessingException.class);
    }

    @Test
    void rejectsPageWhoseExtractedTextExceedsTheMemoryBound() throws IOException {
        String excessiveText = "x".repeat(PdfDocumentProcessor.MAX_EXTRACTED_CHARS_PER_PAGE + 1);

        assertThatThrownBy(() -> processor.process(pdfWithText(excessiveText)))
                .isInstanceOf(PdfProcessingException.class)
                .hasNoCause();
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 750);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] mixedPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage digitalPage = new PDPage(PDRectangle.A4);
            document.addPage(digitalPage);
            try (PDPageContentStream stream = new PDPageContentStream(document, digitalPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 750);
                stream.showText("digital page");
                stream.endText();
            }
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithPages(int pageCount, PDRectangle pageSize) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < pageCount; page++) document.addPage(new PDPage(pageSize));
            document.save(output);
            return output.toByteArray();
        }
    }
}
