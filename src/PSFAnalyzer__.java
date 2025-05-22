import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import bilib.commons.components.GridPanel;
import bilib.commons.components.GridToolbar;
import bilib.commons.components.SpinnerRangeDouble;
import bilib.commons.components.SpinnerRangeInteger;
import bilib.commons.job.ExecutionMode;
import bilib.commons.job.callable.Job;
import bilib.commons.job.runnable.Pool;
import bilib.commons.settings.Settings;
import bilib.commons.table.CustomizedColumn;
import bilib.commons.table.CustomizedTable;
import bilib.commons.utils.NumFormat;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.WaitForUserDialog;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import ij.plugin.RoiEnlarger;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import psf.Data3D;
import psf.PSF;
import psf.Point3D;
import psf.defocusplane.DefocusPlanePSF;
import psf.defocusplane.DefocusPlanePSF.Plane;
import psf.defocusplane.ZFunction;
import psf.defocusplane.lateral.Gaussian;
import psf.defocusplane.lateral.LateralFunction;
import psfgenerator.CollectionPSF;

public class PSFAnalyzer__ implements PlugIn, Measurements {

	JFrame frame;
	static JLabel iconImage, imageLabel, iconImage2;
	PSFGenerator psfG = new PSFGenerator();
	private SpinnerRangeInteger spnNX = new SpinnerRangeInteger(256, 1, 9999, 1, 3);
	private SpinnerRangeInteger spnNY = new SpinnerRangeInteger(256, 1, 9999, 1, 3);
	private SpinnerRangeInteger spnNZ = new SpinnerRangeInteger(32, 1, 9999, 1, 3);
	private SpinnerRangeDouble spnResLateral = new SpinnerRangeDouble(100, 1, 99999, 1, 3);
	private SpinnerRangeDouble spnResAxial = new SpinnerRangeDouble(250, 1, 999999, 1, 3);
	private SpinnerRangeDouble spnNA = new SpinnerRangeDouble(1.4, 0.1, 3, 0.1, 3);
	private SpinnerRangeDouble spnLambda = new SpinnerRangeDouble(610, 10, 9999, 10, 3);
	private JButton lblResolutionXY = new JButton("FWHMXY");
	private JButton lblResolutionZ = new JButton("FWHMZ");
	CustomizedTable table;
	private Data3D data;
	private String fullname = "Untitled";
	private String shortname = "...";
	PSF psf = new DefocusPlanePSF(DefocusPlanePSF.GAUSSIAN);
	static JTable tableImages;
	static DefaultTableModel modelImages;
	static JScrollPane jScrollPaneImages;
	String CELLTYPEANALYZER_IMAGES_DEFAULT_PATH = "images_path";
	Preferences prefImages = Preferences.userRoot();
	ImagePlus[] imps;
	ImageIcon[] icons;
	TextField textImages;
	static ImagePlus imp;
	static JPanel panelX, panelY, panelFit;
	static JComboBox comboMode;
	static JCheckBox checkPS;
	static JTextField psField;
	ImageCanvas canvas;
	private Set<Integer> roiSet;
	private List<Integer> roiIndices;
	int roiIndex;
	Roi[] rois, roisFwhm;
	RoiManager rm;
	private JLabel xMeanLabel, xFwhm, yFwhm, events;

	@Override
	public void run(String arg0) {
		try {

			JFrame.setDefaultLookAndFeelDecorated(true);
			JDialog.setDefaultLookAndFeelDecorated(true);
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {
			e.printStackTrace();
		}
		createAndShowGUI();

	}

	public void createAndShowGUI() {
		frame = new JFrame();

		JButton buttonRefresh = new JButton("");
		ImageIcon iconRefresh = createImageIcon("images/refresh.png");
		Icon iconRefreshCell = new ImageIcon(iconRefresh.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));
		buttonRefresh.setIcon(iconRefreshCell);
		JButton buttonOpenImage = new JButton("");
		ImageIcon iconOpenImage = createImageIcon("images/openimage.png");
		Icon iconOpenImageCell = new ImageIcon(iconOpenImage.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));
		buttonOpenImage.setIcon(iconOpenImageCell);
		JButton buttonBrowse = new JButton("");
		ImageIcon iconBrowse = createImageIcon("images/browse.png");
		Icon iconBrowseCell = new ImageIcon(iconBrowse.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));
		buttonBrowse.setIcon(iconBrowseCell);
		textImages = (TextField) new TextField(15);
		textImages.setText(prefImages.get(CELLTYPEANALYZER_IMAGES_DEFAULT_PATH, ""));
		DirectoryListener listenerImages = new DirectoryListener("Browse for movies...  ", textImages,
				JFileChooser.FILES_AND_DIRECTORIES);
		buttonBrowse.addActionListener(listenerImages);
		JPanel panelImagesDirect = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelImagesDirect.add(textImages);
		panelImagesDirect.add(buttonBrowse);

		JPanel panelPicture = new JPanel();
		panelPicture.setLayout(new BoxLayout(panelPicture, BoxLayout.Y_AXIS));
		JPanel bLabel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		JSeparator separator1 = new JSeparator(SwingConstants.VERTICAL);
		Dimension dime = separator.getPreferredSize();
		separator.setPreferredSize(dime);
		separator1.setPreferredSize(dime);
		bLabel.add(separator);
		bLabel.add(panelImagesDirect);
		bLabel.add(buttonRefresh);
		bLabel.add(separator1);
		bLabel.add(buttonOpenImage);
		panelPicture.add(bLabel);
		tableImages = new JTable();
		modelImages = new DefaultTableModel();
		tableImages.setModel(modelImages);
		jScrollPaneImages = new JScrollPane(tableImages);
		jScrollPaneImages.setPreferredSize(new Dimension(655, 200));
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		JPanel imagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		imagePanel.add(jScrollPaneImages);
		panelPicture.add(imagePanel);
		panelPicture.add(Box.createVerticalStrut(3));
		panelPicture.add(bLabel);

		JPanel panelImages = new JPanel();
		panelImages.setLayout(new BoxLayout(panelImages, BoxLayout.Y_AXIS));
		panelX = new JPanel();
		panelX.setLayout(new BoxLayout(panelX, BoxLayout.Y_AXIS));
		iconImage = new JLabel();
		iconImage.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createRaisedBevelBorder(),
				BorderFactory.createLoweredBevelBorder()));
		iconImage2 = new JLabel();
		iconImage2.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createRaisedBevelBorder(),
				BorderFactory.createLoweredBevelBorder()));
		imageLabel = new JLabel();
		imageLabel.setBorder(BorderFactory.createLoweredSoftBevelBorder());
		// imageLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
		JPanel channelPanel = new JPanel();
		channelPanel.add(new JPanel(new FlowLayout(FlowLayout.LEFT)).add(iconImage));
		JPanel channelPanel2 = new JPanel();
		channelPanel2.add(new JPanel(new FlowLayout(FlowLayout.LEFT)).add(iconImage2));
		panelY = new JPanel();
		panelY.setLayout(new BoxLayout(panelY, BoxLayout.Y_AXIS));
		JTabbedPane tpXY = new JTabbedPane();
		tpXY.addTab("Cross Section-X", null, panelX, "PSF Cross Section X");
		tpXY.setMnemonicAt(0, KeyEvent.VK_1);
		tpXY.addTab("Cross Section-Y", null, panelY, "PSF Cross Section Y");
		tpXY.setMnemonicAt(1, KeyEvent.VK_2);
		JPanel panelData = new JPanel();
		panelData.setLayout(new BoxLayout(panelData, BoxLayout.Y_AXIS));
		JTabbedPane tp = new JTabbedPane();
		GridPanel pnOptics = new GridPanel(2);
		pnOptics.place(0, 0, "Wavelength");
		pnOptics.place(0, 1, spnLambda);
		pnOptics.place(0, 2, "nm");
		pnOptics.place(0, 3, "  ");
		pnOptics.place(0, 4, "NA");
		pnOptics.place(0, 5, spnNA);

		pnOptics.place(2, 0, "Pixelsize XY");
		pnOptics.place(2, 1, spnResLateral);
		pnOptics.place(2, 2, "nm");
		pnOptics.place(2, 3, "  ");
		pnOptics.place(2, 4, "Z-step");
		pnOptics.place(2, 5, spnResAxial);
		pnOptics.place(2, 6, "nm");

		pnOptics.place(3, 0, "FWHM XY");
		pnOptics.place(3, 1, lblResolutionXY);
		pnOptics.place(3, 2, "nm");
		pnOptics.place(3, 3, "  ");
		pnOptics.place(3, 4, "FWHM Z");
		pnOptics.place(3, 5, lblResolutionZ);
		pnOptics.place(3, 6, "nm");
		// Output Panel
		GridPanel pnOut = new GridPanel(2);
		pnOut.place(1, 0, "Size XYZ");
		pnOut.place(1, 1, spnNX);
		pnOut.place(1, 2, spnNY);
		pnOut.place(1, 3, spnNZ);
		// panelData.add(buildPanel());
		JPanel flowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		flowPanel.add(new JPanel(new FlowLayout(FlowLayout.LEFT)).add(channelPanel2));
		flowPanel.add(pnOptics);
		panelData.add(flowPanel);
		panelData.add(pnOut);
		panelData.add(summaryTableInitial());

		tp.addTab("FWHM Estimate from Images", null, panelImages, "FWHM from Images");
		// tp.setTabComponentAt(0, labelJSD);
		tp.setMnemonicAt(0, KeyEvent.VK_1);
		tp.addTab("PSF from Microscope Parameters", null, panelData, "PSF from Microscope Parameters");
		tp.setMnemonicAt(1, KeyEvent.VK_2);
		// tp.setBounds(50,50,200,200);
		comboMode = new JComboBox<String>();
		String[] comboItems = new String[] { "Automatic", "Manual", "Manual Average" };
		for (int i = 0; i < comboItems.length; i++)
			comboMode.addItem(comboItems[i]);
		comboMode.setToolTipText("Select an option to get Point Spread Function Analysis");
		JButton btnProcess = new JButton();
		ImageIcon iconProcess = createImageIcon("images/process.png");
		Icon processCell = new ImageIcon(iconProcess.getImage().getScaledInstance(15, 15, Image.SCALE_SMOOTH));
		btnProcess.setIcon(processCell);
		btnProcess.setToolTipText("Click this button to process selected image.");
		panelFit = new JPanel(new FlowLayout(FlowLayout.LEFT));
		checkPS = new JCheckBox("Physical Units: ");
		psField = new JTextField(5);
		psField.setEnabled(false);
		panelFit.add(btnProcess);
		panelFit.add(checkPS);
		panelFit.add(psField);
		JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel chPanel = new JPanel();
		chPanel.setLayout(new BoxLayout(chPanel, BoxLayout.Y_AXIS));
		chPanel.add(panelFit);
		JPanel panelImageLabel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelImageLabel.add(imageLabel);
		chPanel.add(panelImageLabel);
		chPanel.add(comboMode);
		totalPanel.add(channelPanel);
		totalPanel.add(chPanel);
		panelImages.add(totalPanel);
		panelImages.add(tpXY);
		JPanel mainPanel2 = new JPanel();
		mainPanel2.setLayout(new BoxLayout(mainPanel2, BoxLayout.Y_AXIS));
		mainPanel2.add(panelPicture);
		mainPanel2.add(tp);
		JFrame frame = new JFrame();
		frame.setTitle("Point-Spread-Function Analyzer");
		frame.setResizable(false);
		frame.add(mainPanel2);
		frame.pack();
		frame.setSize(660, 900);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);

		checkPS.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED)
					psField.setEnabled(true);
				if (e.getStateChange() == ItemEvent.DESELECTED)
					psField.setEnabled(false);
			}
		});
		lblResolutionXY.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				updateInterfaceXY();
				summaryTable();

			}
		});
		lblResolutionZ.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				updateInterfaceZ();
			}
		});
		buttonRefresh.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshAction();
			}
		});
		buttonOpenImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				List<ImagePlus> impAnalClose = new ArrayList<ImagePlus>();
				int[] IDs = WindowManager.getIDList();
				if (IDs != null)
					for (int i = 0; i < IDs.length; i++)
						impAnalClose.add(WindowManager.getImage(IDs[i]));

				if (tableImages.getSelectedRow() != -1) {
					if (IDs != null)
						for (int i = 0; i < IDs.length; i++)
							impAnalClose.get(i).hide();
					imp = imps[tableImages.getSelectedRow()];
				}
				if (imp == null)
					IJ.error("Please, select an image within the main directory.");
				if (imp != null)
					imp.show();

			}
		});
		btnProcess.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (imp == null)
					IJ.error("Please, you should open an image before processing.");
				if (imp != null)
					processImage();

			}
		});

	}

	private void updateInterfaceZ() {
		double z = 2 * spnLambda.get() / (spnNA.get() * spnNA.get());
		String znm = (new DecimalFormat("###.#")).format(z);
		lblResolutionZ.setText(znm);
	}

	private void updateInterfaceXY() {
		double xy = 0.61 * spnLambda.get() / spnNA.get();
		String snm = (new DecimalFormat("###.#")).format(xy);
		lblResolutionXY.setText(snm);
	}

	public void summaryTable() {

		compute(ExecutionMode.MULTITHREAD_NO);
		Point3D max = psf.getData().max;
		Point3D fwhm = psf.getData().fwhm;
		String name = psf.getShortname();
		Data3D data = psf.getData();
		ImageStack stack = new ImageStack(psf.nx, psf.ny);
		stack.addSlice(new FloatProcessor(psf.nx, psf.ny, data.createAsFloat(0)));
		iconImage2.setIcon(new ImageIcon(
				new ImagePlus("PSF " + name, stack).getImage().getScaledInstance(140, 125, Image.SCALE_SMOOTH)));
		table.setCell(0, 1, NumFormat.sci(psf.NA));
		table.setCell(0, 2, "");
		table.setCell(1, 1, NumFormat.sci(psf.lambda));
		table.setCell(1, 2, "");
		table.setCell(2, 1, NumFormat.sci(psf.getData().energy));
		table.setCell(2, 2, "");
		table.setCell(3, 1, NumFormat.sci(psf.nx * psf.resLateral));
		table.setCell(3, 2, "" + psf.nx);
		table.setCell(4, 1, NumFormat.sci(psf.ny * psf.resLateral));
		table.setCell(4, 2, "" + psf.ny);
		table.setCell(5, 1, NumFormat.sci(psf.nz * psf.resAxial));
		table.setCell(5, 2, "" + psf.nz);
		table.setCell(6, 1, NumFormat.sci(psf.resLateral));
		table.setCell(6, 2, "");
		table.setCell(7, 1, NumFormat.sci(psf.resLateral));
		table.setCell(7, 2, "");
		table.setCell(8, 1, NumFormat.sci(psf.resAxial));
		table.setCell(8, 2, "");
		table.setCell(9, 1, NumFormat.sci(fwhm.x * psf.resLateral));
		table.setCell(9, 2, NumFormat.sci(fwhm.x));
		table.setCell(10, 1, NumFormat.sci(fwhm.y * psf.resLateral));
		table.setCell(10, 2, NumFormat.sci(fwhm.y));
		table.setCell(11, 1, NumFormat.sci(fwhm.z * psf.resAxial));
		table.setCell(11, 2, NumFormat.sci(fwhm.z));
		table.setCell(12, 1, NumFormat.sci(fwhm.value));
		table.setCell(12, 2, "");

		table.setCell(13, 1, NumFormat.sci(max.x * psf.resLateral));
		table.setCell(13, 2, NumFormat.sci(max.x));
		table.setCell(14, 1, NumFormat.sci(max.y * psf.resLateral));
		table.setCell(14, 2, NumFormat.sci(max.y));
		table.setCell(15, 1, NumFormat.sci(max.z * psf.resAxial));
		table.setCell(15, 2, NumFormat.sci(max.z));
		table.setCell(16, 1, NumFormat.sci(max.value));
		table.setCell(16, 2, "");

	}

	public JPanel summaryTableInitial() {

		ArrayList<CustomizedColumn> columns = new ArrayList<CustomizedColumn>();
		columns.add(new CustomizedColumn("Feature", String.class, 40, false));
		columns.add(new CustomizedColumn("Value in nm", String.class, 40, false));
		columns.add(new CustomizedColumn("Value in pixel", String.class, 40, false));

		table = new CustomizedTable(columns, true);
		table.getTableHeader().setDefaultRenderer(new SimpleHeaderRenderer());
		table.append(new String[] { "Numerical Aperture", NumFormat.sci(0.0), "" });
		table.append(new String[] { "Wavelength", NumFormat.sci(0.0), "" });
		table.append(new String[] { "Energy", NumFormat.sci(0.0), "" });

		table.append(new String[] { "Size X", NumFormat.sci(0.0), "" + 0.0 });
		table.append(new String[] { "Size Y", NumFormat.sci(0.0), "" + 0.0 });
		table.append(new String[] { "Size Z", NumFormat.sci(0.0), "" + 0.0 });

		table.append(new String[] { "Pixelsize X", NumFormat.sci(0.0) });
		table.append(new String[] { "Pixelsize Y", NumFormat.sci(0.0) });
		table.append(new Object[] { "Axial Z-step", NumFormat.sci(0.0) });

		table.append(new String[] { "FWHM Lateral X", NumFormat.sci(0.0), NumFormat.sci(0.0) });
		table.append(new String[] { "FWHM Lateral Y", NumFormat.sci(0.0), NumFormat.sci(0.0) });
		table.append(new String[] { "FWHM Axial Z", NumFormat.sci(0.0), NumFormat.sci(0.0) });
		table.append(new String[] { "Energy under FWHM", NumFormat.sci(0.0), "" });

		table.append(new String[] { "Max Lateral X", NumFormat.sci(0.0), NumFormat.sci(0.0) });
		table.append(new String[] { "Max Lateral Y", NumFormat.sci(0.0), NumFormat.sci(0.0) });
		table.append(new String[] { "Max Axial Z", NumFormat.sci(0.0), NumFormat.sci(0.0) });
		table.append(new String[] { "Max Value", NumFormat.sci(0.0), "" });

		JPanel panel = new JPanel();
		panel.add(new JLabel(""));
		panel.setLayout(new BorderLayout());
		// panel.add(panel.getPane(), BorderLayout.NORTH);
		panel.add(table.getPane(200, 200), BorderLayout.CENTER);
		return panel;

	}

	public JPanel buildPanel() {
		GridToolbar pn = new GridToolbar(false);
		pn.place(03, 0, "<html>Z focal plane</sub></html>");
		pn.place(04, 0, "<html>Z defocused plane (x2)</html>");

		// pn.place(03, 1, spnFocus);
		// pn.place(04, 1, spnDefocus);

		pn.place(03, 2, "[nm]");
		pn.place(04, 2, "[nm]");

		JPanel panel = new JPanel();
		panel.add(pn);

		return panel;
	}

	public void compute(ExecutionMode mode) {
		int index = 1;
		// ArrayList<PSF> psfs = CollectionPSF.getStandardCollection();
		int nx = spnNX.get();
		int ny = spnNY.get();
		int nz = spnNZ.get();
		int type = 0;
		int scale = 0;

		// psf = psfs.get(index);
		psf.setOpticsParameters(spnNA.get(), spnLambda.get());
		psf.setResolutionParameters(spnResLateral.get(), spnResAxial.get());
		psf.setOutputParameters(nx, ny, nz, type, scale);
		Pool pool = new Pool("Main", null);
		pool.register(psf);
		pool.execute(ExecutionMode.MULTITHREAD_NO);

	}

	public static ImageIcon createImageIcon(String path) {
		java.net.URL imgURL = PSFAnalyzer__.class.getResource(path);
		if (imgURL != null) {
			return new ImageIcon(imgURL);
		} else {
			System.err.println("Couldn't find file: " + path);
			return null;
		}
	}

	public void refreshAction() {

		prefImages.put(CELLTYPEANALYZER_IMAGES_DEFAULT_PATH, textImages.getText());
		File imageFolder = new File(textImages.getText());
		File[] listOfFiles = imageFolder.listFiles();
		String[] imageTitles = new String[listOfFiles.length];
		imps = new ImagePlus[imageTitles.length];
		icons = new ImageIcon[imps.length];
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile())
				imageTitles[i] = listOfFiles[i].getName();
			imps[i] = IJ.openImage(textImages.getText() + File.separator + imageTitles[i]);
			icons[i] = new ImageIcon(getScaledImage(imps[i].getImage(), 90, 60));

		}
		Object[] columnNames = new Object[] { "Image", "Title", "Extension" };
		Object[][] dataTImages = new Object[imps.length][columnNames.length];
		for (int i = 0; i < dataTImages.length; i++)
			for (int j = 0; j < dataTImages[i].length; j++)
				dataTImages[i][j] = "";
		modelImages = new DefaultTableModel(dataTImages, columnNames) {

			@Override
			public Class<?> getColumnClass(int column) {
				if (getRowCount() > 0) {
					Object value = getValueAt(0, column);
					if (value != null) {
						return getValueAt(0, column).getClass();
					}
				}

				return super.getColumnClass(column);
			}

			public boolean isCellEditable(int row, int col) {
				return false;
			}

		};
		tableImages.setModel(modelImages);
		tableImages.setSelectionBackground(new Color(229, 255, 204));
		tableImages.setSelectionForeground(new Color(0, 102, 0));
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		tableImages.setDefaultRenderer(String.class, centerRenderer);
		tableImages.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tableImages.setRowHeight(60);
		tableImages.setAutoCreateRowSorter(true);
		tableImages.getTableHeader().setDefaultRenderer(new SimpleHeaderRenderer());

		for (int i = 0; i < modelImages.getRowCount(); i++) {
			modelImages.setValueAt(icons[i], i, tableImages.convertColumnIndexToModel(0));
			modelImages.setValueAt(imps[i].getShortTitle(), i, tableImages.convertColumnIndexToModel(1));
			modelImages.setValueAt(imps[i].getTitle().substring(imps[i].getTitle().lastIndexOf(".")), i,
					tableImages.convertColumnIndexToModel(2));
		}
		tableImages.getColumnModel().getColumn(0).setPreferredWidth(100);
		tableImages.getColumnModel().getColumn(1).setPreferredWidth(450);
		tableImages.getColumnModel().getColumn(2).setPreferredWidth(100);

	}

	public static Image getScaledImage(Image srcImg, int w, int h) {
		BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = resizedImg.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(srcImg, 0, 0, w, h, null);
		g2.dispose();
		return resizedImg;
	}

	public void processImage() {
		Thread process0 = new Thread(new Runnable() {

			public void run() {
				if (rm != null)
					rm.reset();
				ImagePlus impProcess = imp.duplicate();
				ImagePlus impRoi = imp.duplicate();
				IJ.run(impProcess, "8-bit", "");
				IJ.run(impProcess, "Auto Threshold", "method=Default ignore_black white");
				IJ.run(impProcess, "Create Selection", "");
				IJ.run(impProcess, "Enlarge...", "enlarge=2 pixel");
				Roi mainRoi = impProcess.getRoi();
				rois = new ShapeRoi(mainRoi).getRois();
				double pixelSize = impProcess.getCalibration().pixelWidth;
				List<Roi> roisDef = new ArrayList<Roi>();
				List<Double> roisDefAreas = new ArrayList<Double>();
				List<Double> roisDefMean = new ArrayList<Double>();
				List<Double> roisDefMeanIn = new ArrayList<Double>();

				if (comboMode.getSelectedIndex() == 2) {
					xFwhm = new JLabel("X-FWHM: ", SwingConstants.RIGHT);
					yFwhm = new JLabel("Y-FWHM: ", SwingConstants.RIGHT);
					events = new JLabel("Events:", SwingConstants.RIGHT);
					JPanel meanFWHM = new JPanel();
					meanFWHM.setLayout(new BoxLayout(meanFWHM, BoxLayout.Y_AXIS));
					meanFWHM.add(xFwhm);
					meanFWHM.add(yFwhm);
					meanFWHM.add(events);
					panelFit.add(meanFWHM);

					WaitForUserDialog cellDialog = new WaitForUserDialog(
							"Please, select a minimun of 2 single particle to calculate FWHM.");

					imp.getCanvas().addMouseListener(new MouseAdapter() {
						public void mouseClicked(MouseEvent e) {
							if (e.getClickCount() == 2) {
								ImageCanvas source = (ImageCanvas) e.getSource();
								if (source != canvas) {

									ImageWindow window = (ImageWindow) source.getParent();
									imp = window.getImagePlus();
									canvas = source;
								}

								double x = canvas.offScreenXD(e.getX());
								double y = canvas.offScreenYD(e.getY());
								rm = new RoiManager();
								rm = RoiManager.getInstance();
								for (int i = 0; i < rois.length; i++) {

									if (rois[i].contains((int) Math.floor(x), (int) Math.floor(y))) {
										imp.setRoi(rois[i]);
										rm.addRoi(rois[i]);

									}

								}

							}

						}

					});
					IJ.setTool("hand");
					cellDialog.show();
					roisFwhm = rm.getRoisAsArray();
					ImageProcessor impRoiProcessor[] = new ImageProcessor[roisFwhm.length];
					for (int j = 0; j < roisFwhm.length; j++) {
						impRoi.setRoi(roisFwhm[j]);
						impRoiProcessor[j] = impRoi.getProcessor().crop();
					}
					FWHMVersion fwhm = new FWHMVersion(pixelSize);
					fwhm.run(impRoiProcessor);

				}

				if (comboMode.getSelectedIndex() == 0) {

					for (int i = 0; i < rois.length; i++)
						roisDefAreas.add(rois[i].getStatistics().area);
					for (int i = 0; i < rois.length; i++)
						if (rois[i].getStatistics().area >= (Collections.min(roisDefAreas) * 5)
								&& rois[i].getStatistics().area <= (Collections.max(roisDefAreas) / 20)) {
							roisDef.add(rois[i]);// RoiEnlarger.enlarge(rois[i], 2.00));

						}

					for (int i = 0; i < roisDef.size(); i++) {
						impRoi.setRoi(roisDef.get(i));
						roisDefMean.add(roisDef.get(i).getStatistics().mean);
						if (// roisDef.get(i).getStatistics().mean > (Collections.max(roisDefMeanIn) / 2) &&
						(4.0 * roisDef.get(i).getStatistics().area / (Math.PI * roisDef.get(i).getStatistics().major
								* roisDef.get(i).getStatistics().major)) > 0.9)
							roisDefMeanIn.add(roisDef.get(i).getStatistics().mean);

					}
					roisFwhm = new Roi[1];
					int roiIndex = roisDefMean.indexOf(Collections.max(roisDefMeanIn));
					roisFwhm[0] = roisDef.get(roiIndex);
					impRoi.setRoi(roisFwhm[0]);
					ImageProcessor[] impRoiProcessor = new ImageProcessor[1];
					impRoiProcessor[0] = impRoi.getProcessor().crop();
					//impRoi.setRoi(new Roi(roisFwhm[0].getContourCentroid()[0], roisFwhm[0].getContourCentroid()[1],
							//roisFwhm[0].getFloatWidth() + 10, roisFwhm[0].getFloatHeight() + 10));
					//ImageProcessor roiToMakeImage = impRoi.getProcessor().crop()
					FWHMVersion fwhm = new FWHMVersion(pixelSize);
					fwhm.run(impRoiProcessor);
				}
				
				if (comboMode.getSelectedIndex() == 1) {

					for (int i = 0; i < rois.length; i++)
						roisDefAreas.add(rois[i].getStatistics().area);
					for (int i = 0; i < rois.length; i++)
						if (rois[i].getStatistics().area >= (Collections.min(roisDefAreas) * 5)
								&& rois[i].getStatistics().area <= (Collections.max(roisDefAreas) / 20)) {
							roisDef.add(rois[i]);// RoiEnlarger.enlarge(rois[i], 2.00));

						}

					for (int i = 0; i < roisDef.size(); i++) {
						impRoi.setRoi(roisDef.get(i));
						roisDefMean.add(roisDef.get(i).getStatistics().mean);
						if (// roisDef.get(i).getStatistics().mean > (Collections.max(roisDefMeanIn) / 2) &&
						(4.0 * roisDef.get(i).getStatistics().area / (Math.PI * roisDef.get(i).getStatistics().major
								* roisDef.get(i).getStatistics().major)) > 0.9)
							roisDefMeanIn.add(roisDef.get(i).getStatistics().mean);

					}
					
					
					imp.getCanvas().addMouseListener(new MouseAdapter() {
						public void mouseClicked(MouseEvent e) {
							if (e.getClickCount() == 3) {
								ImageCanvas source = (ImageCanvas) e.getSource();
								if (source != canvas) {

									ImageWindow window = (ImageWindow) source.getParent();
									imp = window.getImagePlus();
									canvas = source;
								}

								double x = canvas.offScreenXD(e.getX());
								double y = canvas.offScreenYD(e.getY());
								for (int i = 0; i < rois.length; i++) {

									if (rois[i].contains((int) Math.floor(x), (int) Math.floor(y))) {
										imp.setRoi(rois[i]);
										impRoi.setRoi(rois[i]);
										ImageProcessor[] impRoiProcessor = new ImageProcessor[1];
										impRoiProcessor[0] = impRoi.getProcessor().crop();
										FWHMVersion fwhm = new FWHMVersion(pixelSize);
										fwhm.run(impRoiProcessor);
									

									}

								}

							}

						}

					});
					
					
					
					
				}
				if (comboMode.getSelectedIndex() == 2) {
					xFwhm.setText(xFwhm.getText() + " " + Math.round(FWHMVersion.xFwhmAverage * 1000.0) / 1000.0);
					yFwhm.setText(yFwhm.getText() + " " + Math.round(FWHMVersion.yFwhmAverage * 1000.0) / 1000.0);
					events.setText(events.getText() + "   " + Math.round(FWHMVersion.eventsSum * 1000.0) / 1000.0);
				}

			}
		});
		process0.start();

	}

	private boolean same(Set<?> set1, Set<?> set2) {
		if (set1 == null || set2 == null)
			return set1 == set2;
		if (set1.size() != set2.size())
			return false;

		for (Object r : set1) {
			if (!set2.contains(r))
				return false;
		}

		return true;
	}

	private void incrementIndex() {
		roiIndex = (roiIndex + 1) % (roiIndices.size() + 1);
	}

}
