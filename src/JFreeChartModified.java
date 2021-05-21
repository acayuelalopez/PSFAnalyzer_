import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleEdge;

import ij.IJ;

/**
 * A demo showing crosshairs that follow the data points on an XYPlot.
 */
public class JFreeChartModified extends JPanel implements ChartMouseListener {

	ChartPanel chartPanel;

	Crosshair xCrosshair;

	Crosshair yCrosshair;
	XYSeriesCollection dataset;
	String xLabel, yLabel, chartTitle;
	double xCenter, x1, y1, x2, y2, domainMin, domainMax, rangeMax;

	public JFreeChartModified(double rangeMax, double domainMin, double domainMax, String chartTitle, XYSeriesCollection dataset,
			String xLabel, String yLabel, double xCenter, double x1, double y1, double x2, double y2) {
		this.dataset = dataset;
		this.xLabel = xLabel;
		this.yLabel = yLabel;
		this.xCenter = xCenter;
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.chartTitle = chartTitle;
		this.domainMin = domainMin;
		this.domainMax = domainMax;
		this.rangeMax = rangeMax;
	}

	public JPanel createContent() {
		JFreeChart chart = ChartFactory.createXYLineChart(chartTitle, xLabel, yLabel, dataset);
		XYPlot plot = (XYPlot) chart.getPlot();
		chartPanel = new ChartPanel(chart);
		chartPanel.addChartMouseListener(this);
		CrosshairOverlay crosshairOverlay = new CrosshairOverlay();
		xCrosshair = new Crosshair(Double.NaN, Color.GRAY, new BasicStroke(0f));
		xCrosshair.setLabelVisible(true);
		yCrosshair = new Crosshair(Double.NaN, Color.GRAY, new BasicStroke(0f));
		yCrosshair.setLabelVisible(true);
		crosshairOverlay.addDomainCrosshair(xCrosshair);
		crosshairOverlay.addRangeCrosshair(yCrosshair);
		chartPanel.addOverlay(crosshairOverlay);
		ValueMarker peakMarker = new ValueMarker(xCenter); // position is the value on the axis
		peakMarker.setPaint(Color.darkGray);
		peakMarker.setLabel("Intensity Peak"); // see JavaDoc for labels, colors, strokes
		plot.addDomainMarker(peakMarker);
		XYLineAnnotation fwhmMarker = new XYLineAnnotation(x1, y1, x2, y2, new BasicStroke(1.0f), Color.darkGray);
		plot.addAnnotation(fwhmMarker);
		plot.addAnnotation(new XYTextAnnotation("FWHM", xCenter, y1));
		chart.setBackgroundPaint(Color.white);
		plot.setBackgroundPaint(new Color(141, 149, 140));
		plot.getDomainAxis().setRange(domainMin, domainMax);
		plot.getRangeAxis().setRange(0, rangeMax);
		chart.getLegend().setBackgroundPaint(new Color(0xFF, 0xFF, 0xFF, 0));
		chartPanel.setPreferredSize(new Dimension(630, 320));
		return chartPanel;
	}

	@Override
	public void chartMouseClicked(ChartMouseEvent event) {
		// ignore
	}

	@Override
	public void chartMouseMoved(ChartMouseEvent event) {
		// Rectangle2D dataArea = chartPanel.getScreenDataArea();
		JFreeChart chart = event.getChart();
		XYPlot plot = (XYPlot) chart.getPlot();
		// ValueAxis xAxis = plot.getDomainAxis();

		Point2D p = chartPanel.translateScreenToJava2D(event.getTrigger().getPoint());
		Rectangle2D plotArea = chartPanel.getScreenDataArea();

		double x = plot.getDomainAxis().java2DToValue(p.getX(), plotArea, plot.getDomainAxisEdge());
		double y = plot.getRangeAxis().java2DToValue(p.getY(), plotArea, plot.getRangeAxisEdge());

		// double x = java2DToValue((double) event.getTrigger().getX(), dataArea,
		// RectangleEdge.BOTTOM);
		// double y = DatasetUtils.findYValue(plot.getDataset(), 0, x);
		xCrosshair.setValue(x);
		yCrosshair.setValue(y);
	}

}