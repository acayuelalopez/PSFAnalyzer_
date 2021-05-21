import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.measure.ResultsTable;
import Jama.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import ij.plugin.filter.PlugInFilter;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.data.function.Function2D;
import org.jfree.data.function.NormalDistributionFunction2D;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.python.util.PythonInterpreter;

import ij.text.TextWindow;
import ij.io.Opener;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;


public class FWHMVersion implements PlugInFilter {

	// Various setup stuff
	static double SPREAD = 10; // noise variance
	// Do I fit an offset? I can't see a good reason to do this unless
	// there is some background. Doing so might give a better result however,
	// but thats cheating really
	static boolean offsetX = false;
	static boolean offsetY = false;
	static boolean showzoom = true;
	static boolean showguesses = false;
	static boolean showfitboxes = false;
	static boolean showfits = true;
	boolean singlepinhole = false;
	static double sthreshold = 0.65;
	static int xfiters = 50;
	static int yfiters = 50;
	static int xspacing = 30;
	static int yspacing = 15;
	static int signoff = 0;
	// static Roi roiToMeasure;
	static double plotheight = 0;
	static double dCutoff = 0.0; // default cutoff (minimum) value for calcs
									// (only values >= dCutoff are used)
	double xCenter, yCenter;// (use "0" to include all positive pixel values)
	static double dFactor = 1.0; // default factor
									// (multiplies pixel values prior to calculations)
	static Rectangle roi;
	static ImageProcessor ip;
	static ResultsTable rt;
	static String maintitle;
	static ij.measure.Calibration cal;
	static double cala;
	static double calb;
	double[] fit;
	double x1, y1, x2, y2;
	double pixelSize;
	ArrayList<Double> xFwhmValues = new ArrayList<Double>(), eventsValues = new ArrayList<Double>(),
			yFwhmValues = new ArrayList<Double>();
	static double xFwhmAverage, yFwhmAverage, eventsSum;

	public FWHMVersion(double pixelSize) {
		this.pixelSize = pixelSize;

	}

	public int setup(String arg, ImagePlus imp) {
		maintitle = imp.getTitle();
		maintitle = "- " + maintitle;
		cal = imp.getCalibration(); // take calibration. This takes care of signed/unsigned problems
		if (cal.calibrated()) {
			double[] calcoeffs = cal.getCoefficients();
			cala = calcoeffs[0];
			calb = calcoeffs[1];
		} else {
			cala = 0;
			calb = 0;
		}
		signoff = 0 - (int) cala;

		return DOES_ALL + NO_CHANGES;
	}

	public void run(ImageProcessor[] ipp) {

		for (int x = 0; x < ipp.length; x++) {
			ip = ipp[x].convertToShort(false);
			roi = ip.getRoi();
			int xoff = roi.x;
			int yoff = roi.y;
			int w = roi.width;
			int h = roi.height;
			byte[] mask = ip.getMaskArray(); // mask is for roi's that are not rectangular
			int options = 0;

			rt = ResultsTable.getResultsTable();
			rt.reset();
			singlepinhole = true;
			xspacing = 30;
			yspacing = 15;
			plotheight = 0;
			/*
			 * if (gd.wasCanceled()) { return; }
			 */

			if (singlepinhole) { // one pinhole only
				options = 1;
				fit = FWHM(roi, ip, options);
				addresults(rt, fit);

				// rt.show("Results");
			} else { // more than one pinhole..

				int histogram[] = ip.getHistogram(); // Make histogram. Offset by signoff (32768)
				double totpixels = 0;

				for (int i = 1; i < 32767; i++) {
					totpixels = totpixels + histogram[i + signoff] * i;
				}

				double frac = sthreshold;
				double threshpixel = 0;
				int threshold, i, j;
				for (i = 1; threshpixel < frac * totpixels; i++) { // threshold histogram
					threshpixel = threshpixel + histogram[i + signoff] * i;
				}
				threshold = i; // this is the pinhole selection threshold

				boolean[] abovethreshold = new boolean[w * h];
				boolean a, b;
				for (i = 0; i < w; i++) {
					for (j = 0; j < h; j++) {
						a = (ip.getPixel(i + xoff, j + yoff) - signoff > threshold);
						b = (mask == null || mask[i + j * w] != 0);
						abovethreshold[i + j * w] = (a && b);
					}
				}

				// start at the beginning of the suspect points, put a box round them
				// and see if there's a fit. If there is a good fit then don't look for
				// other pinholes within a region. This region is set to be twice as
				// big as the FWHM in X and Y

				Rectangle pinhole = ip.getRoi(); // pinhole is the fit window, not the region of interest
				// but is initialised like this
				int xpos = 0; // (xpos, ypos) is an int position of the fit within the ROI
				int ypos = 0;
				int xfwhm = 0; // (xfwhm, yfwhm) is an int version of the fwhms
				int yfwhm = 0;

				// (xboxwidth, yboxwidth) are the dimensions of the fit box
				int xboxwidth = xspacing;
				int yboxwidth = yspacing;
				options = 0;
				int numpinholes = 0;
				String progressmessage;

				// allow pinhole box edges to extend beyond the ROI, except if they extend
				// beyond the edge of the
				// whole image
				// also ROI has to be bigger than pinhole box

				if (xboxwidth > w || yboxwidth > h) {
					IJ.showMessage(
							"The region of interest is not big enough in X and/or Y to contain one pinhole.\nChange the pinhole spacing or increase the box size.");
					return;
				}

				// this rather complicated section sets the edge of the pinhole box in such as
				// way that it can extend
				// beyond the ROI but connot extend beyond the image. I hope

				int start_i, start_j, end_i, end_j;
				if (xoff - xboxwidth / 2 < 0) {
					start_i = (xboxwidth / 2 - xoff);
				} else {
					start_i = 0;
				}
				if (yoff - yboxwidth / 2 < 0) {
					start_j = (yboxwidth / 2 - yoff);
				} else {
					start_j = 0;
				}
				if (xoff + w + xboxwidth / 2 > ip.getWidth()) {
					end_i = (ip.getWidth() - xboxwidth / 2 - xoff);
				} else {
					end_i = w;
				}
				if (yoff + h + yboxwidth / 2 > ip.getHeight()) {
					end_j = (ip.getHeight() - yboxwidth / 2 - yoff);
				} else {
					end_j = h;
				}

				for (i = start_i; i < end_i; i++) {
					for (j = start_j; j < end_j; j++) {
						if (abovethreshold[i + j * w]) {
							pinhole.setRect(i + xoff - xboxwidth / 2, j + yoff - yboxwidth / 2, xboxwidth, yboxwidth);
							fit = FWHM(pinhole, ip, options);
							// if fit is good in X and Y then do the following. It would be an idea to
							// ask if the user only cares about one axis in the future
							if ((fit[0] < 1.0) && (fit[1] < 1.0)) { // if good X and Y fit..
								addresults(rt, fit);
								progressmessage = "Analysing Pinhole " + String.valueOf(numpinholes);
								IJ.showStatus(progressmessage);
								numpinholes++;
								xpos = (int) (fit[4] + 0.5 - xoff);
								ypos = (int) (fit[5] + 0.5 - yoff);
								xfwhm = (int) (fit[2] + 0.5);
								yfwhm = (int) (fit[3] + 0.5);
								int xstart = xpos - xfwhm;
								if (xstart < 0)
									xstart = 0;
								int xend = xpos + xfwhm;
								if (xend > w)
									xend = w;
								int ystart = ypos - yfwhm;
								if (ystart < 0)
									ystart = 0;
								int yend = ypos + yfwhm;
								if (yend > h)
									yend = h;
								for (int k = xstart; k < xend; k++) {
									for (int l = ystart; l < yend; l++) {
										abovethreshold[k + l * w] = false; // ... don't look any more
									}
								}
							}

						}

					}
				}

				// rt.show("Results");
				fwhmplot(w, h);

			}
			xFwhmValues.add(fit[2]);
			yFwhmValues.add(fit[3]);
			eventsValues.add(fit[6]);
		}
		xFwhmAverage = xFwhmValues.stream().mapToDouble(i -> i).average().getAsDouble();
		yFwhmAverage = yFwhmValues.stream().mapToDouble(i -> i).average().getAsDouble();
		eventsSum = eventsValues.stream() .mapToDouble(i -> i).sum();
	} 

	void fwhmplot(int w, int h) {
		float[] xcentroids = rt.getColumn(2); // xposition
		float[] xfits = rt.getColumn(0); // fwhm
		float[] ycentroids = rt.getColumn(3);
		float[] yfits = rt.getColumn(1);

		double[] xxminmax = ij.util.Tools.getMinMax(xcentroids);
		double[] xyminmax = ij.util.Tools.getMinMax(xfits);
		double[] yxminmax = ij.util.Tools.getMinMax(ycentroids);
		double[] yyminmax = ij.util.Tools.getMinMax(yfits);

		double[] dummy = { 0, 1 };
		double xplotheight, yplotheight;

		ImagePlus test_imp;

		if (w > h) { // if there is more X width than Y. This might not always work
			String xfwhmtitle = "X FWHM (pixels) " + maintitle;
			test_imp = WindowManager.getImage(xfwhmtitle);
			if (test_imp != null) {
				test_imp.hide();
			}
			Plot pw = new Plot(xfwhmtitle, "X (pixels)", "X FWHM (pixels)", dummy, dummy);
			if (plotheight == 0) {
				xplotheight = xyminmax[1] * 1.1;
			} else {
				xplotheight = plotheight;
			}
			pw.setLimits(xxminmax[0], xxminmax[1], 0, xplotheight);
			pw.addPoints(xcentroids, xfits, 1);
			// PSFAnalyzer__.panelX.add(in);

		} else {
			String yfwhmtitle = "Y FWHM (pixels) " + maintitle;
			test_imp = WindowManager.getImage(yfwhmtitle);
			if (test_imp != null) {
				test_imp.hide();
			}
			Plot pw = new Plot(yfwhmtitle, "Y (pixels)", "Y FWHM (pixels)", dummy, dummy);
			if (plotheight == 0) {
				yplotheight = yyminmax[1] * 1.1;
			} else {
				yplotheight = plotheight;
			}
			pw.setLimits(yxminmax[0], yxminmax[1], 0, yplotheight);
			pw.addPoints(ycentroids, yfits, 1);
			// pw.show();

			// PSFAnalyzer__.panelY.add(in);
		}
	} // fwhmplot

	void addresults(ResultsTable rt, double[] fit) {
		rt.incrementCounter();
		rt.addValue("X FWHM", fit[2]);
		rt.addValue("Y FWHM", fit[3]);
		rt.addValue("X center", fit[4]);
		rt.addValue("Y center", fit[5]);
		if (offsetX) {
			rt.addValue("X offset", fit[7]);
		}
		if (offsetY) {
			rt.addValue("Y offset", fit[8]);
		}
		rt.addValue("events", fit[6]);
		rt.addValue("X qual", fit[0]);
		rt.addValue("Y qual", fit[1]);
		return;
	} // addresults

	double[] FWHM(Rectangle FWHMroi, ImageProcessor ip, int options)

	{
		int xoff = FWHMroi.x; // FWHMroi is the redion of interest,
		int yoff = FWHMroi.y; // but not *the* region of interest
		int w = FWHMroi.width;
		int h = FWHMroi.height;
		int new_a;
		int new_b;
		double[] results = new double[9];
		double lambdaX = 0;
		double lambdaY = 0;

		if (xoff == 0 && yoff == 0 && w == 0 && h == 0) {
			IJ.showMessage("There is no Region of Interest.");
			return results;
		}

		// Draw zoom box of ROI if necessary
		// Zoom box is max_dim pixels in which ever direction is the longest
		// so it's visible but not too big.
		if (((options & 0x0001) != 0) && showzoom) {

			String zoom_title = "ROI detail box " + maintitle;
			ImagePlus new_imp = WindowManager.getImage(zoom_title);
			if (new_imp != null) {
				new_imp.hide();
			}

			new_imp = NewImage.createShortImage(zoom_title, w, h, 1, NewImage.FILL_BLACK);

			ImageProcessor new_ip = new_imp.getProcessor();
			for (int a = xoff; a < xoff + w; a++) {
				new_a = a - xoff;
				for (int b = yoff; b < yoff + h; b++) {
					new_b = b - yoff;
					new_ip.putPixel(new_a, new_b, ip.getPixel(a, b));
				}
			}

			// scale so max dimension is (max dim) pixels
			double hd = (double) h;
			double wd = (double) w;
			double ratio = hd / wd;
			int scaleh;
			int scalew;
			int max_dim = 200;

			if (ratio > 1) {
				scaleh = max_dim;
				scalew = (int) (scaleh / ratio);
			} else {
				scalew = max_dim;
				scaleh = (int) (ratio * scalew);
			}

			ImageProcessor scaled_ip = new_ip.resize(scalew, scaleh);
			scaled_ip.resetMinAndMax();
			new_imp.setProcessor(zoom_title, scaled_ip);
			PSFAnalyzer__.iconImage
					.setIcon(new ImageIcon(new_imp.getImage().getScaledInstance(140, 125, Image.SCALE_SMOOTH)));
			PSFAnalyzer__.imageLabel.setText("ROI detail box: " + PSFAnalyzer__.imp.getTitle());
			// .show();
			new_imp.updateAndDraw();

		}

		double[] xdataX = new double[w];
		double[] ydataX = new double[w];
		double maxyX = 0;
		int hmask;

		for (int col = 0; col < w; col++) {
			xdataX[col] = col;
			ydataX[col] = 0;
			hmask = 0; // takes account of non-rectangular roi's
			for (int row = 0; row < h; row++) {
				hmask++;
				ydataX[col] = ydataX[col] + ip.getPixel(xoff + col, yoff + row) - signoff;
			}
			if (ydataX[col] > maxyX)
				maxyX = ydataX[col];
			ydataX[col] = ydataX[col] / hmask;
		}

		// 1B. Get Y data. Same, but Y is h wide and w tall
		double[] xdataY = new double[h];
		double[] ydataY = new double[h];
		double maxyY = 0;
		int vmask;

		for (int col = 0; col < h; col++) {
			xdataY[col] = col;
			ydataY[col] = 0;
			vmask = 0;
			for (int row = 0; row < w; row++) {
				vmask++;
				ydataY[col] = ydataY[col] + ip.getPixel(xoff + row, yoff + col) - signoff;
			}
			if (ydataY[col] > maxyY)
				maxyY = ydataY[col];
			ydataY[col] = ydataY[col] / vmask;
		}

		double m00 = 0;
		double m10 = 0;
		double m01 = 0;
		double m20 = 0;
		double m02 = 0;
		double m11 = 0;
		int ry;
		int rx;
		double xCoord;
		double yCoord;
		double currentPixel;

		for (ry = yoff; ry < (yoff + h); ry++) {
			for (rx = xoff; rx < (xoff + w); rx++) {
				xCoord = rx;
				yCoord = ry;
				currentPixel = ip.getPixelValue(rx, ry);
				currentPixel = currentPixel - dCutoff;
				if (currentPixel < 0)
					currentPixel = 0; // gets rid of negative pixel values
				currentPixel = dFactor * currentPixel;
				m00 += currentPixel;
				m10 += currentPixel * xCoord;
				m01 += currentPixel * yCoord;
			}
		}
		double xC = m10 / m00;
		double yC = m01 / m00;

		for (ry = yoff; ry < (yoff + h); ry++) {
			for (rx = xoff; rx < (xoff + w); rx++) {
				xCoord = rx;
				yCoord = ry;
				currentPixel = ip.getPixelValue(rx, ry);
				currentPixel = currentPixel - dCutoff;
				if (currentPixel < 0)
					currentPixel = 0; // gets rid of negative pixel values
				currentPixel = dFactor * currentPixel;
				m20 += currentPixel * (xCoord - xC) * (xCoord - xC);
				m02 += currentPixel * (yCoord - yC) * (yCoord - yC);
				m11 += currentPixel * (xCoord - xC) * (yCoord - yC);
			}
		}
		double xxVar = m20 / m00;
		double yyVar = m02 / m00;
		double xyVar = m11 / m00;

		double[][] xX = new double[xdataX.length][1];
		for (int j = 0; j < xdataX.length; j++)
			xX[j][0] = xdataX[j];

		double[] aguessX = new double[4];
		aguessX[0] = maxyX;
		aguessX[1] = xC - xoff;
		aguessX[2] = Math.sqrt(xxVar); // devation is sqrt(variance)
		aguessX[3] = 0;

		double[] yX = (double[]) ydataX;
		double[] sX = new double[xdataX.length];
		for (int k = 0; k < xdataX.length; k++) { // to weight the samples
			if ((yX[k] < 1) || (yX[k] < maxyX * 0.1)) {
				sX[k] = 1.0;
			} else {
				sX[k] = 1.0;
			}
		}
		boolean[] varyX = new boolean[aguessX.length];
		for (int i = 0; i < aguessX.length; i++)
			varyX[i] = true;
		varyX[3] = offsetX; // fit an offset for X?
		LMfunc fX = new LMGauss();

		// 3B. Set up Y fit data
		double[][] xY = new double[xdataY.length][1];
		for (int j = 0; j < xdataY.length; j++)
			xY[j][0] = xdataY[j];

		double[] aguessY = new double[4];
		aguessY[0] = maxyY;
		aguessY[1] = yC - yoff;
		aguessY[2] = Math.sqrt(yyVar);
		aguessY[3] = 0;

		double[] yY = (double[]) ydataY;
		double[] sY = new double[xdataY.length];
		for (int k = 0; k < xdataY.length; k++) {
			if ((yY[k] < 1) || (yY[k] < maxyY * 0.1)) {
				sY[k] = 1.0;
			} else {
				sY[k] = 1.0;
			}
		}
		boolean[] varyY = new boolean[aguessY.length];
		for (int i = 0; i < aguessY.length; i++)
			varyY[i] = true;
		varyY[3] = offsetY; // fit an offset for Y?
		LMfunc fY = new LMGauss();

		// 4. Do fits. X then Y separately
		try {
			lambdaX = solve(xX, aguessX, yX, sX, varyX, fX, 0.001, 0.01, xfiters, 2);
		} catch (Exception ex) {
			System.err.println("Exception caught X: " + ex.getMessage());
			System.exit(1);
		}

		try {
			lambdaY = solve(xY, aguessY, yY, sY, varyY, fY, 0.001, 0.01, yfiters, 2);
		} catch (Exception ex) {
			System.err.println("Exception caught Y: " + ex.getMessage());
			System.exit(1);
		}

		// 5. Reassign fit data
		double devX = Math.abs(aguessX[2]);
		double fwhmX = devX * 2.35482; // convert std dev to fwhm
		double heightX = aguessX[0] * devX * Math.sqrt(2 * 3.14159); // don't do anything with this currently

		double devY = Math.abs(aguessY[2]);
		double fwhmY = devY * 2.35482;
		double heightY = aguessY[0] * devY * Math.sqrt(2 * 3.14159);
		// 6. Output fit data
		results[0] = lambdaX;
		results[1] = lambdaY;
		results[2] = fwhmX;
		results[3] = fwhmY;
		results[4] = aguessX[1] + xoff;
		results[5] = aguessY[1] + yoff;
		results[6] = m00;
		results[7] = aguessX[3];
		results[8] = aguessY[3];

		// 7. Plot data and fit if necessary
		if (((options & 0x0001) != 0) && showfits) {

			double[] yfitX = new double[100];
			double[] xfitX = new double[100];
			double[][] xfit2dX = new double[100][1];
			double fitstepX = (xdataX[xdataX.length - 1] - xdataX[0]) / 100;
			for (int l = 0; l < 100; l++) {
				xfitX[l] = fitstepX * l + xdataX[0];
				xfit2dX[l][0] = xfitX[l];
				yfitX[l] = fX.val(xfit2dX[l], aguessX);
			}

			String x_title = "X Histogram " + maintitle;
			ImagePlus new_pw = WindowManager.getImage(x_title);
			if (new_pw != null) {
				new_pw.hide();
			}

			double[] xxminmax = ij.util.Tools.getMinMax(xdataX);
			double[] xyminmax1 = ij.util.Tools.getMinMax(ydataX);
			double[] xyminmax2 = ij.util.Tools.getMinMax(yfitX);

			Plot pwX = new Plot(x_title, "X-Position", "Intensity");
			pwX.setColor(new Color(13, 160, 232));
			pwX.addPoints(xdataX, ydataX, PlotWindow.LINE);
			pwX.addLegend("Data");
			pwX.setBackgroundColor(new Color(254, 249, 231));
			pwX.setLimits(xxminmax[0], xxminmax[1], 0, Math.max(xyminmax1[1], xyminmax2[1]) * 1.1);
			pwX.setColor(new Color(232, 13, 13));
			pwX.addPoints(xfitX, yfitX, PlotWindow.CIRCLE);
			pwX.addLegend("Fit");

			XYSeries dataSerieX = new XYSeries("Data-X");
			XYSeries fitSerieX = new XYSeries("Fit-X");
			for (int i = 0; i < xdataX.length; i++)
				dataSerieX.add(xdataX[i], ydataX[i]);

			for (int i = 0; i < xfitX.length; i++)
				fitSerieX.add(xfitX[i], yfitX[i]);

			XYSeriesCollection datasetX = new XYSeriesCollection();
			datasetX.addSeries(dataSerieX);
			datasetX.addSeries(fitSerieX);
			// datasetX.addSeries(normalSeries);
			xCenter = results[4];
			x1 = xCenter - (fwhmX / 2);
			y1 = ij.util.Tools.getMinMax(yfitX)[1] / 2;
			x2 = xCenter + (fwhmX / 2);
			y2 = ij.util.Tools.getMinMax(yfitX)[1] / 2;

			JFreeChartModified freeChartX = new JFreeChartModified(ij.util.Tools.getMinMax(yfitX)[1] + 10,
					-1 * (fwhmX / 2), ij.util.Tools.getMinMax(xfitX)[1] + (fwhmX / 2), "X-FWHM Estimate", datasetX,
					"X-Position", "Intensity", xCenter, x1, y1, x2, y2);
			freeChartX.setSize(new Dimension(PSFAnalyzer__.panelX.getWidth(), 400));
			JPanel chartPanelX = freeChartX.createContent();
			Object[] columnNames = new Object[] { "X-FWHM", "X-Peak Intensity", "Events", "X-qual" };
			DefaultTableModel modelX = new DefaultTableModel(columnNames, 1);
			JTable tableX = new JTable(modelX);
			double pixelSize = 0;
			if (PSFAnalyzer__.checkPS.isSelected() == Boolean.TRUE)
				pixelSize = Double.valueOf(PSFAnalyzer__.psField.getText());
			if (PSFAnalyzer__.checkPS.isSelected() == Boolean.TRUE) {
				modelX.setValueAt(Math.round(pixelSize * results[2] * 1000.0) / 1000.0, 0, 0);
				modelX.setValueAt(Math.round(pixelSize * results[4] * 1000.0) / 1000.0, 0, 1);
			}
			if (PSFAnalyzer__.checkPS.isSelected() == Boolean.FALSE) {
				modelX.setValueAt(Math.round(results[2] * 1000.0) / 1000.0, 0, 0);
				modelX.setValueAt(Math.round(results[4] * 1000.0) / 1000.0, 0, 1);
			}
			modelX.setValueAt(Math.round(results[6] * 1000.0) / 1000.0, 0, 2);
			modelX.setValueAt(results[7], 0, 3);

			tableX.setRowHeight(0, 30);
			JScrollPane jscrollTable = new JScrollPane(tableX);
			jscrollTable.setPreferredSize(new Dimension(655, 45));
			DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
			centerRenderer.setHorizontalAlignment(JLabel.CENTER);
			tableX.setDefaultRenderer(Object.class, centerRenderer);
			tableX.getTableHeader().setDefaultRenderer(new SimpleHeaderRenderer());
			JPanel panelTable = new JPanel(new FlowLayout(FlowLayout.LEFT));
			panelTable.add(jscrollTable);
			panelTable.setPreferredSize(new Dimension(655, 55));
			if (PSFAnalyzer__.panelX.getComponents().length != 0) {
				PSFAnalyzer__.panelX.removeAll();
				PSFAnalyzer__.panelX.add(chartPanelX);
				PSFAnalyzer__.panelX.add(panelTable);
			}
			if (PSFAnalyzer__.panelX.getComponents().length == 0) {
				PSFAnalyzer__.panelX.add(chartPanelX);
				PSFAnalyzer__.panelX.add(panelTable);
			}

			double[] yfitY = new double[100];
			double[] xfitY = new double[100];
			double[][] xfit2dY = new double[100][1];
			double fitstepY = (xdataY[xdataY.length - 1] - xdataY[0]) / 100;
			for (int l = 0; l < 100; l++) {
				xfitY[l] = fitstepY * l + xdataY[0];
				xfit2dY[l][0] = xfitY[l];
				yfitY[l] = fY.val(xfit2dY[l], aguessY);
			}

			String y_title = "Y Histogram " + maintitle;
			new_pw = WindowManager.getImage(y_title);
			if (new_pw != null) {
				new_pw.hide();
			}

			double[] yxminmax = ij.util.Tools.getMinMax(xdataY);
			double[] yyminmax1 = ij.util.Tools.getMinMax(ydataY);
			double[] yyminmax2 = ij.util.Tools.getMinMax(yfitY);

			Plot pwY = new Plot(y_title, "Y Pixels", "Counts", xdataY, ydataY);
			pwY.setLimits(yxminmax[0], yxminmax[1], 0, Math.max(yyminmax1[1], yyminmax2[1]) * 1.1);
			pwY.addPoints(xfitY, yfitY, PlotWindow.CIRCLE);

			XYSeries dataSerieY = new XYSeries("Data-Y");
			XYSeries fitSerieY = new XYSeries("Fit-Y");
			for (int i = 0; i < xdataY.length; i++)
				dataSerieY.add(xdataY[i], ydataY[i]);

			for (int i = 0; i < xfitY.length; i++)
				fitSerieY.add(xfitY[i], yfitY[i]);

			XYSeriesCollection datasetY = new XYSeriesCollection();
			datasetY.addSeries(dataSerieY);
			datasetY.addSeries(fitSerieY);
			// datasetX.addSeries(normalSeries);
			yCenter = results[5];
			x1 = yCenter - (fwhmY / 2);
			y1 = ij.util.Tools.getMinMax(yfitY)[1] / 2;
			x2 = yCenter + (fwhmY / 2);
			y2 = ij.util.Tools.getMinMax(yfitY)[1] / 2;
			JFreeChartModified freeChartY = new JFreeChartModified(ij.util.Tools.getMinMax(yfitY)[1] + 10,
					-1 * (fwhmY / 2), ij.util.Tools.getMinMax(xfitY)[1] + (fwhmY / 2), "Y-FWHM Estimate", datasetY,
					"Y-Position", "Intensity", yCenter, x1, y1, x2, y2);
			freeChartY.setSize(new Dimension(PSFAnalyzer__.panelY.getWidth(), 400));
			JPanel chartPanelY = freeChartY.createContent();
			Object[] columnNamesY = new Object[] { "Y-FWHM", "Y-Peak Intensity", "Events", "Y-qual" };
			DefaultTableModel modelY = new DefaultTableModel(columnNamesY, 1);
			JTable tableY = new JTable(modelY);
			if (PSFAnalyzer__.checkPS.isSelected() == Boolean.TRUE) {
				modelY.setValueAt(Math.round(pixelSize * results[3] * 1000.0) / 1000.0, 0, 0);
				modelY.setValueAt(Math.round(pixelSize * results[5] * 1000.0) / 1000.0, 0, 1);
			}
			if (PSFAnalyzer__.checkPS.isSelected() == Boolean.FALSE) {
				modelY.setValueAt(Math.round(results[3] * 1000.0) / 1000.0, 0, 0);
				modelY.setValueAt(Math.round(results[5] * 1000.0) / 1000.0, 0, 1);
			}

			modelY.setValueAt(Math.round(results[6] * 1000.0) / 1000.0, 0, 2);
			modelY.setValueAt(results[8], 0, 3);
			tableY.setRowHeight(0, 30);
			JScrollPane jscrollTableY = new JScrollPane(tableY);
			jscrollTableY.setPreferredSize(new Dimension(655, 45));
			tableY.setDefaultRenderer(Object.class, centerRenderer);
			tableY.getTableHeader().setDefaultRenderer(new SimpleHeaderRenderer());
			JPanel panelTableY = new JPanel(new FlowLayout(FlowLayout.LEFT));
			panelTableY.add(jscrollTableY);
			panelTableY.setPreferredSize(new Dimension(655, 55));
			if (PSFAnalyzer__.panelY.getComponents().length != 0) {
				PSFAnalyzer__.panelY.removeAll();
				PSFAnalyzer__.panelY.add(chartPanelY);
				PSFAnalyzer__.panelY.add(panelTableY);
			}
			if (PSFAnalyzer__.panelY.getComponents().length == 0) {
				PSFAnalyzer__.panelY.add(chartPanelY);
				PSFAnalyzer__.panelY.add(panelTableY);
			}

			// pwY.draw();
		}

		return results;

	} // FWHM

	static double chiSquared(double[][] x, double[] a, double[] y, double[] s, LMfunc f)
// Works out the error of the data (contained in x[][] and y[]) to the fit
//  (contained in function f and fit parameters a[])
	{
		int npts = y.length;
		double sum = 0.;

		for (int i = 0; i < npts; i++) {
			double d = y[i] - f.val(x[i], a);
			d = d / s[i];
			sum = sum + (d * d);
		}

		return sum;
	} // chiSquared

	static class LMGauss implements LMfunc
//
// This is the function to which we are trying to fit.
// Different parts of it return:
// 1. val - values of the function
// 2. grad - differentiated values of the function
// 3. initial - returns inital values (not used)
// 4. testdata - returns a set of test data (not used)
//
	{
		public double val(double[] x, double[] a)
		// return a Gaussian
		{
			double y = 0.;
			double arg = (x[0] - a[1]) / a[2];
			double ex = Math.exp(-arg * arg / 2);
			y += (a[3] + a[0] * ex);

			return y;
		} // val

		public double grad(double[] x, double[] a, int a_k)
		// return differential coefficents of a Gaussian
		{

			double arg = (x[0] - a[1]) / a[2];
			double ex = Math.exp(-arg * arg / 2);
			double fac = a[0] * ex * 2. * arg;

			if (a_k == 0)
				return ex;

			else if (a_k == (1)) {
				return fac / a[2];
			}

			else if (a_k == (2)) {
				return fac * arg / a[2];
			}

			else if (a_k == (3)) {
				return 1;
			}

			else
				return 1;

		} // grad

		public double[] initial()
		// obsolete way of setting initial conditions.
		// I use the moment calculator now
		{
			double[] a = new double[4];
			a[0] = 32.;
			a[1] = 13.;
			a[2] = 0.7;
			a[3] = 0;

			return a;
		} // initial

		public Object[] testdata()
		// obsolete test method
		{
			Object[] o = new Object[4];
			int npts = 30;

			double[][] x = new double[npts][1];
			double[] y = new double[npts];
			double[] s = new double[npts];
			double[] a = new double[4];

			o[0] = x;
			o[1] = a;
			o[2] = y;
			o[3] = s;

			return o;
		} // testdata

	} // LMGauss

	static interface LMfunc {

		/**
		 * x is a single point, but domain may be mulidimensional
		 */
		double val(double[] x, double[] a);

		/**
		 * return the kth component of the gradient df(x,a)/da_k
		 */
		double grad(double[] x, double[] a, int ak);

		/**
		 * return initial guess at a[]
		 */
		double[] initial();

		/**
		 * return an array[4] of x,a,y,s for a test case; a is the desired final answer.
		 */
		Object[] testdata();

	} // LMfunc

	public static double solve(double[][] x, double[] a, double[] y, double[] s, boolean[] vary, LMfunc f,
			double lambda, double termepsilon, int maxiter, int verbose) throws Exception
//
// This routine solves fits to non-linear functions using the Levenberg 
//  Marquardt algorithm. This algorithm is robust with almost all
//  functions and especially so if the initial conditions are far from the fit.
//  However, it is more complicated than others and also slower, but we don't
//  worry about that sort of thing now, do we?!  Gaussian functions are not suitable
//  for fitting with most simple fit routines
//
// This routine could easily be adapted to other situations:
//  The fit parameters for the initial guesses and the eventualy fit are in a[],
//  the data is in 
//  x[][] and y[], and the function to be fitted is in f
// For those not familiar with this routine, Wikipedia explains all:
//  http://en.wikipedia.org/wiki/Levenberg-Marquardt_algorithm
//
	{
		int npts = y.length;
		int nparm = a.length;

		if (verbose > 0) {
			System.out.print("solve x[" + x.length + "][" + x[0].length + "]");
			System.out.print(" a[" + a.length + "]");
			System.out.println(" y[" + y.length + "]");
		}

		double e0 = chiSquared(x, a, y, s, f);
		// double lambda = 0.001;
		boolean done = false;

		// g = gradient, H = hessian, d = step to minimum
		// H d = -g, solve for d
		double[][] H = new double[nparm][nparm];
		double[] g = new double[nparm];
		// double[] d = new double[nparm];

		double[] oos2 = new double[s.length];
		for (int i = 0; i < npts; i++)
			oos2[i] = 1. / (s[i] * s[i]);

		int iter = 0;
		int term = 0; // termination count test

		do {
			++iter;

			// hessian approximation
			for (int r = 0; r < nparm; r++) {
				for (int c = 0; c < nparm; c++) {
					for (int i = 0; i < npts; i++) {
						if (i == 0)
							H[r][c] = 0.;
						double[] xi = x[i];
						H[r][c] += (oos2[i] * f.grad(xi, a, r) * f.grad(xi, a, c));
					} // npts
				} // c
			} // r

			// boost diagonal towards gradient descent
			for (int r = 0; r < nparm; r++)
				H[r][r] *= (1. + lambda);

			// gradient
			for (int r = 0; r < nparm; r++) {
				for (int i = 0; i < npts; i++) {
					if (i == 0)
						g[r] = 0.;
					double[] xi = x[i];
					g[r] += (oos2[i] * (y[i] - f.val(xi, a)) * f.grad(xi, a, r));
				}
			} // npts

			// scale (for consistency with NR, not necessary)
			if (false) {
				for (int r = 0; r < nparm; r++) {
					g[r] = -0.5 * g[r];
					for (int c = 0; c < nparm; c++) {
						H[r][c] *= 0.5;
					}
				}
			}

			// solve H d = -g, evaluate error at new location
			// double[] d = DoubleMatrix.solve(H, g);
			double[] d = (new Matrix(H)).lu().solve(new Matrix(g, nparm)).getRowPackedCopy();
			// double[] na = DoubleVector.add(a, d);
			double[] na = (new Matrix(a, nparm)).plus(new Matrix(d, nparm)).getRowPackedCopy();
			double e1 = chiSquared(x, na, y, s, f);

			if (verbose > 0) {
				System.out.println("\n\niteration " + iter + " lambda = " + lambda);
				System.out.print("a = ");
				(new Matrix(a, nparm)).print(10, 2);
				if (verbose > 1) {
					System.out.print("H = ");
					(new Matrix(H)).print(10, 2);
					System.out.print("g = ");
					(new Matrix(g, nparm)).print(10, 2);
					System.out.print("d = ");
					(new Matrix(d, nparm)).print(10, 2);
				}
				System.out.print("e0 = " + e0 + ": ");
				System.out.print("moved from ");
				(new Matrix(a, nparm)).print(10, 2);
				System.out.print("e1 = " + e1 + ": ");
				if (e1 < e0) {
					System.out.print("to ");
					(new Matrix(na, nparm)).print(10, 2);
				} else {
					System.out.println("move rejected");
				}
			}

			// termination test (slightly different than NR)
			if (Math.abs(e1 - e0) > termepsilon) {
				term = 0;
			} else {
				term++;
				if (term == 4) {
					System.out.println("terminating after " + iter + " iterations");
					done = true;
				}
			}
			if (iter >= maxiter)
				done = true;

			// in the C++ version, found that changing this to e1 >= e0
			// was not a good idea. See comment there.
			//
			if (e1 > e0 || Double.isNaN(e1)) { // new location worse than before
				lambda *= 10.;
			} else { // new location better, accept new parameters
				lambda *= 0.1;
				e0 = e1;
				// simply assigning a = na will not get results copied back to caller
				for (int i = 0; i < nparm; i++) {
					if (vary[i])
						a[i] = na[i];
				}
			}

		} while (!done);

		return lambda;
	} // solve

	public double calculateSD(double numArray[]) {
		double sum = 0.0, standardDeviation = 0.0;
		int length = numArray.length;

		for (double num : numArray) {
			sum += num;
		}

		double mean = sum / length;

		for (double num : numArray) {
			standardDeviation += Math.pow(num - mean, 2);
		}

		return Math.sqrt(standardDeviation / length);
	}
// Adrian_FWHM

	@Override
	public void run(ImageProcessor arg0) {
		// TODO Auto-generated method stub

	}
}
