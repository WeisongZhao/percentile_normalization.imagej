package com.HIT.weisongzhao;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;

public class Process {
    static ImagePlus result;
    public static ProgressBarGUI progressGUI;
    boolean done=false;
    public static ImagePlus create(String title, int width, int height, int slices) {
        // 创建一个 ImageStack 对象
        ImageStack stack = new ImageStack(width, height);

        // 在 ImageStack 中添加指定数量的切片，初始值全部为0
        for (int i = 0; i < slices; i++) {
            FloatProcessor sliceProcessor = new FloatProcessor(width, height);
            stack.addSlice("", sliceProcessor);
        }

        // 使用 ImagePlus 的构造函数创建 ImagePlus 对象
        ImagePlus imagePlus = new ImagePlus(title, stack);

        // 返回创建的 ImagePlus 对象
        return imagePlus;
    }
    //clear_min clear_max,when input is stack scope,then must pass the calculated boundary
    public static ImagePlus process(String m,float clear_min,float clear_max,ImagePlus imp, float lowerBound, float upperBound) {
        // 检查输入的图像是否为空
        if (imp == null) {
            System.err.println("输入图像为空！");
            return null;
        }
        progressGUI=new ProgressBarGUI(0,imp.getNSlices());
        progressGUI.setVisible(true);
        result=create("result",imp.getWidth(),imp.getHeight(),imp.getNSlices());
        // 获取图像的类型（单张图像或堆栈）
        boolean isStack = imp.getStackSize() > 1;

        // 如果模式为'slice'，则对每个切片分别归一化
        if (m.equals("slice")) {
            if (isStack) {
                ImageStack stack = imp.getImageStack();
                for (int i = 1; i <=imp.getNSlices(); i++) {
                    progressGUI.updateProgress(i);
                    FloatProcessor ip = stack.getProcessor(i).convertToFloatProcessor();
                    normalize(ip,i, lowerBound, upperBound);

                }
            } else {
                normalize(imp.getProcessor().convertToFloatProcessor(), 1,lowerBound, upperBound);
            }
        }
        // 如果模式为'stack'，则在整个图像堆栈中找到上下界，并进行归一化
        else if (m.equals("stack")) {
            for (int i_ = 1; i_ <= imp.getNSlices(); i_++) {
                progressGUI.updateProgress(i_);
                ImageProcessor ip=imp.getStack().getProcessor(i_).convertToFloatProcessor();
                float scale = 1/ (clear_max - clear_min);
                float offset = - clear_min * scale;
                // 归一化图像
                ip.multiply(scale);
                ip.add(offset);
                ImageProcessor ipf=result.getStack().getProcessor(i_);
                float value=0;
                for(int i=0;i<ip.getHeight();i++){
                    for(int j=0;j<ip.getHeight();j++){
                        value=ip.getPixelValue(i,j);
                        if(value>0){
                            if(value<1){
                                ipf.putPixelValue(i,j,value);
                            }
                            else{
                                ipf.putPixelValue(i,j,1);
                                //pixel value>1
                            }
                        }else{
                            ipf.putPixelValue(i,j,0);
                            //pixel value<0
                        }
                    }
                }
            }
            }
        progressGUI.setVisible(false);
        result.setDisplayRange(result.getStack().getProcessor(1).getStatistics().min,result.getStack().getProcessor(1).getStatistics().max);
        // 返回归一化后的 ImagePlus 对象
//        result.show();
        save(result);
        return result;
    }

    // 将图像归一化到给定的下界和上界
    private static void normalize(FloatProcessor ip, int destSlice,float lowerBound, float upperBound) {
        // 获取图像统计信息
        ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MIN_MAX, null);
        int []a=new int[2];
        a=getPercentileBound(stats,lowerBound,upperBound);
        float min = (float)a[0];
        float max = (float)a[1];

        // 计算归一化参数
        float scale = 1/ (max - min);
        float offset = - min * scale;
        // 归一化图像
        ip.multiply(scale);
        ip.add(offset);
        ImageProcessor ipf=result.getStack().getProcessor(destSlice);
        ImageStatistics m=ip.getStatistics();
        float value=0;
        for(int i=0;i<ip.getHeight();i++){
            for(int j=0;j<ip.getHeight();j++){
                value=ip.getPixelValue(i,j);
                if(value>0){
                    if(value<1){
                        ipf.putPixelValue(i,j,value);
                    }
                    else{
                        ipf.putPixelValue(i,j,1);
                        //pixel value>1
                    }
                }else{
                    ipf.putPixelValue(i,j,0);
                    //pixel value<0
                }
            }
        }

    }

    private static int[] getPercentileBound(ImageStatistics stats, float percentile_low, float percentile_upper) {
        int[] histogram=stats.histogram;
        double min=stats.min;
        double max=stats.max;
        int [] ret=new int[2];
        int len=histogram.length;
        int threshold_low;
        int threshold_upper;

        int sum= Arrays.stream(histogram).reduce(0, (a, b) -> a + b);
        threshold_low= (int) (sum*percentile_low);
        threshold_upper= (int) (sum*percentile_upper);
        int count=0;
        for(int i=0;i<len;i++){
            count+=histogram[i];
            if(count>=threshold_low&&((count-histogram[i])<=threshold_low)){
                ret[0]=(int)((float)i/255*(max-min)+min);
            }
            else if(count>=threshold_upper&&((count-histogram[i])<=threshold_upper)){
                ret[1]=(int)((float)i/255*(max-min)+min);
                break;
            }
        }
        return ret;
    }
    public static void save(ImagePlus imp) {
        // 创建一个示例 ImagePlus 对象，这里假设你已经有了要保存的图像

        // 创建文件选择器
        JFileChooser fileChooser = new JFileChooser();

        // 设置默认保存路径
        fileChooser.setCurrentDirectory(new File("."));

        // 显示文件保存对话框
        int result = fileChooser.showSaveDialog(null);

        // 处理用户的选择
        if (result == JFileChooser.APPROVE_OPTION) {
            // 获取用户选择的文件
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();

            // 保存图像
            IJ.save(imp, filePath);
            System.out.println("图像保存成功：" + filePath);
        } else {
            System.out.println("用户取消了保存操作。");
        }
    }
    // 示例用法
    public static void main(String[] args) {
        // 假设你已经有了需要处理的ImagePlus对象和其他参数
        String m = "stack"; // 或 "stack"
        ImagePlus imp = getYourImagePlus(); // 获取你的ImagePlus对象的方法
        float lowerBound = 0.30f; // 下界
        float upperBound = 0.93f; // 上界
        // 调用 process 函数进行处理
        ImagePlus result = process(m, 0,0,imp, lowerBound, upperBound);

        // 显示结果
        if (result != null) {
            result.show();
        }
    }
    public static void main_(String[] args) {
        // 假设你需要创建一个 512x512 大小，通道数为3，切片数为10，帧数为5 的 ImagePlus 对象
        int width = 512;
        int height = 512;
        int slices = 10;

        // 创建 ImagePlus 对象
        ImagePlus result = create(" ",width, height, slices);

        // 显示结果
        result.show();
    }


    // 获取 ImagePlus 对象的方法
    private static ImagePlus getYourImagePlus() {
        // 在这里实现获取 ImagePlus 对象的逻辑
        // 返回你的 ImagePlus 对象
        ImagePlus image = IJ.openImage("D:\\SR\\Data\\3Ddata\\temporal data\\c1-405-2.tif");
        return image;
    }
}





class ProgressBarGUI extends JFrame {

    private JProgressBar progressBar;
    private JLabel label;
    int finish;

    public ProgressBarGUI(int n, int nn) {
        super("Progress Bar Example");
        finish=nn;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 150);

        // 创建进度条
        progressBar = new JProgressBar(0, nn);
        progressBar.setValue(n);
        progressBar.setStringPainted(true);

        // 创建标签显示进度信息
        label = new JLabel("Processing " + n + " out of " + nn);

        // 添加组件到界面
        setLayout(new BorderLayout());
        add(progressBar, BorderLayout.CENTER);
        add(label, BorderLayout.SOUTH);
    }

    public void updateProgress(int n) {
        progressBar.setValue(n);
        label.setText("Processing " + n + " out of " + progressBar.getMaximum());
        if(n==finish){
            this.setVisible(false);
        }
    }
}

