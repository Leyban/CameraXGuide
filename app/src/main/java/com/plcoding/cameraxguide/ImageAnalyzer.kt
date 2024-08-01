package com.plcoding.cameraxguide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageAnalyzer(
    private val context: Context,
    private val onLivenessAnalysis: (Boolean)-> Unit,
    private val onFaceAnalysis: (Boolean)-> Unit
) : ImageAnalysis.Analyzer {
    private var interpreter: Interpreter? = null
    private lateinit var detector: FaceDetector
    private var frameSkipCounter = 0


    init {
        // Initialize TensorFlow Lite Interpreter
        val modelFile = loadModelFile(context, "livenesss10_40.tflite")
        interpreter = Interpreter(modelFile)

        // Initialize Face Detector
        setupDetector()
    }

    private fun setupDetector() {
        // High-accuracy landmark detection and face classification
//        val highAccuracyOpts = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//            .build()
        // Real-time contour detection
        val realTimeOpts = FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        try {
            detector = FaceDetection.getClient(realTimeOpts)
        } catch (e: IllegalStateException){
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (frameSkipCounter % 30 == 0) {
            frameSkipCounter = 0 // Reset Frame Counter

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                val result = detector.process(image)
                    .addOnSuccessListener { faces ->
                        val validFace = evaluateFaces(faces)

                        onFaceAnalysis(validFace)
                        if (validFace) {
                            val bitmap = imageProxy.toBitmap()
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val liveness = isLive(bitmap, rotation)
                            onLivenessAnalysis(liveness)
                        }
                        imageProxy.close()
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Log.d("Error", e.toString())
                        onFaceAnalysis(false)
                        onLivenessAnalysis(false)
                        imageProxy.close()
                    }
            }

        } else {
            imageProxy.close()
        }
        frameSkipCounter++
    }

    private fun evaluateFaces(faces: List<Face>): Boolean {
        if (faces.size != 1) {
            Log.d("Faces Count:", faces.size.toString())
            return false
        }

        val faceAngleTolerance = 20

        for (face in faces) {
            val bounds = face.boundingBox
//            Log.d("Bounds", bounds.toString())

            // Check Face Angle Rotation
            val rotX = face.headEulerAngleX // Head is rotated to the right rotX degrees
            if (-faceAngleTolerance > rotX || rotX > faceAngleTolerance) {
                Log.d("Head Angle X", rotX.toString())
                return false
            }
            val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
            if (-faceAngleTolerance > rotY || rotY > faceAngleTolerance) {
                Log.d("Head Angle Y", rotY.toString())
                return false
            }
            val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees
            if (-faceAngleTolerance > rotZ || rotZ > faceAngleTolerance) {
                Log.d("Head Angle Z", rotZ.toString())
                return false
            }

            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and nose available):
//            val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
//            leftEar?.let {
//                val leftEarPos = leftEar.position
//            }

            // If contour detection was enabled:
//            val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
//            val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points

            // If classification was enabled:
//            if (face.smilingProbability != null) {
//                val smileProb = face.smilingProbability
//            }

//            val leftEyeOpenProb = face.leftEyeOpenProbability
//            if (leftEyeOpenProb == null || leftEyeOpenProb < .5) {
//                Log.d("Left Eye Closed", leftEyeOpenProb.toString())
//                return false
//            }
//            val rightEyeOpenProb = face.rightEyeOpenProbability
//            if (rightEyeOpenProb == null || rightEyeOpenProb < .5) {
//                Log.d("Right Eye Closed", rightEyeOpenProb.toString())
//                return false
//            }

            // If face tracking was enabled:
//            if (face.trackingId != null) {
//                val id = face.trackingId
//            }
        }

        return true
    }

    // Load model file from assets or elsewhere
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun rescaleBitmap(bitmap: Bitmap, newHeight: Int, newWidth: Int, rotation: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val matrix = Matrix()
        matrix.postScale(
            newWidth.toFloat() / width.toFloat(),
            newHeight.toFloat() / height.toFloat()
        )
        matrix.postRotate(rotation.toFloat())

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

    private fun preprocessImage(bitmap: Bitmap, rotation: Int): ByteBuffer {
        val rescaleBitmap = rescaleBitmap(bitmap, 80, 80, rotation)
        val bitmapToFloatArray = bitmapToFloatArray(rescaleBitmap)
        return floatArrayToByteBuffer(bitmapToFloatArray)
    }

    private fun processObject(obj: Any): Array<FloatArray> {
        val currentTimeMillis = System.currentTimeMillis()
        val fArr = Array(1) { FloatArray(2) }

        // Run the interpreter with the input object and output array
        interpreter?.run(obj, fArr)

        // Logging the processing time
        val processingTime = System.currentTimeMillis() - currentTimeMillis
        Log.i("ProcessingTime", "Processing took $processingTime ms")

        return fArr
    }

    private fun applySoftmax(input: FloatArray): FloatArray {
        val length = input.size
        val expArray = FloatArray(length)
        var sumExp = 0.0

        // Calculate the exponentials and their sum
        for (i in 0 until length) {
            expArray[i] = kotlin.math.exp(input[i])
            sumExp += expArray[i]
        }

        // Calculate the softmax
        val softmaxArray = FloatArray(length)
        for (i in 0 until length) {
            softmaxArray[i] = (expArray[i] / sumExp).toFloat()
        }

        return softmaxArray
    }

    private fun isLive(bitmap: Bitmap, rotation: Int): Boolean {
        // Process the bitmap to get an intermediate result
        val intermediateResult = processObject(preprocessImage(bitmap, rotation))

        // Extract a specific value from the softmax output
        val softmaxValue = applySoftmax(intermediateResult[0])

        print("Model Output:  $intermediateResult -- Score: $softmaxValue")

        // Log the output
        Log.v("ProcessedResult", intermediateResult.contentToString())

        // Threshold checking
        return softmaxValue[0] > 0.6
    }
}