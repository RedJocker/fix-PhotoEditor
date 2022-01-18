package org.hyperskill.photoeditor


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import android.provider.MediaStore.Images

import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.*

import org.hyperskill.photoeditor.BitmapFilters.brightenCopy
import org.hyperskill.photoeditor.BitmapFilters.calculateBrightnessMean
import org.hyperskill.photoeditor.BitmapFilters.contrastedCopy
import org.hyperskill.photoeditor.BitmapFilters.gammaCopy
import org.hyperskill.photoeditor.BitmapFilters.saturatedCopy


class MainActivity : AppCompatActivity() {

    private val currentImage: ImageView by lazy {
        findViewById<ImageView>(R.id.ivPhoto)
    }

    private val galleryButton: Button by lazy {
        findViewById<Button>(R.id.btnGallery)
    }

    private val saveButton : Button by lazy {
        findViewById<Button>(R.id.btnSave);
    }

    private val brightnessSlider: Slider by lazy {
        findViewById<Slider>(R.id.slBrightness);
    }

    private val contrastSlider: Slider by lazy {
        findViewById<Slider>(R.id.slContrast)
    }

    private val saturationSlider: Slider by lazy {
        findViewById<Slider>(R.id.slSaturation)
    }

    private val gammaSlider: Slider by lazy {
        findViewById<Slider>(R.id.slGamma)
    }

    private val intentLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                currentImage.setImageURI(uri)
                currentOriginalImageDrawable = currentImage.drawable as BitmapDrawable?
            }
        }

    private var currentOriginalImageDrawable: BitmapDrawable? = null
    private var lastJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setListener()

        //do not change this line
        currentImage.setImageBitmap(createBitmap())
        //

        currentOriginalImageDrawable = currentImage.drawable as BitmapDrawable?
    }



    private fun setListener() {
        galleryButton.setOnClickListener { _ ->
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intentLauncher.launch(intent)
        }

        saveButton.setOnClickListener { _ ->
            if(hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                val bitmap = ((currentImage.drawable as BitmapDrawable?)?.bitmap ?: return@setOnClickListener)

                val values = ContentValues()

                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
                values.put(Images.Media.MIME_TYPE, "image/jpeg")
                values.put(Images.ImageColumns.WIDTH, bitmap.width)
                values.put(Images.ImageColumns.HEIGHT, bitmap.height)

                val uri = this@MainActivity.contentResolver.insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@setOnClickListener

                val openOutputStream = contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, openOutputStream)
            } else {
                requestPermissions(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }

        }

        brightnessSlider.addOnChangeListener(this::onSliderChanges)
        contrastSlider.addOnChangeListener(this::onSliderChanges)
        saturationSlider.addOnChangeListener(this::onSliderChanges)
        gammaSlider.addOnChangeListener(this::onSliderChanges)


////      // case without coroutines (comment block above)      should produce "Are your filters being applied asynchronously?. expected: <(__, __, __)> actual: <(__, __, __)>"
//        brightnessSlider.addOnChangeListener(this::onSliderChangesWithoutCoroutines)
//        contrastSlider.addOnChangeListener(this::onSliderChangesWithoutCoroutines)
//        saturationSlider.addOnChangeListener(this::onSliderChangesWithoutCoroutines)
//        gammaSlider.addOnChangeListener(this::onSliderChangesWithoutCoroutines)

    }


    private fun onSliderChangesWithoutCoroutines(slider: Slider, sliderValue: Float, fromUser: Boolean){
        val bitmap = currentOriginalImageDrawable?.bitmap ?: return

        val brightnessValue = brightnessSlider.value.toInt()
        val brightenCopy = bitmap.brightenCopy(brightnessValue)

        val contrastValue = contrastSlider.value.toInt()
        val averageBrightness = brightenCopy.calculateBrightnessMean()
        val contrastedCopy = brightenCopy.contrastedCopy(contrastValue, averageBrightness)

        val saturationValue = saturationSlider.value.toInt()
        val saturatedCopy = contrastedCopy.saturatedCopy(saturationValue, contrastedCopy)

        val gammaValue = gammaSlider.value
        val gammaCopy = saturatedCopy.gammaCopy(gammaValue)

        currentImage.setImageBitmap(gammaCopy)
    }

    private fun onSliderChanges(slider: Slider, sliderValue: Float, fromUser: Boolean) {

        lastJob?.cancel()

        lastJob = GlobalScope.launch(Dispatchers.Default) {
            //  I/System.out: onSliderChanges job making calculations running on thread DefaultDispatcher-worker-1
            println("onSliderChanges " + "job making calculations running on thread ${Thread.currentThread().name}")

            val bitmap = currentOriginalImageDrawable?.bitmap ?: return@launch

            val brightenCopyDeferred: Deferred<Bitmap> = this.async {
                val brightnessValue = brightnessSlider.value.toInt()
                bitmap.brightenCopy(brightnessValue)
            }
            val brightenCopy: Bitmap = brightenCopyDeferred.await()

            val contrastedCopyDeferred: Deferred<Bitmap> = this.async {
                val contrastValue = contrastSlider.value.toInt()
                val averageBrightness = brightenCopy.calculateBrightnessMean()
                brightenCopy.contrastedCopy(contrastValue, averageBrightness)
            }
            val contrastedCopy = contrastedCopyDeferred.await()

            val saturatedCopyDeferred: Deferred<Bitmap> = this.async {
                val saturationValue = saturationSlider.value.toInt()
                contrastedCopy.saturatedCopy(saturationValue, contrastedCopy)
            }
            val saturatedCopy = saturatedCopyDeferred.await()

            val gammaCopyDeferred: Deferred<Bitmap> = this.async {
                val gammaValue = gammaSlider.value
                saturatedCopy.gammaCopy(gammaValue)
            }
            val gammaCopy = gammaCopyDeferred.await()

            runOnUiThread {
                //  I/System.out: onSliderChanges job updating view running on thread main
                println("onSliderChanges " + "job updating view running on thread ${Thread.currentThread().name}")
                currentImage.setImageBitmap(gammaCopy)
            }
        }
    }

    private fun hasPermission(manifestPermission: String): Boolean {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.checkSelfPermission(manifestPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            PermissionChecker.checkSelfPermission(this, manifestPermission) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions(permissionsToRequest: List<String>) {
        permissionsToRequest.filter { permissionToRequest ->
            hasPermission(permissionToRequest).not()
        }.also {
            if(it.isEmpty().not()) {
                // asking runtime permission is only for M or above. Before M permissions are
                // required on installation based on AndroidManifest.xml, so in theory it should
                // have required permissions if it is running
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.i("Permission", "requestPermissions")
                    this.requestPermissions(it.toTypedArray(), 0)
                } else {
                    // this should happen only if permission not requested on AndroidManifest.xml
                    Log.i("Permission", "missing required permission")
                }
            } else {
                Log.i("Permission",  "All required permissions are granted")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEachIndexed { index: Int, result: Int ->
            if(result == PackageManager.PERMISSION_GRANTED) {
                Log.d("PermissionRequest", "${permissions[index]} granted")
                if(permissions[index] == Manifest.permission.READ_EXTERNAL_STORAGE) {
                    galleryButton.callOnClick()
                } else if(permissions[index] == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    saveButton.callOnClick()
                }
            } else {
                Log.d("PermissionRequest", "${permissions[index]} denied")
            }
        }
    }

    // do not change this function
    fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        // get pixel array from source

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                // get color
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x+y) % 100 + 120

                pixels[index] = Color.rgb(R,G,B)

            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }
    //
}




