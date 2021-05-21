import sys
import os
import numpy as np
import pandas

from miplib.psf import psfgen

import miplib.analysis.resolution.fourier_ring_correlation as frc
from miplib.data.containers.fourier_correlation_data import FourierCorrelationDataCollection
from PIL import Image
from matplotlib import cm


resol = 0.25
##Setup deconvolution


fwhm = [resol, ] * 2
print(fwhm)
psf_generator = psfgen.PsfFromFwhm(fwhm)

psf = psf_generator.xy()
im = Image.fromarray(np.uint8(cm.gist_earth(psf)*255))
im.save('psfimages/naaa.bmp')
Image._show(im)


