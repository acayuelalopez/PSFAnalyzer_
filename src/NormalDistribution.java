
import java.awt.Color;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.data.function.Function2D;
import org.jfree.data.function.NormalDistributionFunction2D;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYSeries;
import javax.swing.JFrame;

/**
 *
 * @author peter
 */
public class NormalDistribution {

    private XYPlot plot;

    private XYSeriesCollection dataset;

    private NumberAxis domainAxis;

    public NormalDistribution(JFrame frame, double mean, double sd) {
        dataset = new XYSeriesCollection();
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Normal Distribution",
                "X",
                "PDF",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                false,
                false
        );

        plot = chart.getXYPlot();
        domainAxis = (NumberAxis) plot.getDomainAxis();
        domainAxis.setAutoRangeStickyZero(false); //Fixes the margin issue with 0

        XYDifferenceRenderer renderer1 = new XYDifferenceRenderer();
        Color none = new Color(0, 0, 0, 0);
        renderer1.setNegativePaint(none);//hide the area where the values for the second series are higher
        renderer1.setSeriesPaint(1, none);//hide the outline of the "difference area"

        plot.setRenderer(0, renderer1);
        updateData(mean, sd);
        final ChartPanel chartPanel = new ChartPanel(chart);
        frame.getContentPane().add(chartPanel);
    }

    private XYSeries createFunctionSeries(double mean, double sd) {
        double minX = mean - (4 * sd), maxX = mean + (4 * sd);  //Minimum and Maximum values on X-axis (4 deviations)
        Function2D normal = new NormalDistributionFunction2D(mean, sd);
        XYSeries functionSeries = DatasetUtils.sampleFunction2DToSeries(normal, minX, maxX, 100, "Normal");
        return functionSeries;
    }

    private XYSeries createAreaSeries(double mean, double sd) {
        XYSeries result = new XYSeries("");
        double fmax = 1 / sd / Math.sqrt(2 * Math.PI) * 1.05;
        result.add(mean - 4 * sd, 0);
        result.add(mean - sd, 0);
        result.add(mean - sd, fmax);
        result.add(mean + sd, fmax);
        result.add(mean + sd, 0);
        result.add(mean + 4 * sd, 0);
        return result;
    }

    public void updateData(double mean, double sd) {
        dataset.removeAllSeries();
        dataset.addSeries(createFunctionSeries(mean, sd));
        dataset.addSeries(createAreaSeries(mean, sd));
        domainAxis.setTickUnit(new NumberTickUnit(sd));
    }


}