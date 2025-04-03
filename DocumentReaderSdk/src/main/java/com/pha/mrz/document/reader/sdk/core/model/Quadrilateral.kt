package com.pha.mrz.document.reader.sdk.core.model

import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point

class Quadrilateral(val contour: MatOfPoint2f, val points: Array<Point>)