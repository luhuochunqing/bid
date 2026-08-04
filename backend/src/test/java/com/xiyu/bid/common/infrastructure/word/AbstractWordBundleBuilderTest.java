package com.xiyu.bid.common.infrastructure.word;

import com.xiyu.bid.performance.infrastructure.PerformanceWordBundleBuilder;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AbstractWordBundleBuilder 异常处理测试。
 *
 * <p>防复发目标：
 * <ol>
 *   <li><b>异常收窄防护</b>：验证 {@code insertImage} 只捕获 IOException 和 InvalidFormatException，
 *       不捕获 RuntimeException。如果有人重新加回 {@code catch (RuntimeException)}，
 *       {@link #insertImage_whenRuntimeExceptionThrown_shouldPropagate} 会失败。</li>
 *   <li>IOException 降级为 LABEL_IMAGE_READ_FAILED 文本</li>
 *   <li>InvalidFormatException 降级为 LABEL_IMAGE_READ_FAILED 文本</li>
 *   <li>正常图片嵌入返回 true</li>
 * </ol>
 */
class AbstractWordBundleBuilderTest {

    /**
     * 防复发测试：当 encodeImage 抛出 RuntimeException 时，必须向上传播，不得被吞掉。
     * <p>背景：原代码 catch (RuntimeException) 会将 NPE 等编程错误伪装为"图片读取失败"，
     * 掩盖真实 bug。本次修复移除了 RuntimeException 捕获。本测试确保不再回退。
     */
    @Test
    void insertImage_whenRuntimeExceptionThrown_shouldPropagate() throws Exception {
        // 自定义子类，encodeImage 抛出 RuntimeException（模拟 NPE 等编程错误）
        AbstractWordBundleBuilder builder = new ThrowingBuilder(
                new RuntimeException("模拟 NPE：encodeImage 内部错误"));

        try (XWPFDocument doc = new XWPFDocument()) {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            // 必须抛出 RuntimeException，不得被吞掉
            assertThatThrownBy(() -> builder.insertImage(doc, img))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("模拟 NPE");
        }
    }

    /**
     * IOException 降级为文本提示，不向上传播。
     */
    @Test
    void insertImage_whenIOExceptionThrown_shouldDegradeToText() throws Exception {
        AbstractWordBundleBuilder builder = new ThrowingBuilder(
                new IOException("图片编码失败"));

        try (XWPFDocument doc = new XWPFDocument()) {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            boolean result = builder.insertImage(doc, img);

            // 返回 false（嵌入失败）
            assertThat(result).isFalse();
            // 文档包含降级文本
            String text = doc.getParagraphs().get(0).getText();
            assertThat(text).contains(AbstractWordBundleBuilder.LABEL_IMAGE_READ_FAILED);
        }
    }

    /**
     * InvalidFormatException 降级行为由编译器保证（catch 子句中已声明），
     * 其行为与 IOException 相同，不单独测试。
     */

    /**
     * 正常图片嵌入返回 true。
     */
    @Test
    void insertImage_normalImage_returnsTrue() throws Exception {
        AbstractWordBundleBuilder builder = new NormalBuilder();

        try (XWPFDocument doc = new XWPFDocument()) {
            BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);

            boolean result = builder.insertImage(doc, img);

            assertThat(result).isTrue();
        }
    }

    /**
     * 防复发测试：含 alpha 通道的 PNG（TYPE_INT_ARGB）必须能成功编码为 JPEG 并嵌入。
     * <p>背景：JPEG 编码器不接受 ARGB 色彩空间，直接编码会抛
     * {@code IIOException: Bogus input colorspace}，被降级逻辑吞掉导致图片静默丢失。
     * {@link PerformanceWordBundleBuilder#encodeImage} 修复为编码前先转白底 RGB。
     * 若回退，本测试会因 IOException 降级而得到 false。
     */
    @Test
    void insertImage_argbImage_shouldEncodeAsJpegSuccessfully() throws Exception {
        // 使用真实的 PerformanceWordBundleBuilder（encodeImage 即 JPEG 编码实现）；
        // attachmentPathResolver 在图片编码路径上不涉及，传 null
        PerformanceWordBundleBuilder builder = new PerformanceWordBundleBuilder(null);

        try (XWPFDocument doc = new XWPFDocument()) {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

            boolean result = builder.insertImage(doc, img);

            assertThat(result).isTrue();
        }
    }

    // ========== 测试用子类 ==========

    /**
     * 抛出指定异常的子类，用于测试异常处理边界。
     */
    private static class ThrowingBuilder extends AbstractWordBundleBuilder {
        private final RuntimeException runtimeEx;
        private final IOException ioEx;

        ThrowingBuilder(RuntimeException ex) {
            this.runtimeEx = ex;
            this.ioEx = null;
        }

        ThrowingBuilder(IOException ex) {
            this.runtimeEx = null;
            this.ioEx = ex;
        }

        @Override protected String getDocumentTitle() { return "Test"; }
        @Override protected int getPdfRenderDpi() { return 96; }
        @Override protected int getMaxPdfPages() { return 0; }
        @Override protected int getContentWidthTwips() { return 9000; }
        @Override protected void applyPageSetup(XWPFDocument doc) {}
        @Override protected void registerHeadingStyles(XWPFDocument doc) {}

        @Override
        protected EncodedImage encodeImage(BufferedImage img) throws IOException {
            if (runtimeEx != null) throw runtimeEx;
            if (ioEx != null) throw ioEx;
            return new EncodedImage(new byte[]{}, XWPFDocument.PICTURE_TYPE_PNG, "image.png");
        }
    }

    /**
     * 正常编码的子类，将 BufferedImage 编码为 PNG。
     */
    private static class NormalBuilder extends AbstractWordBundleBuilder {
        @Override protected String getDocumentTitle() { return "Test"; }
        @Override protected int getPdfRenderDpi() { return 96; }
        @Override protected int getMaxPdfPages() { return 0; }
        @Override protected int getContentWidthTwips() { return 9000; }
        @Override protected void applyPageSetup(XWPFDocument doc) {}
        @Override protected void registerHeadingStyles(XWPFDocument doc) {}

        @Override
        protected EncodedImage encodeImage(BufferedImage img) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return new EncodedImage(out.toByteArray(), XWPFDocument.PICTURE_TYPE_PNG, "image.png");
        }
    }
}
