package com.tools.main.controller;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.properties.AreaBreakType;

@CrossOrigin(origins = "https://snap-shift-552700783517.europe-west1.run.app")
@RestController
@RequestMapping("/api/convert")
public class ImageConverterController {

    // A list of supported output formats
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("jpeg", "jpg", "png", "bmp", "gif");
    private static final List<String> SUPPORTED_IMAGE_OUTPUT_FORMATS = Arrays.asList("png", "jpeg", "jpg");


    /**
     * Converts an uploaded image file to a specified target format.
     *
     * @param file The image file uploaded by the user.
     * @param toFormat The target format for the conversion (e.g., "jpeg", "png", "gif").
     * @return A ResponseEntity containing the converted image bytes or an error message.
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("toFormat") String toFormat) {

        // 1. Validate the target format
        String format = toFormat.toLowerCase();
        if (!SUPPORTED_FORMATS.contains(format)) {
            return ResponseEntity.badRequest().body(("Error: Unsupported output format '" + toFormat + "'. Supported formats are: " + SUPPORTED_FORMATS).getBytes());
        }

        // 2. Check if the file is empty
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Please upload a file.".getBytes());
        }

        try {
            // 3. Read the uploaded file into a BufferedImage
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(file.getBytes()));

            // Check if the file is a valid image that ImageIO can read
            if (sourceImage == null) {
                return ResponseEntity.badRequest().body("Error: The uploaded file is not a valid or supported image.".getBytes());
            }

            // 4. Handle transparency for formats that don't support it (like JPEG)
            BufferedImage outputImage = sourceImage;
            if (format.equals("jpeg") || format.equals("jpg") || format.equals("bmp")) {
                // Create a new BufferedImage with a white background
                outputImage = new BufferedImage(
                    sourceImage.getWidth(),
                    sourceImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB // This type does not have an alpha channel
                );
                // Draw the source image onto the new image, filling transparent areas with white
                outputImage.createGraphics().drawImage(sourceImage, 0, 0, Color.WHITE, null);
            }

            // 5. Write the BufferedImage to a byte array in the target format
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean success = ImageIO.write(outputImage, format, outputStream);

            if (!success) {
                 return ResponseEntity.internalServerError().body(("Error: Could not write to format '" + format + "'.").getBytes());
            }

            // 6. Build and return the successful response with the correct content type
            return ResponseEntity.ok()
                    .contentType(getMediaTypeForFormat(format))
                    .body(outputStream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error during image conversion.".getBytes());
        }
    }

    /**
     * Helper method to determine the correct MediaType for a given format string.
     *
     * @param format The image format (e.g., "jpeg", "png").
     * @return The corresponding MediaType.
     */
    private MediaType getMediaTypeForFormat(String format) {
        switch (format.toLowerCase()) {
            case "jpeg":
            case "jpg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "bmp":
                return MediaType.parseMediaType("image/bmp");
            default:
                // Fallback for any other case, though validation should prevent this
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
    /**
     * NEW METHOD: Converts an uploaded image into a single-page PDF document.
     *
     * @param file The image file uploaded by the user.
     * @return A ResponseEntity containing the PDF document bytes or an error message.
     */
    @PostMapping(value = "/image-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> imageToPdf(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("Error: Please upload at least one image file.".getBytes());
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            document.setMargins(20, 20, 20, 20);

            // Process each file
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];

                if (file.isEmpty() || !file.getContentType().startsWith("image/")) {
                    continue; // Skip invalid or empty files
                }

                // *** KEY CHANGE: Add a page break before adding the next image (but not before the first one) ***
                if (i > 0) {
                    document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                }

                // Create and configure the image
                Image pdfImage = new Image(ImageDataFactory.create(file.getBytes()));
                pdfImage.setAutoScale(true); // Scale image to fit within page margins

                // Add the image to the current page
                document.add(pdfImage);
            }

            document.close();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String pdfFilename = "converted_document.pdf";
            headers.setContentDispositionFormData("attachment", pdfFilename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error during PDF conversion.".getBytes());
        }
    }
    
    @PostMapping(value = "/pdf-to-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> pdfToImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format) {

        format = format.toLowerCase();
        if (!SUPPORTED_IMAGE_OUTPUT_FORMATS.contains(format)) {
            return ResponseEntity.badRequest().body(("Error: Unsupported output format '" + format + "'. Supported formats are: " + SUPPORTED_IMAGE_OUTPUT_FORMATS).getBytes());
        }

        if (file.isEmpty() || !"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.badRequest().body("Error: Please upload a valid PDF file.".getBytes());
        }

        try (final PDDocument document = PDDocument.load(file.getInputStream());
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); ++i) {
                // Render page to an image
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 300); // 300 DPI for good quality

                // Create a byte array output stream for the image
                ByteArrayOutputStream imageBaos = new ByteArrayOutputStream();
                ImageIO.write(bim, format, imageBaos);
                
                // Create a zip entry for the image
                String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
                String baseFilename = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
                ZipEntry zipEntry = new ZipEntry(baseFilename + "_page_" + (i + 1) + "." + format);
                zos.putNextEntry(zipEntry);
                zos.write(imageBaos.toByteArray());
                zos.closeEntry();
            }
            
            zos.finish(); // Finalize the zip file

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            String zipFilename = file.getOriginalFilename().substring(0, file.getOriginalFilename().lastIndexOf('.')) + ".zip";
            headers.setContentDispositionFormData("attachment", zipFilename);
            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error during PDF to Image conversion.".getBytes());
        }
    }
}
