package com.HIT.weisongzhao;

import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Random;

import ij.*;
import ij.plugin.frame.Recorder;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import net.imglib2.type.numeric.real.FloatType;

/** This plugin implements the Brightness/Contrast, Window/level and
 Color Balance commands, all in the Image/Adjust sub-menu. It
 allows the user to interactively adjust the brightness  and
 contrast of the active image. It is multi-threaded to
 provide a more  responsive user interface. */
public class Percentile_Normalization extends PlugInDialog implements Runnable,
        ActionListener, AdjustmentListener, ItemListener {

    public static final String LOC_KEY = "b&c.loc";
    public static final String[] sixteenBitRanges = {"Automatic", "8-bit (0-255)", "10-bit (0-1023)",
            "12-bit (0-4095)", "14-bit (0-16383)", "15-bit (0-32767)", "16-bit (0-65535)"};
    static final int AUTO_THRESHOLD = 5000;

    HistogramPlot plot = new HistogramPlot();
    Thread thread;
    private static Percentile_Normalization instance;
//    Percentile_Normalization_Utils<FloatType> processor=new Percentile_Normalization_Utils<FloatType>();
    int sliderRange = 1001;
    boolean doAutoAdjust,doReset,doSet, doSnapshot,doApplyLut,doRefresh;
    boolean mode=false;//default state is the slice scope
    Panel panel, tPanel;
    Button resetB, snapshotB,refreshB,applyB,setB;
    int previousImageID;
    int previousType;
    int previousSlice = 1;
    ImageJ ij;
    boolean change_occur=true;
    double min, max;
    double defaultMin, defaultMax;
    //lxl-add follow the name style of "defaultMin, defaultMax"
    int previousUpperBound=sliderRange-1,previousLowerBound=-1;
    //lxl-add  全部都是1000倍
    int upperBound=1000,lowerBound=0;
    boolean RGBImage;
    Scrollbar lowerBoundSlider, upperBoundSlider;
    Label minLabel, maxLabel, windowLabel, levelLabel,lowerBoundValueLabel,upperBoundValueLabel;
    boolean done=false;
    //the num of max slice num to calc when mode is 3d
    int maxslice=100;
    int autoThreshold;
    GridBagLayout gridbag;
    GridBagConstraints c;
    ImagePlus copy;
    int y = 0;
    Process processor=new Process();
    boolean windowLevel, balance;
    Font monoFont = new Font("Monospaced", Font.PLAIN, 11);
    Font sanFont = IJ.font12;
    int channels = 7; // RGB
    Choice choice_3D_2D;
    private String blankLabel8 = "--------";
    private String blankLabel12 = "------------";
    private double scale = Prefs.getGuiScale();
    private int digits;

    public Percentile_Normalization() {
        super("Percentile_Norm");
    }

    public void run(String arg) {
        windowLevel = arg.equals("wl");
        balance = arg.equals("balance");
        if (windowLevel)
            ij.setTitle("W&L");
        else if (balance) {
            setTitle("Color");
            channels = 4;
        }

        if (instance!=null) {
            if (!instance.getTitle().equals(getTitle())) {
                Percentile_Normalization ca = instance;
                Prefs.saveLocation(LOC_KEY, ca.getLocation());
                ca.close();
            } else {
                instance.toFront();
                return;
            }
        }
        instance = this;
        IJ.register(Percentile_Normalization.class);
        WindowManager.addWindow(this);

        ij = IJ.getInstance();
        gridbag = new GridBagLayout();
        c = new GridBagConstraints();
        setLayout(gridbag);
        if (scale>1.0) {
            sanFont = sanFont.deriveFont((float)(sanFont.getSize()*scale));
            monoFont = monoFont.deriveFont((float)(monoFont.getSize()*scale));
        }

        // plot
        c.gridx = 0;
        y = 0;
        c.gridy = y++;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(10, 10, 0, 10);
        gridbag.setConstraints(plot, c);
        add(plot);
        plot.addKeyListener(ij);//没看明白在干什么 question

        if (!windowLevel) {
            panel = new Panel();
            c.gridy = y++;
            c.insets = new Insets(0, 10, 0, 10);
            gridbag.setConstraints(panel, c);
            panel.setLayout(new BorderLayout());
            minLabel = new Label(blankLabel8, Label.LEFT);
            minLabel.setFont(monoFont);
            if (IJ.debugMode) minLabel.setBackground(Color.yellow);
            panel.add("West", minLabel);
            maxLabel = new Label(blankLabel8, Label.RIGHT);
            maxLabel.setFont(monoFont);
            if (IJ.debugMode) maxLabel.setBackground(Color.yellow);
            panel.add("East", maxLabel);
            add(panel);
            blankLabel8 = "        ";
        }

        // min slider
        if (!windowLevel) {
            // 创建一个新的Panel来容纳滑块和标签
            Panel sliderPanel = new Panel();
            sliderPanel.setLayout(new GridBagLayout());

            // 设置滑块的约束条件
            lowerBoundSlider = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, sliderRange);
            GUI.fixScrollbar(lowerBoundSlider);
            GridBagConstraints sliderConstraints = new GridBagConstraints();
            sliderConstraints.gridx = 0;
            sliderConstraints.gridy = 0;
            sliderConstraints.weightx = 0.9;
            sliderConstraints.fill = GridBagConstraints.HORIZONTAL;
            sliderConstraints.insets = new Insets(2, 10, 0, 0); // 右边距设为0，因为标签会紧随其后
            sliderPanel.add(lowerBoundSlider, sliderConstraints);
            lowerBoundSlider.setBlockIncrement(10);
            lowerBoundSlider.setUnitIncrement(1);

            // 设置标签的约束条件
            lowerBoundValueLabel = new Label("0.0%", Label.CENTER);
            lowerBoundValueLabel.setFont(monoFont);
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 1;
            labelConstraints.gridy = 0;
            labelConstraints.weightx=0.1;
            labelConstraints.insets = new Insets(2, 5, 0, 10); // 左边距设为5，与滑块之间留出一些空间
            sliderPanel.add(lowerBoundValueLabel, labelConstraints);

            // 将Panel添加到主布局中
            c.gridy = y++;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 0; // Panel整体不需要扩展
            c.gridwidth = GridBagConstraints.REMAINDER; // 占据剩余的所有列
            gridbag.setConstraints(sliderPanel, c);
            add(sliderPanel);
            // 添加滑块值变化的监听器
            lowerBoundSlider.addAdjustmentListener(this);
            addLabel("LowerBound_Percentile",null);
        }

        // max slider
        if (!windowLevel) {
            // 创建一个新的Panel来容纳滑块和标签
            Panel sliderPanel = new Panel();
            sliderPanel.setLayout(new GridBagLayout());

            // 设置滑块的约束条件
            upperBoundSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange, 1, 0, sliderRange);
            GUI.fixScrollbar(upperBoundSlider);
            GridBagConstraints sliderConstraints = new GridBagConstraints();
            sliderConstraints.gridx = 0;
            sliderConstraints.gridy = 0;
            sliderConstraints.weightx = 0.9;
            sliderConstraints.fill = GridBagConstraints.HORIZONTAL;
            sliderConstraints.insets = new Insets(2, 10, 0, 0); // 右边距设为0，因为标签会紧随其后
            sliderPanel.add(upperBoundSlider, sliderConstraints);
            upperBoundSlider.setBlockIncrement(10);
            upperBoundSlider.setUnitIncrement(1);
            // 设置标签的约束条件
            upperBoundValueLabel = new Label("100.0%", Label.CENTER);
            upperBoundValueLabel.setFont(monoFont);
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 1;
            labelConstraints.gridy = 0;
            labelConstraints.weightx=0.1;
            labelConstraints.insets = new Insets(2, 5, 0, 10); // 左边距设为5，与滑块之间留出一些空间
            sliderPanel.add(upperBoundValueLabel, labelConstraints);

            // 将Panel添加到主布局中
            c.gridy = y++;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 0; // Panel整体不需要扩展
            c.gridwidth = GridBagConstraints.REMAINDER; // 占据剩余的所有列
            gridbag.setConstraints(sliderPanel, c);
            add(sliderPanel);
            // 添加滑块值变化的监听器
            upperBoundSlider.addAdjustmentListener(this);
            addLabel("UpperBound_Percentile",null);
        }


        //  3D/2D mode
        if(!windowLevel){
            choice_3D_2D = new Choice();
            choice_3D_2D.addItem("Slice Scope");
            choice_3D_2D.addItem("Stack Scope");

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = y++;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 20, 0, 20);
            gbc.weightx = 1.0; // 这会让Choice组件在水平方向上扩展以填充可用空间

            gridbag.setConstraints(choice_3D_2D, gbc);
            choice_3D_2D.addItemListener(this);
            add(choice_3D_2D);
            addLabel("Mode", null);
        }



        // buttons
        if (scale>1.0) {
            Font font = getFont();
            if (font!=null)
                font = font.deriveFont((float)(font.getSize()*scale));
            else
                font = new Font("SansSerif", Font.PLAIN, (int)(12*scale));
            setFont(font);
        }
        int trim = IJ.isMacOSX()?20:0;
        panel = new Panel();
        panel.setLayout(new GridLayout(0,2, 0, 0));

        //SET TODO
//        snapshotB = new TrimmedButton("Snapshot",trim);
//        snapshotB.addActionListener(this);
//        snapshotB.addKeyListener(ij);
//        panel.add(snapshotB);
        //set TODO
        setB = new TrimmedButton("Set",trim);
        setB.addActionListener(this);
        setB.addKeyListener(ij);
        panel.add(setB);
        //Reset TODO
        resetB = new TrimmedButton("Reset",trim);
        resetB.addActionListener(this);
        resetB.addKeyListener(ij);
        panel.add(resetB);
        //refresh UI TODO
        refreshB=new TrimmedButton("Refresh",trim);
        refreshB.addActionListener(this);
        refreshB.addKeyListener(ij);
        panel.add(refreshB);
        //apply TODO
        applyB = new TrimmedButton("Apply",trim);
        applyB.addActionListener(this);
        applyB.addKeyListener(ij);
        panel.add(applyB);

        c.gridy = y++;
        c.insets = new Insets(8, 5, 10, 5);
        gridbag.setConstraints(panel, c);
        add(panel);

        addKeyListener(ij);  // ImageJ handles keyboard shortcuts
        pack();
        Point loc = Prefs.getLocation(LOC_KEY);
        if (loc!=null)
            setLocation(loc);
        else
            GUI.centerOnImageJScreen(this);
        if (IJ.isMacOSX()) setResizable(false);
        show();
        setup();
//        plot.calcBoundary((double) (lowerBound) / 1000, (double) (upperBound) / 1000);
//        Thread thread_=new Thread(()->{while(!done){setup();}},"check image update");
//        thread_.start();
//        copy=WindowManager.getCurrentImage().duplicate();
//        copy.show();
        thread = new Thread(this, "Percentile_Normalization");
        thread.start();
//        Thread thread__=new Thread(()->{while(!done){if(change_occur){
//            updatePlot();
//            ImagePlus imp=WindowManager.getCurrentImage();
//            updateLabels(imp);
//            if ((IJ.shiftKeyDown()||(balance&&channels==7)) && imp.isComposite())
//                ((CompositeImage)imp).updateAllChannelsAndDraw();
//            else
//                imp.updateChannelAndDraw();
//            if (RGBImage)
//                imp.unlock();
//            change_occur=false;
//        }
//        }},"update graph when any change happens");
//        thread__.start();

    }

    void setup() {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp!=null) {
            if (imp.getType()==ImagePlus.COLOR_RGB && imp.isLocked())
                return;
            setup(imp);
            //if no new imagej or no change happens,skip the following update
            if(!flag_for_setup&&!change_occur){
                return;
            }
            //when new image come then update the plot boundary
            plot.calcBoundary((double) (lowerBound) / 1000, (double) (upperBound) / 1000);
            updatePlot();
            updateLabels(imp);
            setMinAndMax(imp,plot.LowerBoundValue,plot.UpperBoundValue);
            imp.updateAndDraw();
            change_occur=false;
            flag_for_setup=false;
        }
    }
    void addLabel(String text, Label label2) {
        if (label2==null&&IJ.isMacOSX()) text += "    ";
        panel = new Panel();
        c.gridy = y++;
        int bottomInset = IJ.isMacOSX()?4:0;
        c.insets = new Insets(0, 10, bottomInset, 0);
        gridbag.setConstraints(panel, c);
        panel.setLayout(new FlowLayout(label2==null?FlowLayout.CENTER:FlowLayout.LEFT, 0, 0));
        Label label= new TrimmedLabel(text);
        label.setFont(sanFont);
        panel.add(label);
        if (label2!=null) {
            label2.setFont(monoFont);
            label2.setAlignment(Label.LEFT);
            panel.add(label2);
        }
        add(panel);
    }



    public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
        Object source = e.getSource();
        if (source== lowerBoundSlider)
            lowerBound = lowerBoundSlider.getValue();
        else if (source== upperBoundSlider)
            upperBound = upperBoundSlider.getValue();
        notify();
    }

    public synchronized  void actionPerformed(ActionEvent e) {
        Button b = (Button)e.getSource();
        if (b==null) return;
        if (b==resetB)
            doReset = true;
        else if (b== snapshotB)
            doSnapshot = true;
        else if(b==refreshB)
            doRefresh=true;
        else if(b==applyB)
            doApplyLut=true;
        else if(b==setB)
            doSet=true;
        notify();
    }
    static boolean flag_for_setup=false;
    static boolean previousMode=false;
    ImageProcessor setup(ImagePlus imp) {
        Roi roi = imp.getRoi();
        if (roi!=null) roi.endPaste();
        ImageProcessor ip = imp.getProcessor();
        int type = imp.getType();
        int slice = imp.getCurrentSlice();
        RGBImage = type==ImagePlus.COLOR_RGB;
        if (imp.getID()!=previousImageID || type!=previousType || slice!=previousSlice||(previousMode!=mode))
        {setupNewImage(imp, ip);
            flag_for_setup=true;}
        previousMode=mode;
        previousImageID = imp.getID();
        previousType = type;
        previousSlice = slice;
        return ip;
    }
    void setDefaultDisplayRange(ImagePlus imp, ImageProcessor ip){
        int bits = imp.getBitDepth();
        if(bits>16){
            if(plot.debug)
                IJ.log("this plugin only support 8 and 16 bit for stack scope currently");
        }

        long histogram[]=new long[(int)Math.pow(2,16)];
        for(int i=0;i<(int)Math.pow(2,16);i++){
            histogram[i]=0;
        }
        int nslice=imp.getNSlices();
        int height=imp.getHeight();
        int width=imp.getWidth();
        int[] randomNumbers;
        if(nslice>=maxslice){
            randomNumbers=new int[maxslice];
            for (int i = 0; i < maxslice; i++) {
                int random = getRandomNumber(1, nslice); // 在 [1, nslice) 范围内生成随机整数
                randomNumbers[i]=random;
            }
        }else{
            randomNumbers=new int[nslice];
            for(int i=0;i<10;i++){
                randomNumbers[i]=i;
            }
        }
        ImageStack stack=imp.getStack();
        //order for getVoxel width height slice
        for(int i_=0;i_<maxslice;i_++){
            int i=randomNumbers[i_];
            for(int j=0;j<width;j++){
                for(int k=0;k<height;k++){
                    int pixel=(int)(stack.getVoxel(j,k,i));
                    histogram[pixel]++;
                }
            }
        }
        int begin=fixhistogram(histogram)[0];
        int end=fixhistogram(histogram)[1];
        //TODO better and safer to use long list
        int []fixedhistogram=new int[end-begin+1];
        for(int i=0;i<fixedhistogram.length;i++){
            fixedhistogram[i]=(int)histogram[i+begin];
        }
        int index=0;
        int []finelhistogram=new int[256];
        for(int i=0;i<finelhistogram.length;i++){
            finelhistogram[i]=0;
        }
        for(int i=0;i<fixedhistogram.length;i++){
            index=(int)(((double)i)/fixedhistogram.length*256);
            finelhistogram[index]+=fixedhistogram[i];
        }
        plot.defaultMin=begin;
        defaultMin=begin;
        defaultMax=end;
        plot.defaultMax=end;
    }
//set up the defaultmin defaultmax and the histogram
    void setupNewImage(ImagePlus imp, ImageProcessor ip)  {
        if(plot.debug){
            IJ.log("set new image. SliceNum:"+imp.getCurrentSlice());}
        if(mode){
            //when a new imagej stack comes then update else return;
            //prepare for the reset
//            ip.snapshot();
            if(previousMode!=mode){
                //set the defaultmin defaultmax of plot and the whole histogram
                plotHistogram(imp);
            }
            return;
        }
            Undo.reset();
//            ip.snapshot();
            int bitDepth = imp.getBitDepth();
            if (bitDepth == 16 || bitDepth == 32) {
                Roi roi = imp.getRoi();
                imp.deleteRoi();
                ImageStatistics stats = imp.getRawStatistics();
                defaultMin = stats.min;
                defaultMax = stats.max;
                imp.setRoi(roi);
                setMinAndMax(imp,defaultMin,defaultMax);
            } else {
                defaultMin = 0;
                defaultMax = 255;
                setMinAndMax(imp,defaultMin,defaultMax);
            }
            plot.defaultMin = imp.getRawStatistics().min;
            plot.defaultMax = imp.getRawStatistics().max;
//            defaultMin= imp.getDisplayRangeMin();
//            defaultMax=imp.getDisplayRangeMax();
            if (!doReset)
                plotHistogram(imp);
            autoThreshold = 0;
            if (imp.isComposite())
                IJ.setKeyUp(KeyEvent.VK_SHIFT);
    }

    void updatePlot() {
        plot.repaint();
    }

    void updateLabels(ImagePlus imp) {
        double min = plot.defaultMin;
        double max = plot.defaultMax;
        int type = imp.getType();
        Calibration cal = imp.getCalibration();
        boolean realValue = type==ImagePlus.GRAY32;
        if (cal.calibrated()) {
            min = cal.getCValue((int)min);
            max = cal.getCValue((int)max);
            realValue = true;
        }
        if (windowLevel) {
            digits = realValue?3:0;
            double window = max-min;
            double level = min+(window)/2.0;
            windowLabel.setText(ResultsTable.d2s(window, digits));
            levelLabel.setText(ResultsTable.d2s(level, digits));
        } else {
            digits = realValue?4:0;
            if (realValue) {
                double s = min<0||max<0?0.1:1.0;
                double amin = Math.abs(min);
                double amax = Math.abs(max);
                if (amin>99.0*s||amax>99.0*s) digits = 3;
                if (amin>999.0*s||amax>999.0*s) digits = 2;
                if (amin>9999.0*s||amax>9999.0*s) digits = 1;
                if (amin>99999.0*s||amax>99999.0*s) digits = 0;
                if (amin>9999999.0*s||amax>9999999.0*s) digits = -2;
                if ((amin>0&&amin<0.001)||(amax>0&&amax<0.001)) digits = -2;
            }
            String minString = IJ.d2s(min, min==0.0?0:digits) + blankLabel8;
            minLabel.setText(minString.substring(0,blankLabel8.length()));
            String maxString = blankLabel8 + IJ.d2s(max, digits);
            maxString = maxString.substring(maxString.length()-blankLabel8.length(), maxString.length());
            maxLabel.setText(maxString);
        }
    }
//lxl-change  update the upper and lower bound
    void updateScrollBars(Scrollbar sb, boolean newRange,boolean left) {
        if (lowerBoundSlider !=null && (sb==null || sb!= lowerBoundSlider)) {
            if(lowerBound>=upperBound&&left){
                upperBound=lowerBound;
            } else if (lowerBound>=upperBound&&!left) {
                lowerBound=upperBound;
            }
            if (newRange)
                lowerBoundSlider.setValues(scaleDown(lowerBound), 1, 0,  sliderRange);
            else
                lowerBoundSlider.setValue(scaleDown(lowerBound));
        }
        if (upperBoundSlider !=null && (sb==null || sb!= upperBoundSlider)) {
            if (newRange)
                upperBoundSlider.setValues(scaleDown(upperBound), 1, 0,  sliderRange);
            else
                upperBoundSlider.setValue(scaleDown(upperBound));
        }
        lowerBoundValueLabel.setText(String.format("%.1f", (double)(lowerBoundSlider.getValue())/10)+'%');
        upperBoundValueLabel.setText(String.format("%.1f", (double)(upperBoundSlider.getValue())/10)+'%');
        plot.calcBoundary((double) (lowerBound) / 1000, (double) (upperBound) / 1000);
    }

    int scaleDown(double v) {
        if (v<0) v = 0;
        if (v>sliderRange) v = sliderRange;
        return (int)(v);
    }
    //update the copy of image
    void doSnapshot(ImagePlus imp, ImageProcessor ip){
        if(imp!=null){
            copy=imp.duplicate();
        }
    }
    void reset(ImagePlus imp, ImageProcessor ip) {
        if (RGBImage)
            ip.reset();
        int bitDepth = imp.getBitDepth();
        if (bitDepth==16 || bitDepth==32) {
            imp.resetDisplayRange();
            defaultMin = imp.getDisplayRangeMin();
            defaultMax = imp.getDisplayRangeMax();
            plot.defaultMin = defaultMin;
            plot.defaultMax = defaultMax;
        }
//        min = defaultMin;
//        max = defaultMax;
        upperBound=1000;
        lowerBound=0;
        updateScrollBars(null, false,true);
        plotHistogram(imp);
        autoThreshold = 0;
    }
    void setMinAndMax(ImagePlus imp, double min, double max) {
        boolean rgb = imp.getType()==ImagePlus.COLOR_RGB;
        if (channels!=7 && rgb)
            imp.setDisplayRange(min, max, channels);
        else
            imp.setDisplayRange(min, max);
        if (rgb)
            plotHistogram(imp);
    }
//把imp数据写入plot
    void plotHistogram(ImagePlus imp) {
        if(mode){
            int bits = imp.getBitDepth();
            if(bits>16){
                if(plot.debug)
                    IJ.log("this plugin only support 8 and 16 bit for stack scope currently");
            }

            long histogram[]=new long[(int)Math.pow(2,16)];
            for(int i=0;i<(int)Math.pow(2,16);i++){
                histogram[i]=0;
            }
            int nslice=imp.getNSlices();
            int height=imp.getHeight();
            int width=imp.getWidth();
            int[] randomNumbers;
            if(nslice>=maxslice){
                randomNumbers=new int[maxslice];
                for (int i = 0; i < maxslice; i++) {
                    int random = getRandomNumber(1, nslice); // 在 [1, nslice) 范围内生成随机整数
                    randomNumbers[i]=random;
                }
            }else{
                randomNumbers=new int[nslice];
                for(int i=0;i<10;i++){
                    randomNumbers[i]=i;
                }
            }
            ImageStack stack=imp.getStack();
            //order for getVoxel width height slice
            for(int i_=0;i_<maxslice;i_++){
                int i=randomNumbers[i_];
                for(int j=0;j<width;j++){
                    for(int k=0;k<height;k++){
                        int pixel=(int)(stack.getVoxel(j,k,i));
                        histogram[pixel]++;
                    }
                }
            }
            int begin=fixhistogram(histogram)[0];
            int end=fixhistogram(histogram)[1];
            //TODO better and safer to use long list
            int []fixedhistogram=new int[end-begin+1];
            for(int i=0;i<fixedhistogram.length;i++){
                fixedhistogram[i]=(int)histogram[i+begin];
            }
            int index=0;
            int []finelhistogram=new int[256];
            for(int i=0;i<finelhistogram.length;i++){
                finelhistogram[i]=0;
            }
            for(int i=0;i<fixedhistogram.length;i++){
                index=(int)(((double)i)/fixedhistogram.length*256);
                finelhistogram[index]+=fixedhistogram[i];
            }
            plot.defaultMin=begin;
            defaultMin=begin;
            defaultMax=end;
            plot.defaultMax=end;
            //for mode=1 the only way to update min and max
            plot.setHistogram(finelhistogram,Color.gray);
            return;
        }
        ImageStatistics stats;
        if (balance && (channels==4 || channels==2 || channels==1) && imp.getType()==ImagePlus.COLOR_RGB) {
            int w = imp.getWidth();
            int h = imp.getHeight();
            byte[] r = new byte[w*h];
            byte[] g = new byte[w*h];
            byte[] b = new byte[w*h];
            ((ColorProcessor)imp.getProcessor()).getRGB(r,g,b);
            byte[] pixels=null;
            if (channels==4)
                pixels = r;
            else if (channels==2)
                pixels = g;
            else if (channels==1)
                pixels = b;
            ImageProcessor ip = new ByteProcessor(w, h, pixels, null);
            stats = ImageStatistics.getStatistics(ip, 0, imp.getCalibration());
        } else {
            int range = imp.getType()==ImagePlus.GRAY16?ImagePlus.getDefault16bitRange():0;
            if (range!=0 && imp.getProcessor().getMax()==Math.pow(2,range)-1 && !(imp.getCalibration().isSigned16Bit())) {
                ImagePlus imp2 = new ImagePlus("Temp", imp.getProcessor());
                stats = new StackStatistics(imp2, 256, 0, Math.pow(2,range));
            } else
                stats = imp.getStatistics();
        }
        Color color = Color.gray;
        if (imp.isComposite() && !(balance&&channels==7))
            color = ((CompositeImage)imp).getChannelColor();
        plot.setHistogram(stats, color);
    }

    private int[] fixhistogram(long[] histogram) {
        int begin=0;
        int end= histogram.length;
        for(int i=0;i<histogram.length;i++){
            if(histogram[i]==0){
                continue;
            }
            begin=i;
            break;
        }
        for(int i=histogram.length-1;i>=0;i--){
            if(histogram[i]==0){
                continue;
            }
            end=i;
            break;
        }
        int a[]=new int[2];
        a[0]=begin;a[1]=end;
        return a;
    }

    private int getRandomNumber(int min, int max) {
            Random random = new Random();
            return random.nextInt(max - min) + min;
    }
    void save(ImagePlus imp){

    }
    void apply(ImagePlus imp, ImageProcessor ip){
        if (balance && imp.isComposite())
            return;
        int bitDepth = imp.getBitDepth();
//        if ((bitDepth==8||bitDepth==16) && !IJ.isMacro()) {
//            String msg = "WARNING: the pixel values will\nchange if you click \"OK\".";
//            if (!IJ.showMessageWithCancel("Apply Lookup Table?", msg))
//                return;
//        }
        String option = null;
        if (RGBImage)
            imp.unlock();
        if (!imp.lock())
            return;
        if (RGBImage) {
            if (imp.getStackSize()>1)
                applyRGBStack(imp);
            else
                applyRGB(imp,ip);
            return;
        }
        if (bitDepth==32) {
            IJ.beep();
            IJ.error("\"Apply\" does not work with 32-bit images");
            imp.unlock();
            return;
        }
//        float range = 1;
//        int tableSize = bitDepth==16?65536:256;
//        float[] table = new float[tableSize];
//        int min = (int)plot.LowerBoundValue;
//        int max = (int)plot.UpperBoundValue;
//        if (IJ.debugMode) IJ.log("Apply: mapping "+min+"-"+max+" to 0-"+(range));
//        for (int i=0; i<tableSize; i++) {
//            if (i<=min)
//                table[i] = 0;
//            else if (i>=max)
//                table[i] = range;
//            else
//                table[i] = (((float) (i-min)/(max-min))*range);
//        }
//        ip.setRoi(imp.getRoi());
        //slice is different from stack scope because the lut is not the same between slices
        if(!mode){
            ImageStack stack = imp.getStack();
            YesNoCancelDialog d = new YesNoCancelDialog(new Frame(),
                    "Entire Stack?", "Apply LUT to all "+stack.size()+" stack slices?");
            if (d.cancelPressed())
            {imp.unlock();
            }
            if (d.yesPressed()) {
                String m="slice";
                ImagePlus result=processor.process(m,0,0,imp,((float)lowerBound)/1000,(float) (upperBound)/1000);
            }
            else{
                ImagePlus imagePlus=new ImagePlus("slice",ip);
                String m="slice";
                ImagePlus result=processor.process(m,0,0,imagePlus,((float)lowerBound)/1000,(float) (upperBound)/1000);
               
            }
        }
        else if (mode) {
            ImageStack stack = imp.getStack();
            YesNoCancelDialog d = new YesNoCancelDialog(new Frame(),
                    "Entire Stack?", "Apply LUT to all "+stack.size()+" stack slices?");
            if (d.cancelPressed())
            {imp.unlock();
                ImagePlus imagePlus=new ImagePlus("stack",ip);
               }
            if (d.yesPressed()) {
                String m="stack";
                ImagePlus result=processor.process(m,plot.LowerBoundValue,plot.UpperBoundValue,imp,((float)lowerBound)/10,(float) (upperBound)/10);
            }
            else{
                ImagePlus imagePlus=new ImagePlus("slice",ip);
                String m="stack";
                ImagePlus result=processor.process(m,plot.LowerBoundValue,plot.UpperBoundValue,imagePlus,((float)lowerBound)/10,(float) (upperBound)/10);
           
            }
        }
        imp.unlock();
    }
    void apply_(ImagePlus imp, ImageProcessor ip) {
        if (balance && imp.isComposite())
            return;
        int bitDepth = imp.getBitDepth();
        if ((bitDepth==8||bitDepth==16) && !IJ.isMacro()) {
            String msg = "WARNING: the pixel values will\nchange if you click \"OK\".";
            if (!IJ.showMessageWithCancel("Apply Lookup Table?", msg))
                return;
        }
        String option = null;
        if (RGBImage)
            imp.unlock();
        if (!imp.lock())
            return;
        if (RGBImage) {
            if (imp.getStackSize()>1)
                applyRGBStack(imp);
            else
                applyRGB(imp,ip);
            return;
        }
        if (bitDepth==32) {
            IJ.beep();
            IJ.error("\"Apply\" does not work with 32-bit images");
            imp.unlock();
            return;
        }
        int range = 256;
        if (bitDepth==16) {
            range = 65536;
            int defaultRange = imp.getDefault16bitRange();
            if (defaultRange>0)
                range = (int)Math.pow(2,defaultRange)-1;
        }
        int tableSize = bitDepth==16?65536:256;
        int[] table = new int[tableSize];
        int min = (int)plot.LowerBoundValue;
        int max = (int)plot.UpperBoundValue;
        if (IJ.debugMode) IJ.log("Apply: mapping "+min+"-"+max+" to 0-"+(range-1));
        for (int i=0; i<tableSize; i++) {
            if (i<=min)
                table[i] = 0;
            else if (i>=max)
                table[i] = range-1;
            else
                table[i] = (int)(((double)(i-min)/(max-min))*range);
        }
        ip.setRoi(imp.getRoi());
        if (imp.getStackSize()>1 && !imp.isComposite()) {
            ImageStack stack = imp.getStack();
            YesNoCancelDialog d = new YesNoCancelDialog(new Frame(),
                    "Entire Stack?", "Apply LUT to all "+stack.size()+" stack slices?");
            if (d.cancelPressed())
            {imp.unlock(); return;}
            if (d.yesPressed()) {
                if (imp.getStack().isVirtual()) {
                    imp.unlock();
                    IJ.error("\"Apply\" does not work with virtual stacks. Use\nImage>Duplicate to convert to a normal stack.");
                    return;
                }
                int current = imp.getCurrentSlice();
                ImageProcessor mask = imp.getMask();
                for (int i=1; i<=imp.getStackSize(); i++) {
                    imp.setSlice(i);
                    ip = imp.getProcessor();
                    if (mask!=null) ip.snapshot();
                    ip.applyTable(table);
                    ip.reset(mask);
                }
                imp.setSlice(current);
                option = "stack";
            } else {
                ip.snapshot();
                ip.applyTable(table);
                ip.reset(ip.getMask());
                option = "slice";
            }
        } else {
            ip.snapshot();
            ip.applyTable(table);
            ip.reset(ip.getMask());
        }
        reset(imp, ip);
        imp.changes = true;
        imp.unlock();
        if (Recorder.record) {
            if (Recorder.scriptMode()) {
                if (option==null) option = "";
                Recorder.recordCall("IJ.run(imp, \"Apply LUT\", \""+option+"\");");
            } else {
                if (option!=null)
                    Recorder.record("run", "Apply LUT", option);
                else
                    Recorder.record("run", "Apply LUT");
            }
        }
    }

    void applyRGB(ImagePlus imp, ImageProcessor ip) {
        recordSetMinAndMax(ip.getMin(), ip.getMax());
        ip.snapshot();
        ip.setMinAndMax(0, 255);
        reset(imp, ip);
    }

    private void applyRGBStack(ImagePlus imp) {
        double min = imp.getDisplayRangeMin();
        double max = imp.getDisplayRangeMax();
        if (IJ.debugMode) IJ.log("applyRGBStack: "+min+"-"+max);
        int current = imp.getCurrentSlice();
        int n = imp.getStackSize();
        if (!IJ.showMessageWithCancel("Update Entire Stack?",
                "Apply brightness and contrast settings\n"+
                        "to all "+n+" slices in the stack?\n \n"+
                        "NOTE: There is no Undo for this operation."))
            return;
        ImageProcessor mask = imp.getMask();
        Rectangle roi = imp.getRoi()!=null?imp.getRoi().getBounds():null;
        ImageStack stack = imp.getStack();
        for (int i=1; i<=n; i++) {
            IJ.showProgress(i, n);
            IJ.showStatus(i+"/"+n);
            if (i!=current) {
                ImageProcessor ip = stack.getProcessor(i);
                ip.setRoi(roi);
                if (mask!=null) ip.snapshot();
                if (channels!=7)
                    ((ColorProcessor)ip).setMinAndMax(min, max, channels);
                else
                    ip.setMinAndMax(min, max);
                if (mask!=null) ip.reset(mask);
            }
        }
        imp.setStack(null, stack);
        imp.setSlice(current);
        imp.changes = true;
        previousImageID = 0;
        setup();
        if (Recorder.record) {
            if (Recorder.scriptMode())
                Recorder.recordCall("IJ.run(imp, \"Apply LUT\", \"stack\");");
            else
                Recorder.record("run", "Apply LUT", "stack");
        }
    }

    void autoAdjust(ImagePlus imp, ImageProcessor ip) {
        if (RGBImage)
            ip.reset();
        ImageStatistics stats = imp.getRawStatistics();
        int limit = stats.pixelCount/10;
        int[] histogram = stats.histogram;
        if (autoThreshold<10)
            autoThreshold = AUTO_THRESHOLD;
        else
            autoThreshold /= 2;
        int threshold = stats.pixelCount/autoThreshold;
        int i = -1;
        boolean found = false;
        int count;
        do {
            i++;
            count = histogram[i];
            if (count>limit) count = 0;
            found = count>threshold;
        } while (!found && i<255);
        int hmin = i;
        i = 256;
        do {
            i--;
            count = histogram[i];
            if (count>limit) count = 0;
            found = count > threshold;
        } while (!found && i>0);
        int hmax = i;
        Roi roi = imp.getRoi();
        if (hmax>=hmin) {
            if (RGBImage) imp.deleteRoi();
            min = stats.histMin+hmin*stats.binSize;
            max = stats.histMin+hmax*stats.binSize;
            if (min==max)
            {min=stats.min; max=stats.max;}
//            setMinAndMax(imp, min, max);
            if (RGBImage && roi!=null) imp.setRoi(roi);
        } else {
            reset(imp, ip);
            return;
        }
        updateScrollBars(null, false,true);
        if (Recorder.record) {
            if (Recorder.scriptMode())
                Recorder.recordCall("IJ.run(imp, \"Enhance Contrast\", \"saturated=0.35\");");
            else
                Recorder.record("run", "Enhance Contrast", "saturated=0.35");
        }
    }






    public static void recordSetMinAndMax(double min, double max) {
        if ((int)min==min && (int)max==max) {
            int imin=(int)min, imax = (int)max;
            if (Recorder.scriptMode()) {
                Recorder.recordCall("imp.setDisplayRange("+imin+", "+imax+");");
                Recorder.recordCall("imp.updateAndDraw();");
            } else
                Recorder.record("setMinAndMax", imin, imax);
        } else {
            if (Recorder.scriptMode()) {
                Recorder.recordCall("imp.setDisplayRange("+ResultsTable.d2s(min,2)+", "+ResultsTable.d2s(max,2)+");");
                Recorder.recordCall("imp.updateAndDraw();");
            } else
                Recorder.recordString("setMinAndMax("+ResultsTable.d2s(min,2)+", "+ResultsTable.d2s(max,2)+");");
        }
    }

    static final int RESET=0, SNAPSHOT=1,AUTO=2, REFRESH=3, MIN=4, MAX=5,SET=7,APPLY=6;

    // Separate thread that does the potentially time-consuming processing
    public void run() {
        while (!done) {
//            synchronized(this) {
//                try {wait();}
//                catch(InterruptedException e) {}
//            }
            setup();
            doUpdate();
        }
    }
    public void adjustMinBound(ImagePlus imp, ImageProcessor ip, int minvalue){
        updateScrollBars(null,false,false);
        if(plot.debug){
            IJ.log("Before:"+"\tplot.LowerBoundValue:"+plot.LowerBoundValue+"\n"
                    +"            plot.UpperBoundValue:"+plot.UpperBoundValue);
            setMinAndMax(imp, plot.LowerBoundValue, plot.UpperBoundValue);
            IJ.log("After:"+"\tplot.LowerBoundValue:"+plot.LowerBoundValue+"\n"
                    +"            plot.UpperBoundValue:"+plot.UpperBoundValue);
        }
    }
    public void adjustMaxBound(ImagePlus imp, ImageProcessor ip, int maxvalue){
        updateScrollBars(null,false,true);
        setMinAndMax(imp, plot.LowerBoundValue, plot.UpperBoundValue);
    }
    public void doRefresh(){
            plot.calcBoundary((double) (lowerBound) / 1000, (double) (upperBound) / 1000);
            plot.repaint();
            if(plot.debug){
                IJ.log("lowerbound:"+lowerBound+"   upperbound:"+upperBound);
            }
    }

    //to be changed
    void doUpdate() {
        ImagePlus imp;
        ImageProcessor ip;
        int action;
//        int lowerBoundValue=lowerBound;
//        int upperBoundValue=upperBound;
        if (doReset) action = RESET;
        else if (doAutoAdjust) action = AUTO;
        else if (doSnapshot) action=SNAPSHOT;
        else if (doRefresh) action=REFRESH;
        else if (doApplyLut)  action = APPLY;
        else if(doSet) action=SET;
        else if (lowerBound!=previousLowerBound||mode!=previousMode) action = MIN;//if mode changes then update the plot lowerbound and upperbound
        else if (upperBound!=previousUpperBound) action = MAX;
        else {
            return;}//no change occur then return
        doSet=doReset = doAutoAdjust = doApplyLut =doRefresh=doSnapshot= false;
        imp = WindowManager.getCurrentImage();
        if (imp==null) {
            IJ.beep();
            IJ.showStatus("No image");
            return;
        } else if (imp.getOverlay()!=null && imp.getOverlay().isCalibrationBar()) {
            IJ.beep();
            IJ.showStatus("Has calibration bar");
            return;
        }
        ip = imp.getProcessor();
        if (RGBImage && !imp.lock())
        {imp=null; return;}
        switch (action) {
            case RESET:
                reset(imp, ip);
                if (Recorder.record) {
                    if (Recorder.scriptMode())
                        Recorder.recordCall("IJ.resetMinAndMax(imp);");
                    else
                        Recorder.record("resetMinAndMax");
                }
                break;
//            case AUTO: autoAdjust(imp, ip); break;
//            case APPLY: apply(imp, ip); break;
            case SNAPSHOT: doSnapshot(imp,ip); break;
            case APPLY: apply(imp,ip);break;
            case MIN: adjustMinBound(imp, ip, lowerBound);
            previousLowerBound=lowerBound;
            break;
            case SET:Set(imp,ip);
            case REFRESH: doRefresh();
            break;
            case MAX: adjustMaxBound(imp, ip, upperBound);
            previousUpperBound=upperBound;
            break;
        }
        change_occur=true;
        updatePlot();
        updateLabels(imp);
        if ((IJ.shiftKeyDown()||(balance&&channels==7)) && imp.isComposite())
            ((CompositeImage)imp).updateAllChannelsAndDraw();
        else
            imp.updateChannelAndDraw();
        if (RGBImage)
            imp.unlock();

    }

    private void Set(ImagePlus imp,ImageProcessor ip) {
        double upper=0.92;
        double lower=0.12;
        GenericDialog gd = new GenericDialog("Set");
        gd.addNumericField("UpperBound_Percentile: ", upper, 3);
        gd.addNumericField("LowerBound_Percentile: ", lower, 3);
        gd.showDialog();
        if (gd.wasCanceled())
            return;
        upper=gd.getNextNumber();
        lower=gd.getNextNumber();
        if(upper>lower&&0<=upper&upper<=1&&lower<=1&&0<=lower){
            upperBound=(int)(upper*1000);
            lowerBound=(int) (lower*1000);
            updateScrollBars(null,false,true);
            setMinAndMax(imp, plot.LowerBoundValue, plot.UpperBoundValue);
        }

    }

    /** Overrides close() in PlugInDialog. */
    public void close() {
        super.close();
        instance = null;
        done = true;
        Prefs.saveLocation(LOC_KEY, getLocation());
        synchronized(this) {
            notify();
        }
    }

//    public void windowActivated(WindowEvent e) {
//        super.windowActivated(e);
//        Window owin = e.getOppositeWindow();
//        if (owin==null || !(owin instanceof ImageWindow))
//            return;
//        if (IJ.debugMode) IJ.log("windowActivated: "+owin);
//        if (IJ.isMacro()) {
//            // do nothing if macro and RGB image
//            ImagePlus imp2 = WindowManager.getCurrentImage();
//            if (imp2!=null && imp2.getBitDepth()==24) {
//                return;
//            }
//        }
//        previousImageID = 0; // user may have modified image
//        setup();
//        WindowManager.setWindow(this);
//    }

    public synchronized  void itemStateChanged(ItemEvent e) {
        if (e.getSource() == choice_3D_2D) {
            System.out.println("[ItemListener<<itemStateChanged]:Choice selected: " + choice_3D_2D.getSelectedItem());
            if(choice_3D_2D.getSelectedItem().equals("Slice Scope")){
                mode=false;
            } else if (choice_3D_2D.getSelectedItem().equals("Stack Scope")) {
                mode=true;
            }
        }
        notify();
    }

    /** Resets this ContrastAdjuster and brings it to the front. */
    public void updateAndDraw() {
        previousImageID = 0;
        toFront();
    }

    /** Updates the ContrastAdjuster. */
    public static void update() {
        if (instance!=null) {
            instance.previousImageID = 0;
            instance.setup();
        }
    }

    public static void main(String[] args) {

        Class<?> clazz = Percentile_Normalization.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring("file:".length(),
                url.length() - clazz.getName().length() - ".class".length());
        System.setProperty("plugins.dir", pluginsDir);


        new ImageJ();


        ImagePlus image = IJ.openImage("D:\\SR\\Data\\3Ddata\\temporal data\\c1-405-2.tif");
        image.show();


        IJ.runPlugIn(clazz.getName(), "");
    }

} // ContrastAdjuster class
class HistogramPlot extends Canvas implements MouseListener {

    static final int WIDTH=256, HEIGHT=128;
    double defaultMin = 0;
    double defaultMax = 255;
    int[] histogram;
    int hmax;
    Image os;
    Graphics osg;
    Color color = Color.gray;
    double scale = Prefs.getGuiScale();
    int width = WIDTH;
    int height = HEIGHT;

    int LowerBoundValue;

    int UpperBoundValue;

    public HistogramPlot() {
        addMouseListener(this);
        if (scale>1.0) {
            width = (int)(width*scale);
            height = (int)(height*scale);
        }
        setSize(width+1, height+1);
    }

    /** Overrides Component getPreferredSize(). Added to work
     around a bug in Java 1.4.1 on Mac OS X.*/
    public Dimension getPreferredSize() {
        return new Dimension(width+1, height+15);
    }
    void setHistogram(int[] int_histogram, Color color) {
        this.color = color;
        this.histogram=int_histogram;
        if (histogram.length!=256) {
            histogram=null;
            return;
        }
        int maxCount = 0;
        int mode = 0;
        for (int i=0; i<256; i++) {
            if (histogram[i]>maxCount) {
                maxCount = histogram[i];
                mode = i;
            }
        }
        int maxCount2 = 0;
        for (int i=0; i<256; i++) {
            if ((histogram[i]>maxCount2) && (i!=mode))
                maxCount2 = histogram[i];
        }
        hmax = maxCount;//看上去像是抹去特别高的柱子
        if ((hmax>(maxCount2*2)) && (maxCount2!=0)) {
            hmax = (int)(maxCount2*1.5);
            histogram[mode] = hmax;
        }
        os = null;
    }
    void setHistogram(ImageStatistics stats, Color color) {
        this.color = color;
        this.histogram = stats.histogram;
        if (histogram.length!=256) {
            histogram=null;
            return;
        }
        int maxCount = 0;
        int mode = 0;
        for (int i=0; i<256; i++) {
            if (histogram[i]>maxCount) {
                maxCount = histogram[i];
                mode = i;
            }
        }
        int maxCount2 = 0;
        for (int i=0; i<256; i++) {
            if ((histogram[i]>maxCount2) && (i!=mode))
                maxCount2 = histogram[i];
        }
        hmax = stats.maxCount;
        if ((hmax>(maxCount2*2)) && (maxCount2!=0)) {
            hmax = (int)(maxCount2*1.5);
            histogram[mode] = hmax;
        }
        os = null;
    }

    public void update(Graphics g) {
        paint(g);
    }
//the histogram in imagej is converted to 0-255 by default
    public void calcBoundary(double percentile_low,double percentile_upper){
        if(this.histogram!=null){
            int len=this.histogram.length;
            int threshold_low;
            int threshold_upper;

            int sum=Arrays.stream(histogram).reduce(0, (a, b) -> a + b);
            threshold_low= (int) (sum*percentile_low);
            threshold_upper= (int) (sum*percentile_upper);
            int count=0;
            for(int i=0;i<len;i++){
                count+=histogram[i];
                if(count>=threshold_low&&((count-histogram[i])<=threshold_low)){
                    this.LowerBoundValue=i;
                }
                else if(count>=threshold_upper&&((count-histogram[i])<=threshold_upper)){
                    this.UpperBoundValue=i;
                    if(debug)
                    {IJ.log("LowerBoundValue:"+this.LowerBoundValue+" UpperBoundValue:"+this.UpperBoundValue);}
                    break;
                }
            }
            this.LowerBoundValue=cast(LowerBoundValue);
            this.UpperBoundValue=cast(UpperBoundValue);
        }
    }
    //from 0-255 to defaultmin-defaultmax
    int cast(int Bound){
        double temp=(double)(defaultMax-defaultMin);
        int result=(int) (temp/this.histogram.length*Bound+defaultMin);
        return result;
    }

    public void paint(Graphics g) {
        int x1=0,x2=width;
        double scale = (double)width/(defaultMax-defaultMin);
        if (this.LowerBoundValue>=defaultMin) {
            x1 = (int)(scale*(LowerBoundValue-defaultMin));
        }
        if (this.UpperBoundValue<=defaultMax) {
            x2 = (int)(scale*(UpperBoundValue-defaultMin));
        }
        if (histogram!=null) {
            if (os==null && hmax!=0) {
                os = createImage(width,height);
                osg = os.getGraphics();
                osg.setColor(Color.white);
                osg.fillRect(0, 0, width, height);
                osg.setColor(color);
                double scale2 = width/256.0;
                for (int i = 0; i < 256; i++) {
                    int x =(int)(i*scale2);
                    osg.drawLine(x, height, x, height - ((int)(height*histogram[i])/hmax));
                }
                osg.dispose();
            }
            if (os!=null) g.drawImage(os, 0, 0, this);
        } else {
            g.setColor(Color.white);
            g.fillRect(0, 0, width, height);
        }
        g.setColor(Color.black);
        g.drawLine(x1, 0, x1, height);
        g.drawLine(x2, 0, x2, height);
        g.setColor(Color.red);
        g.drawRect(0, 0, width, height);
//        g.setColor(Color.black); // 设置文字颜色
        //debug
        if(debug){
            IJ.log("x1:"+x1+"   x2:"+x2+"   defaultmin:"+defaultMin+"   defaultmax:"+defaultMax+"\n   plot.lowerbound:"+this.LowerBoundValue+"    plot.upperbound:"+this.UpperBoundValue);}
    }
    static boolean debug=false;
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}

} // ContrastPlot class
class TrimmedLabel extends Label {
    int trim = IJ.isMacOSX()?0:6;

    public TrimmedLabel(String title) {
        super(title);
    }

    public Dimension getMinimumSize() {
        return new Dimension(super.getMinimumSize().width, super.getMinimumSize().height-trim);
    }

    public Dimension getPreferredSize() {
        return getMinimumSize();
    }

} // TrimmedLabel class
