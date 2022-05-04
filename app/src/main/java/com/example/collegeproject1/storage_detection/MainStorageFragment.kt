package com.example.collegeproject1.storage_detection

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.example.collegeproject1.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_main_storage.view.*
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainStorageFragment : Fragment() {
    private lateinit var bitmap: Bitmap
    private lateinit var imageUri: Uri
    private val IMAGE_CAPTURE_CODE = 625
    private var isLongClicked: Boolean = false
    var innerImage: android.widget.ImageView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_main_storage, container, false)

        view.cameraBtn.setOnLongClickListener {
            var intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"

            isLongClicked = true
            startActivityForResult(intent, 100)

            return@setOnLongClickListener true
        }

        view.imageView.setOnClickListener {
            val snack = Snackbar.make(
                it,
                "Press the camera button or Long Hold to Select Image",
                Snackbar.LENGTH_SHORT
            )

            snack.apply {
                setAction("Dismiss") {
                    snack.dismiss()
                }
                show()
            }

        }

        view.cameraBtn.setOnClickListener {
            openCamera()
        }

        return view
    }

    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Image")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From camera")
        imageUri =
            requireActivity().contentResolver!!.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )!!

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE)

    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (isLongClicked) {
            view?.imageView?.setImageURI(data?.data)

            //keeping screen on
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            var uri: Uri? = data?.data

            bitmap = MediaStore.Images.Media.getBitmap(
                this.activity?.contentResolver, uri
            )
            runTF(bitmap)
        }
        if (requestCode == IMAGE_CAPTURE_CODE &&
            resultCode == Activity.RESULT_OK
        ) {
            Log.d("enteredBlah", "onActivityResult()")
            if (imageUri != null && innerImage != null)
                innerImage!!.setImageURI(imageUri)
            doInference()
        }

    }

    private fun doInference() {
        Log.d("InferenceRunBlah", "doInferenceFunction")
        var input: Bitmap? = uriToBitmap(imageUri)
        var rotated = rotateBitmap(input!!)
        view?.imageView?.setImageBitmap(rotated)
        rotated?.let { runTF(it) }

    }

    private fun rotateBitmap(input: Bitmap): Bitmap? {
        val orientationColumn =
            arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cur =
            activity?.contentResolver?.query(imageUri, orientationColumn, null, null, null)

        var orientation = -1
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]))
        }
        Log.d("tryOrientation", orientation.toString() + "")
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(orientation.toFloat())
        return Bitmap.createBitmap(
            input,
            0,
            0,
            input.width,
            input.height,
            rotationMatrix,
            true
        )
    }

    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        try {
            val parcelFileDescriptor =
                activity?.contentResolver?.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }


    private fun runTF(bitmap: Bitmap) {
        val tensorImg = TensorImage.fromBitmap(bitmap)

        val tfOptions =
            org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(3)
                .setScoreThreshold(0.5f)
                .build()

        val tfDetector =
            org.tensorflow.lite.task.vision.detector.ObjectDetector.createFromFileAndOptions(
                this.context,
                "model.tflite",
                tfOptions
            )

        val predictedResults = tfDetector.detect(tensorImg)
        Log.d("predictedResultBlah", predictedResults.toString())

        val result = predictedResults.map {
            val category = it.categories.first()
            val text = category.label
            val score = "${category.score.times(100).toInt()}%"

            DetectionResult(it.boundingBox, text, score)
        }

        val imgResult = drawResultOnImage(bitmap, result)

        view?.imageView?.setImageBitmap(imgResult)

    }

    private fun drawResultOnImage(
        bitmap: Bitmap,
        detectionResult: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvasToPrint = Canvas(outputBitmap)
        val paint = Paint()
        paint.textAlign = Paint.Align.LEFT

        detectionResult.forEach { detectionResult ->
            // draw bounding box
            paint.color = Color.RED
            paint.strokeWidth = 8F
            paint.style = Paint.Style.STROKE
            val box = detectionResult.boundingBox
            canvasToPrint.drawRect(box, paint)

            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.color = Color.YELLOW
            paint.strokeWidth = 2F

            paint.textSize = 96F
            paint.getTextBounds(detectionResult.text, 0, detectionResult.text.length, tagSize)
            val fontSize: Float = paint.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < paint.textSize) paint.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvasToPrint.drawText(
                "${detectionResult.text}, ${detectionResult.score}",
                box.left + margin,
                box.top + tagSize.height().times(1F),
                paint
            )
        }
        return outputBitmap
    }

    data class DetectionResult(val boundingBox: RectF, val text: String, val score: String)

}