package com.example.collegeproject1.live_detection

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.Navigation
import com.example.collegeproject1.R
import kotlinx.android.synthetic.main.fragment_live.*
import kotlinx.android.synthetic.main.fragment_live.view.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.concurrent.Executors

class LiveFragment : Fragment() {

    var chk: Boolean = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_live, container, false)

        view.backBtn.setOnClickListener {
            chk = true
            Navigation.findNavController(view)
                .navigate(R.id.action_liveFragment_to_homeFragment)
        }

        return view
    }

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var bitmapBuffer: Bitmap
    private val executor = Executors.newSingleThreadExecutor()
    private val tfImageBuffer = TensorImage(DataType.UINT8)

    private var imageRotatedDegrees: Int = 0


    private val tfLite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(requireContext(), Constants.MODEL_PATH),
            Interpreter.Options().addDelegate(NnApiDelegate())
        )
    }

    override fun onResume() {
        super.onResume()
        bindCamera()
    }

    private val tfInputSize by lazy {
        val inputIndex = 0
        val inputShape = tfLite.getInputTensor(inputIndex).shape()
        Size(inputShape[2], inputShape[1])
    }

    private val tfImageProcessor by lazy {
        val cropSize = minOf(bitmapBuffer.width, bitmapBuffer.height)
        ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(
                ResizeOp(
                    tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR
                )
            )
            .add(Rot90Op(imageRotatedDegrees / 90))
            .add(NormalizeOp(0f, 1f))
            .build()
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCamera() = viewFinderFrag.post {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(viewFinderFrag.display.rotation)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(viewFinderFrag.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val converter = YuvToRgbConverter(requireContext())

            imageAnalysis.setAnalyzer(executor, { image ->
                if (!::bitmapBuffer.isInitialized) {
                    imageRotatedDegrees = image.imageInfo.rotationDegrees
                    bitmapBuffer = Bitmap.createBitmap(
                        image.width, image.height, Bitmap.Config.ARGB_8888
                    )
                }

                image.use { converter.yuvToRgb(image.image!!, bitmapBuffer) }

                val tfImage = tfImageProcessor.process(tfImageBuffer.apply { load(bitmapBuffer) })
                tfFunct(tfImage)

            })

            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            preview.setSurfaceProvider(viewFinderFrag.createSurfaceProvider(camera.cameraInfo))

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun tfFunct(tfImage: TensorImage?): Any {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(0.4f)
            .setNumThreads(5)
            .setMaxResults(1)
            .build()


        val detector = ObjectDetector.createFromFileAndOptions(
            requireContext(),
            Constants.MODEL_PATH,
            options
        )


        val results = detector.detect(tfImage)

        val resultToShow = results.map {
            val category = it.categories.first()
            val text = "${category.label}"
            val score = "${category.score.times(100).toInt()}%"

            val location = RectF(
                it.boundingBox.top,
                it.boundingBox.left,
                it.boundingBox.bottom,
                it.boundingBox.right
            )

            DetectedResult(location, text, score)

        }

        activity?.runOnUiThread {
            resultToShow.maxByOrNull { it.score }?.let { showResult(resultToShow) }
        }


        return results
    }


    private fun showResult(resultToShow: List<DetectedResult>) {

        if (chk == true)
            return

        resultToShow.forEach {
            textPrediction.text = "${it.label}"

        }
    }

    data class DetectedResult(val location: RectF, val label: String, val score: String)

}