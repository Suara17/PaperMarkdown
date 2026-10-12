import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GeneratePaperIcon {

    public static void main(String[] args) throws Exception {
        int[] sizes = {72, 96, 144, 192, 512};
        String[] folders = {
            "res/mipmap-hdpi",
            "res/mipmap-xhdpi",
            "res/mipmap-xxhdpi",
            "res/mipmap-xxxhdpi",
            "."
        };

        String baseDir = "/storage/emulated/0/Download/PaperMarkdown";

        // 首先渲染 1024x1024 超高精度原始母版
        int masterSize = 1024;
        BufferedImage master = renderMasterIcon(masterSize);

        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            BufferedImage scaled = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(master, 0, 0, s, s, null);
            g2.dispose();

            File outDir = new File(baseDir, folders[i]);
            outDir.mkdirs();
            String filename = (s == 512) ? "icon_paper_512.png" : "ic_launcher.png";
            File outFile = new File(outDir, filename);
            ImageIO.write(scaled, "PNG", outFile);
            System.out.println("Generated: " + outFile.getAbsolutePath() + " (" + s + "x" + s + ")");

            // 同时生成 ic_launcher_round.png 供部分启动器使用
            if (s != 512) {
                File roundFile = new File(outDir, "ic_launcher_round.png");
                ImageIO.write(scaled, "PNG", roundFile);
            }
        }
    }

    private static BufferedImage renderMasterIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. 整体底座：素雅温暖的暖米黄宣纸圆角容器
        int pad = 56;
        int boxSize = size - pad * 2;
        int corner = 220; // 现代圆角 Squircle
        RoundRectangle2D baseBox = new RoundRectangle2D.Float(pad, pad, boxSize, boxSize, corner, corner);

        // 底色渐变：暖玉白到柔和麦黄
        GradientPaint baseGrad = new GradientPaint(
            pad, pad, new Color(0xFC, 0xFA, 0xF2),
            pad + boxSize, pad + boxSize, new Color(0xEF, 0xE5, 0xD4)
        );
        g2.setPaint(baseGrad);
        g2.fill(baseBox);

        // 柔和微边框
        g2.setColor(new Color(0xDD, 0xD0, 0xBE, 180));
        g2.setStroke(new BasicStroke(7.0f));
        g2.draw(baseBox);

        // 2. 居中信笺阴影
        int paperW = 540;
        int paperH = 680;
        int paperX = (size - paperW) / 2;
        int paperY = (size - paperH) / 2 + 10;
        int paperRadius = 36;
        int foldSize = 130; // 右上角折角大小

        // 柔和弥散阴影
        for (int i = 8; i >= 1; i--) {
            int alpha = 4 * i;
            g2.setColor(new Color(60, 45, 30, alpha));
            g2.fill(new RoundRectangle2D.Float(
                paperX - i * 2, paperY + i * 3, paperW + i * 4, paperH + i * 2, paperRadius + 8, paperRadius + 8
            ));
        }

        // 3. 构建带右上折角的信笺主体 Path
        Path2D.Float paperPath = new Path2D.Float();
        paperPath.moveTo(paperX + paperRadius, paperY);
        paperPath.lineTo(paperX + paperW - foldSize, paperY);
        // 折角斜边
        paperPath.lineTo(paperX + paperW, paperY + foldSize);
        // 右下
        paperPath.lineTo(paperX + paperW, paperY + paperH - paperRadius);
        paperPath.quadTo(paperX + paperW, paperY + paperH, paperX + paperW - paperRadius, paperY + paperH);
        // 左下
        paperPath.lineTo(paperX + paperRadius, paperY + paperH);
        paperPath.quadTo(paperX, paperY + paperH, paperX, paperY + paperH - paperRadius);
        // 左上
        paperPath.lineTo(paperX, paperY + paperRadius);
        paperPath.quadTo(paperX, paperY, paperX + paperRadius, paperY);
        paperPath.closePath();

        // 填充白玉素笺表面
        GradientPaint paperGrad = new GradientPaint(
            paperX, paperY, new Color(0xFF, 0xFF, 0xFD),
            paperX, paperY + paperH, new Color(0xF7, 0xF3, 0xEB)
        );
        g2.setPaint(paperGrad);
        g2.fill(paperPath);

        // 信笺细淡轮廓线
        g2.setColor(new Color(0xDF, 0xD4, 0xC3));
        g2.setStroke(new BasicStroke(4.0f));
        g2.draw(paperPath);

        // 4. 右上角折角三角形阴影与折纸面
        Path2D.Float foldTriangle = new Path2D.Float();
        foldTriangle.moveTo(paperX + paperW - foldSize, paperY);
        foldTriangle.lineTo(paperX + paperW - foldSize, paperY + foldSize);
        foldTriangle.lineTo(paperX + paperW, paperY + foldSize);
        foldTriangle.closePath();

        // 折纸背面渐变（带有稍深的阴影纸色）
        GradientPaint foldGrad = new GradientPaint(
            paperX + paperW - foldSize, paperY, new Color(0xEB, 0xE2, 0xD2),
            paperX + paperW, paperY + foldSize, new Color(0xDC, 0xCE, 0xBA)
        );
        g2.setPaint(foldGrad);
        g2.fill(foldTriangle);

        // 折线描边
        g2.setColor(new Color(0xD2, 0xC3, 0xAF));
        g2.setStroke(new BasicStroke(3.5f));
        g2.draw(foldTriangle);

        // 5. 信笺上的素雅横向稿纸排版线 (3 条)
        int lineStartX = paperX + 70;
        int lineEndX = paperX + paperW - 70;
        int lineY1 = paperY + 230;
        int lineGap = 90;

        g2.setColor(new Color(0xE3, 0xD9, 0xC9));
        g2.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // 短装饰引导线
        g2.drawLine(lineStartX, lineY1 - 50, lineStartX + 120, lineY1 - 50);
        // 主横线
        g2.drawLine(lineStartX, lineY1, lineEndX, lineY1);
        g2.drawLine(lineStartX, lineY1 + lineGap, lineEndX - 80, lineY1 + lineGap);
        g2.drawLine(lineStartX, lineY1 + lineGap * 2, lineEndX, lineY1 + lineGap * 2);

        // 6. 核心视觉聚焦点：一枚古典温润的朱砂印章 (Vermilion Seal)
        // 放置在信笺中央略偏下方，犹如文人在素笺落款处加盖的书斋闲印
        int sealW = 210;
        int sealH = 150;
        int sealX = (size - sealW) / 2;
        int sealY = paperY + 390;
        int sealRadius = 24;

        RoundRectangle2D sealBox = new RoundRectangle2D.Float(sealX, sealY, sealW, sealH, sealRadius, sealRadius);

        // 朱砂红印泥渐变（沉稳而古雅的中国传统朱砂红）
        GradientPaint sealGrad = new GradientPaint(
            sealX, sealY, new Color(0xB8, 0x41, 0x24),
            sealX + sealW, sealY + sealH, new Color(0x8F, 0x2A, 0x12)
        );
        g2.setPaint(sealGrad);
        g2.fill(sealBox);

        // 朱砂印细描边
        g2.setColor(new Color(0xD8, 0x64, 0x48, 160));
        g2.setStroke(new BasicStroke(3.0f));
        g2.draw(new RoundRectangle2D.Float(sealX + 4, sealY + 4, sealW - 8, sealH - 8, sealRadius - 4, sealRadius - 4));

        // 7. 印章内部：纯净古雅的 Markdown 标志性符号「M ↓」
        g2.setColor(new Color(0xFC, 0xFA, 0xF5));
        g2.setStroke(new BasicStroke(12.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // 绘制 M 的经典典雅线条
        int mx = sealX + 40;
        int my = sealY + 35;
        int mw = 72;
        int mh = 80;

        Path2D.Float mPath = new Path2D.Float();
        mPath.moveTo(mx, my + mh);
        mPath.lineTo(mx, my);
        mPath.lineTo(mx + mw / 2, my + mh * 0.65f);
        mPath.lineTo(mx + mw, my);
        mPath.lineTo(mx + mw, my + mh);
        g2.draw(mPath);

        // 绘制 ↓ 向下箭头（代表 Markdown）
        int ax = sealX + 150;
        int ay = sealY + 35;
        int ah = 80;

        g2.drawLine(ax, ay + 4, ax, ay + ah);
        // 箭头两撇
        g2.drawLine(ax - 18, ay + ah - 22, ax, ay + ah);
        g2.drawLine(ax + 18, ay + ah - 22, ax, ay + ah);

        g2.dispose();
        return img;
    }
}