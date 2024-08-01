package com.plcoding.cameraxguide.presentation

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.plcoding.cameraxguide.domain.Classification
import com.plcoding.cameraxguide.domain.LivenessClassifier
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LivenessImageAnalyzer (
    private  val classifier: LivenessClassifier,
    private val onResults: (List<Classification>)-> Unit
): ImageAnalysis.Analyzer{
    override fun analyze(image: ImageProxy) {
        val rotationDegrees = image.imageInfo.rotationDegrees
        val bitmap = image.toBitmap()

        val floatArray = preprocessImage(bitmap)



    }



    private fun rescaleBitmap(bitmap: Bitmap, newHeight: Int, newWidth: Int ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val matrix = Matrix()
        matrix.postScale(newWidth.toFloat() / width.toFloat(), newHeight.toFloat() / height.toFloat())

        val createBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
        return createBitmap
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val width = bitmap.width
        val height = bitmap.height
        val fArr = Array(1) {
            Array(3) {
                Array(height) {
                    FloatArray(width)
                }
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                fArr[0][2][y][x] = ((pixel shr 16) and 0xFF).toFloat()
                fArr[0][1][y][x] = ((pixel shr 8) and 0xFF).toFloat()
                fArr[0][0][y][x] = (pixel and 0xFF).toFloat()
            }
        }

        return fArr
    }

    private fun floatArrayToByteBuffer(fArr: Array<Array<Array<FloatArray>>>): ByteBuffer {
        val allocateDirect = ByteBuffer.allocateDirect(
            fArr.size * fArr[0].size * fArr[0][0].size * fArr[0][0][0].size * 4
        )
        allocateDirect.order(ByteOrder.nativeOrder())

        for (i in fArr.indices) {
            for (j in fArr[i].indices) {
                for (k in fArr[i][j].indices) {
                    for (l in fArr[i][j][k].indices) {
                        allocateDirect.putFloat(fArr[i][j][k][l])
                    }
                }
            }
        }

        allocateDirect.rewind()
        return allocateDirect
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val rescaleBitmap = rescaleBitmap(bitmap, 80, 80)
        val bitmapToFloatArray = bitmapToFloatArray(rescaleBitmap)
        return floatArrayToByteBuffer(bitmapToFloatArray)
    }
}