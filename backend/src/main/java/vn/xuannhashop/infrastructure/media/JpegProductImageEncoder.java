package vn.xuannhashop.infrastructure.media;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.inventory.application.ProductImageEncoder;
import vn.xuannhashop.business.shared.DomainException;

@Component
public class JpegProductImageEncoder implements ProductImageEncoder {
    private static final int MAX_SIDE = 1200;
    private static final long MAX_PIXELS = 64_000_000;

    @Override
    public byte[] toJpeg(byte[] image) {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw invalidImage();
                reader.setInput(stream, false, !format.equals("jpeg"));
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw DomainException.bad("Ảnh vượt quá 64 megapixel. Hãy giảm độ phân giải trước khi tải lên.");
                }
                int rotation = format.equals("jpeg") ? orientation(reader.getImageMetadata(0)) : 1;
                var read = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / MAX_SIDE);
                read.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, read);
                return encode(scale(decoded, rotation));
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw invalidImage();
        }
    }

    private BufferedImage scale(BufferedImage source, int orientation) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double ratio = Math.min(1d, (double) MAX_SIDE / Math.max(sourceWidth, sourceHeight));
        int width = Math.max(1, (int) Math.round(sourceWidth * ratio));
        int height = Math.max(1, (int) Math.round(sourceHeight * ratio));
        boolean swap = orientation >= 5;
        BufferedImage result = new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);
        var graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.transform(switch (orientation) {
                case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
                case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
                case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
                case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
                case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
                case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
                case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
                default -> new AffineTransform();
            });
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private byte[] encode(BufferedImage image) throws IOException {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var buffer = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(buffer)) {
            var params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.86f);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        if (buffer.size() > 2 * 1024 * 1024) throw invalidImage();
        return buffer.toByteArray();
    }

    private int orientation(javax.imageio.metadata.IIOMetadata metadata) {
        // The JDK JPEG reader exposes Exif in APP1's unknown marker bytes.
        if (metadata == null) return 1;
        var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
        var markers = tree.getElementsByTagName("unknown");
        for (int i = 0; i < markers.getLength(); i++) {
            var marker = (IIOMetadataNode) markers.item(i);
            if (!"225".equals(marker.getAttribute("MarkerTag"))) continue;
            if (!(marker.getUserObject() instanceof byte[] bytes) || bytes.length < 14) continue;
            var data = ByteBuffer.wrap(bytes);
            if (data.getInt(0) != 0x45786966 || data.getShort(4) != 0) continue;
            short order = data.getShort(6);
            if (order != 0x4949 && order != 0x4d4d) continue;
            data.order(order == 0x4949 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            if (data.getShort(8) != 42) continue;
            long start = 6L + Integer.toUnsignedLong(data.getInt(10));
            if (start < 14 || start + 2 > bytes.length) continue;
            int entries = Short.toUnsignedInt(data.getShort((int) start));
            for (int j = 0, offset = (int) start + 2; j < entries && offset + 12 <= bytes.length; j++, offset += 12) {
                if (data.getShort(offset) == 0x0112 && data.getShort(offset + 2) == 3 && data.getInt(offset + 4) == 1) {
                    int value = data.getShort(offset + 8);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
        }
        return 1;
    }

    private DomainException invalidImage() {
        return DomainException.bad("Tệp ảnh không đọc được. Hãy chọn ảnh JPEG hoặc PNG hợp lệ.");
    }
}
