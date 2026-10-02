package br.com.taas.saas.gestaoproducao.operations.infrastructure.integration.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import br.com.taas.saas.gestaoproducao.operations.application.importing.PdfPageContent;
import br.com.taas.saas.gestaoproducao.operations.application.importing.PdfProcessingException;
import br.com.taas.saas.gestaoproducao.operations.application.importing.PdfProcessingResult;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.PdfDocumentProcessor;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;

@Component
public final class PdfBoxDocumentProcessor implements PdfDocumentProcessor {
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    @Override
    public PdfProcessingResult process(byte[] originalPdf) {
        Objects.requireNonNull(originalPdf, "originalPdf must not be null");
        if (originalPdf.length == 0 || originalPdf.length > ImportDocument.MAX_FILE_BYTES
                || !hasPdfSignature(originalPdf)) {
            throw new PdfProcessingException();
        }

        try (PDDocument document = Loader.loadPDF(
                new RandomAccessReadBuffer(originalPdf), IOUtils.createTempFileOnlyStreamCache())) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1
                    || document.getNumberOfPages() > MAX_PAGES) {
                throw new PdfProcessingException();
            }
            return processPages(document);
        } catch (PdfProcessingException expected) {
            throw expected;
        } catch (IOException | RuntimeException malformedDocument) {
            throw new PdfProcessingException();
        }
    }

    private PdfProcessingResult processPages(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        renderer.setSubsamplingAllowed(true);
        PDFTextStripper textStripper = new PDFTextStripper();
        List<PdfPageContent> pages = new ArrayList<>(document.getNumberOfPages());
        int totalRenderedBytes = 0;

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            textStripper.setStartPage(pageIndex + 1);
            textStripper.setEndPage(pageIndex + 1);
            BoundedTextWriter textOutput = new BoundedTextWriter(MAX_EXTRACTED_CHARS_PER_PAGE);
            textStripper.writeText(document, textOutput);
            String extractedText = textOutput.value();
            if (!extractedText.isBlank()) {
                pages.add(new PdfPageContent(pageIndex + 1, extractedText, null));
                continue;
            }

            PDPage page = document.getPage(pageIndex);
            ensurePageDimensionsWithinLimit(page);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.GRAY);
            try {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", png)) throw new PdfProcessingException();
                byte[] imageBytes = png.toByteArray();
                totalRenderedBytes += imageBytes.length;
                if (totalRenderedBytes > MAX_RENDERED_BYTES) throw new PdfProcessingException();
                pages.add(new PdfPageContent(pageIndex + 1, "", imageBytes));
            } finally {
                image.flush();
            }
        }
        return new PdfProcessingResult(pages);
    }

    private static void ensurePageDimensionsWithinLimit(PDPage page) {
        PDRectangle cropBox = page.getCropBox();
        double widthPoints = cropBox.getWidth();
        double heightPoints = cropBox.getHeight();
        int rotation = Math.floorMod(page.getRotation(), 360);
        if (rotation == 90 || rotation == 270) {
            double swap = widthPoints;
            widthPoints = heightPoints;
            heightPoints = swap;
        }
        double widthPixels = Math.ceil(widthPoints * RENDER_DPI / 72.0);
        double heightPixels = Math.ceil(heightPoints * RENDER_DPI / 72.0);
        if (!Double.isFinite(widthPixels) || !Double.isFinite(heightPixels)
                || widthPixels < 1 || heightPixels < 1
                || widthPixels > MAX_PAGE_PIXELS || heightPixels > MAX_PAGE_PIXELS
                || widthPixels * heightPixels > MAX_PAGE_PIXELS) {
            throw new PdfProcessingException();
        }
    }

    private static boolean hasPdfSignature(byte[] content) {
        if (content.length < PDF_SIGNATURE.length) return false;
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (content[index] != PDF_SIGNATURE[index]) return false;
        }
        return true;
    }

    private static final class BoundedTextWriter extends Writer {
        private final int maximumCharacters;
        private final StringBuilder text = new StringBuilder();

        private BoundedTextWriter(int maximumCharacters) {
            this.maximumCharacters = maximumCharacters;
        }

        @Override
        public void write(char[] characters, int offset, int length) throws IOException {
            if (length < 0 || text.length() + length > maximumCharacters) {
                throw new IOException("Extracted PDF text limit exceeded");
            }
            text.append(characters, offset, length);
        }

        @Override public void flush() { }
        @Override public void close() { }
        private String value() { return text.toString(); }
    }
}
