package org.hyperskill.photoeditor

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.core.graphics.scale
import androidx.core.graphics.set
import com.google.android.material.slider.Slider


class MainActivity : AppCompatActivity() {

    private val currentImage: ImageView by lazy {
        findViewById<ImageView>(R.id.ivPhoto)
    }

    private val galleryButton: Button by lazy {
        findViewById<Button>(R.id.btnGallery)
    }

    private val brightnessSlider: Slider by lazy {
        findViewById<Slider>(R.id.slBrightness)
    }

    private val saveButton : Button by lazy {
        findViewById<Button>(R.id.btnSave)
//            .also{ it.text = "wrong value" }  // should produce "Wrong text for btnSave expected:<[SAVE]> but was:<[WRONG VALUE]>"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setListener()

        //do not change this line
        currentImage.setImageBitmap(createBitmap())         // commenting this line should produce "Initial image was null, it should be set with ___.setImageBitmap(createBitmap())"
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
//                                                                    .scale(30,30)  // produce "content://media/external/images/media/1 expected:<200> but was:<30>"
                val values = ContentValues()
                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
                values.put(Images.Media.MIME_TYPE, "image/jpeg")
                values.put(Images.ImageColumns.WIDTH, bitmap.width)
                values.put(Images.ImageColumns.HEIGHT, bitmap.height)

                val uri = this@MainActivity.contentResolver.insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@setOnClickListener

    //            val uri = this@MainActivity.contentResolver.insert(
    //                Uri.parse(Images.Media.EXTERNAL_CONTENT_URI.toString() + "/1"), values
    //            ) ?: return@setOnClickListener                                     // produce "content://media/external/images/media/1 expected:<200> but was:<100>"

                val openOutputStream = contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, openOutputStream)
            } else {
                requestPermissions(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))   // commenting should produce "Have you asked permission to write?"
            }

        }

        brightnessSlider.addOnChangeListener { _, value, _ ->
            val bitmap = currentOriginalImageDrawable?.bitmap ?: return@addOnChangeListener
            val copy = bitmap.copy(Bitmap.Config.RGB_565, true)
            val height = bitmap.height
            val width = bitmap.width
            val intValue = value.toInt()

            for(y in 0 until height) {
                for(x in 0 until width) {
                    copy[x, y] = bitmap.brightenPixel(x, y, intValue)
                }
            }
            currentImage.setImageBitmap(copy)
        }
    }


    private fun hasPermission(manifestPermission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            if (result == PackageManager.PERMISSION_GRANTED) {
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


    @ColorInt
    private fun Bitmap.brightenPixel(x : Int, y: Int, value: Int) : Int {
        val color = this.getPixel(x, y)
        val oldRed = Color.red(color)
        val oldBlue = Color.blue(color)
        val oldGreen = Color.green(color)

        val updateValue = { newValue: Int -> when {
                newValue > 255 -> 255
                newValue < 0 -> 0
                else -> newValue
            }
        }

        val red = updateValue(oldRed + value) // + 20
        val blue = updateValue(oldBlue + value)
        val green = updateValue(oldGreen + value)

        return Color.rgb(red, green, blue)
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
}