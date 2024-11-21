package org.apache.sshd.jp.imgrcgnze;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SURFFLANNMatchingHomography {


    public static void main(String[] args) throws IOException {
        // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        for (int i = 1; i <= 8; i++) {
            new SURFFLANNMatchingHomography().run(args, i);
        }
    }

    public void run(String[] args, int i) throws IOException {
        String dir = "D:\\workspace\\projects\\mina-sshd\\jump-server\\src\\main\\resources\\image\\test\\";
        String combined = dir + "combined" + i + ".png";
        String raw = dir + "contour.png";
        String object = dir + "object" + i + ".png";
        String scene = dir + "scene" + i + ".png";

        // 1. 读取物品图片和场景图片
        Mat objectImage = Imgcodecs.imread(object, Imgcodecs.IMREAD_GRAYSCALE);
        flushImage(objectImage, dir+"objectBinaryImage" + i + ".png");
        Mat sceneImage = Imgcodecs.imread(scene, Imgcodecs.IMREAD_GRAYSCALE);
        flushImage(sceneImage, dir+"sceneBinaryImage" + i + ".png");
//        HighGui.imshow("Result", sceneImage);
//        HighGui.waitKey(0);

        if (objectImage.empty() || sceneImage.empty()) {
            System.out.println("Failed to load images!");
            return;
        }

        // 2. 预处理（边缘检测）
        Mat kernel = Mat.ones(3, 3, CvType.CV_8UC1);
        Mat blurredObjectImage = new Mat();
        Imgproc.GaussianBlur(objectImage, blurredObjectImage, new Size(5, 5), 1.5);
        flushImage(blurredObjectImage, dir+"objectBlurredObjectImage" + i + ".png");

        Mat objectEdges = new Mat();
        Imgproc.Canny(blurredObjectImage, objectEdges, 100, 200);
        Mat dilatedObjectEdge = new Mat();
        Imgproc.dilate(objectEdges, dilatedObjectEdge, kernel, new Point(-1, -1), 1);
//        HighGui.imshow("objectEdges", dilatedObjectEdge);
//        HighGui.waitKey(0);

        Mat blurredSceneImage = new Mat();
        Imgproc.GaussianBlur(sceneImage, blurredSceneImage, new Size(5, 5), 1.5);
        flushImage(blurredSceneImage, dir+"sceneBlurredObjectImage" + i + ".png");
        Mat sceneEdges = new Mat();
        Imgproc.Canny(blurredSceneImage, sceneEdges, 100, 200);
        flushImage(sceneEdges, dir+"sceneEdgeWithGaussImage" + i + ".png");

//        HighGui.imshow("Result", sceneEdges);
//        HighGui.waitKey(0);

        // 3. 提取轮廓
        List<MatOfPoint> objectContours = new ArrayList<>();
        Imgproc.findContours(dilatedObjectEdge, objectContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat contourImage = Mat.zeros(objectImage.size(), CvType.CV_8UC3);
        List<MatOfPoint> contours = maxContour(objectContours);
        Imgproc.drawContours(contourImage, contours, -1, new Scalar(0, 255, 0), 1); // 绿色轮廓线
        flushImage(contourImage, dir+"objectContourWithGaussImage" + i + ".png");
//        HighGui.imshow("contourImage", contourImage);
//        HighGui.waitKey(0);

        // 4. 筛选物品的主轮廓（假设第一个轮廓为主轮廓）
        MatOfPoint objectContour = contours.get(0);
        double objectArea = Imgproc.contourArea(objectContour);
        log.info("object area:{}", objectArea);
        // ------------------------------------------------------//

        List<MatOfPoint> sceneContours = new ArrayList<>();
        Imgproc.findContours(sceneEdges, sceneContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        sceneContours = sceneContours.stream().filter(sc -> Imgproc.contourArea(sc) > 10).collect(Collectors.toList());

        // 5. 匹配场景中的轮廓
        DefaultKeyValue<DefaultKeyValue<Double, Double>, MatOfPoint> kv = new DefaultKeyValue<>(new DefaultKeyValue<>(Double.MAX_VALUE, Double.MAX_VALUE), null);
        for (MatOfPoint sceneContour : new ArrayList<>(sceneContours)) {
            // 轮廓再细分
            Mat sci = Mat.zeros(sceneImage.size(), CvType.CV_8UC1);
            Imgproc.drawContours(sci, Arrays.asList(sceneContour), -1, new Scalar(255), Core.FILLED);

            // 细分后的轮廓优化
            List<MatOfPoint> pointContours = new ArrayList<>();
            Imgproc.findContours(sci, pointContours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            for (MatOfPoint pc : new ArrayList<>(pointContours)) {
                Mat mask = Mat.zeros(sceneImage.size(), CvType.CV_8UC1);
                Imgproc.drawContours(mask, Arrays.asList(pc), -1, new Scalar(255), Core.FILLED);

//                Mat kernel = Mat.ones(3, 3, CvType.CV_8UC1);
                // Perform morphological operations
                for (int j = 0; j < 3; j++) {
                    Mat dilated = new Mat();
                    Imgproc.dilate(mask, dilated, kernel, new Point(-1, -1), j);

                    // Find new contours in dilated and eroded masks
                    List<MatOfPoint> dilatedContours = new ArrayList<>();
                    Imgproc.findContours(dilated, dilatedContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                    for (MatOfPoint c : dilatedContours) {
                        if (Imgproc.contourArea(c) > 0.75 * objectArea) {
                            compareScore(objectImage, objectContour, c, objectArea, kv);
                        }
                    }
                }

                for (int j = 0; j < 3; j++) {
                    Mat eroded = new Mat();
                    Imgproc.erode(mask, eroded, kernel, new Point(-1, -1), j);
                    List<MatOfPoint> erodedContours = new ArrayList<>();
                    Imgproc.findContours(eroded, erodedContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                    for (MatOfPoint c : erodedContours) {
                        if (Imgproc.contourArea(c) > 0.75 * objectArea) {
                            compareScore(objectImage, objectContour, c, objectArea, kv);
                        }
                    }
                }

                // 使用 matchShapes 计算轮廓相似度
                if (Imgproc.contourArea(pc) > 0.75 * objectArea) {
                    compareScore(objectImage, objectContour, pc, objectArea, kv);
                }
            }

            // 使用 matchShapes 计算轮廓相似度
            if (Imgproc.contourArea(sceneContour) > 0.75 * objectArea) {
                compareScore(objectImage, objectContour, sceneContour, objectArea, kv);
            }
        }

        // 获取相对位置
        Rect boundingBox = Imgproc.boundingRect(kv.getValue());
        System.out.println("Bounding Box: " + boundingBox.toString());
        // 包括：
        int x = boundingBox.x; // 左上角 x 坐标
        int y = boundingBox.y; // 左上角 y 坐标
        int width = boundingBox.width; // 矩形宽度
        int height = boundingBox.height; // 矩形高度
        log.info("match block position:({},{}), width:{}, height:{}", x, y, width, height);

        // 6. 可视化结果
        Mat resultImage = Imgcodecs.imread(scene); // 原始场景图用于显示
        if (kv.getValue() != null) {
            List<MatOfPoint> bestMatchList = new ArrayList<>();
            bestMatchList.add(kv.getValue());
            Imgproc.drawContours(resultImage, bestMatchList, -1, new Scalar(0, 255, 0), 1); // 绿色描绘匹配轮廓
        }
        Image bufferedImage = HighGui.toBufferedImage(resultImage);
        ImageIO.write((BufferedImage) bufferedImage, "png", new File(combined));
    }

    private void flushImage(Mat objectImage, String path) throws IOException {
        Image bufferedImage = HighGui.toBufferedImage(objectImage);
        ImageIO.write((BufferedImage) bufferedImage, "png", new File(path));
    }

    private void showPoint(MatOfPoint sceneContour, Mat objectImage) {
        Mat si = Mat.zeros(objectImage.size(), CvType.CV_8UC3);
        Imgproc.drawContours(si, Arrays.asList(sceneContour), -1, new Scalar(0, 255, 0), 1); // 绿色轮廓线
//        HighGui.imshow("target-contour", si);
//        HighGui.waitKey(0);

    }

    private void compareScore(Mat objectImage, MatOfPoint objectContour, MatOfPoint sceneContour, double objectArea, DefaultKeyValue<DefaultKeyValue<Double, Double>, MatOfPoint> kv) {

        double area = Imgproc.contourArea(sceneContour);
        double areaScore = Math.abs(area - objectArea) / Math.max(area, objectArea);
        double sharpScore = Imgproc.matchShapes(objectContour, sceneContour, Imgproc.CONTOURS_MATCH_I1, 0);

//        double score = 0.8*areaScore + 0.2*sharpScore;
        if (/*areaScore < kv.getKey().getKey() */sharpScore < kv.getKey().getValue()) {
            kv.getKey().setKey(areaScore);
            kv.getKey().setValue(sharpScore);
            kv.setValue(sceneContour);
        }

        log.info("scene mat of point area:{}, sharp score:{}", area, sharpScore);
        showPoint(sceneContour, objectImage);
    }

    private List<MatOfPoint> maxContour(List<MatOfPoint> contours) {
        MatOfPoint maxContour = contours.get(0);
        for (int i = 1; i < contours.size(); i++) {
            MatOfPoint c = contours.get(i);
            if (c.size().area() > maxContour.size().area()) {
                maxContour = c;
            }
        }
        return Arrays.asList(maxContour);
    }


}

