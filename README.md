# PSF Analyzer Plugin for ImageJ

**PSF Analyzer** is a plugin for ImageJ designed to facilitate the analysis of Point Spread Functions (PSF) in microscopy images. It provides a graphical interface for selecting, processing, and analyzing PSF data, focusing on the estimation of Full Width at Half Maximum (FWHM) and other relevant parameters.

## Main Features

- Load and preview multiple images from a directory.
- Select and process images to estimate PSF parameters.
- Automatic and manual modes for PSF analysis.
- Calculation of FWHM in lateral (XY) and axial (Z) directions.
- Export results in CSV format.
- Save processed images as TIFF files.

## Requirements

- Java 8 or higher.
- ImageJ (preferably the Fiji distribution).

## Installation

1. Compile or download the JAR file of the plugin.
2. Place it in the `plugins` folder of ImageJ/Fiji.
3. Restart ImageJ.
4. Access the plugin via `Plugins > PSF Analyzer`.

## Workflow

1. Select a directory of images.
2. Define a region of interest (ROI) with the rectangle tool.
3. Process the image to estimate PSF parameters.
4. View and adjust the ROIs if necessary.
5. Export the results and save the processed images.

## Outputs

- CSV file with quantitative data per image.
- TIFF images of the masks and generated montages.
- Individual and total results tables.

## Application

This plugin is useful for researchers in microscopy and optical imaging, enabling semi-automated quantification of PSF parameters in microscopy images.

## License

Distributed under the MIT License.

