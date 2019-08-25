package com.example.simplesafdemo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.drawToBitmap
import androidx.documentfile.provider.DocumentFile
import com.example.simplesafdemo.MainActivity.Constant.RC_OPEN_DOCUMENT
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppSettings.init(applicationContext)

        Picasso.get().load(Constant.TEST_URL).into(imageView)

        btnSAve.setOnClickListener {
            if (imageView.isLaidOut) {
                if (checkGrantedSAF()) {
                    launch {
                        val uri = saveImageImmediately()
                        if (!uri.isNullOrEmpty()) {
                            Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        btnSaveAndMove.setOnClickListener {
            if (imageView.isLaidOut) {
                if (checkGrantedSAF()) {
                    launch {
                        val uri =
                            saveAndMove(Constant.fileName, imageView.drawToBitmap(), ImageType.JPG)
                        if (!uri.isNullOrEmpty()) {
                            Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        btnChooseFolder.setOnClickListener {
            chooseFolderBeforeSave(ImageType.JPG)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    private fun checkGrantedSAF(): Boolean {
        val treeUri = AppSettings.treeUri
        if (treeUri.isNullOrEmpty() || treeUri.isNullOrBlank()) {
            showDialogChooseFolder()
            return false
        } else if (DocumentFile.fromTreeUri(this, treeUri.toUri())?.canWrite() != true) {
            showDialogChooseFolder()
            return false
        }
        return true
    }

    private fun showDialogChooseFolder() {
        AlertDialog.Builder(this)
            .setMessage(R.string.choose_folder)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, Constant.REQUEST_SAF)
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == Constant.REQUEST_SAF) {
                if (data == null) return
                val treeUri = data.data ?: return
                if (DocumentFile.fromTreeUri(this, treeUri)!!.canWrite()) {
                    var takeFlags = data.flags
                    takeFlags =
                        takeFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    // Check for the freshest data.
                    contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                    AppSettings.treeUri = treeUri.toString()

                    Log.d(MainActivity::class.java.simpleName, "Tree Uri -> $treeUri")

                    btnSAve.callOnClick()
                }
            } else if (requestCode == Constant.RC_OPEN_DOCUMENT) {
                if (data == null) return
                val uri = data.data ?: return
                launch {
                    val imageUri = withContext(Dispatchers.IO) {
                        saveImageInFolderSelected(imageView.drawToBitmap(), uri, ImageType.JPG)
                    }

                    Log.d(MainActivity::class.java.simpleName, "Uri  -> $imageUri")
                    if (!imageUri.isNullOrEmpty()) {
                        Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun isQ() = true //Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private suspend fun saveImageImmediately() = withContext(Dispatchers.IO) {
        saveImageToExternal(Constant.fileName, imageView.drawToBitmap(), ImageType.JPG)
    }

    private suspend fun saveAndMove(imgName: String, bm: Bitmap, imageType: ImageType): String? {
        var outputStream: OutputStream? = null

        try {
            val fileName = when(imageType) {
                ImageType.JPG -> "$imgName.jpeg"
                ImageType.PNG -> "$imgName.png"
                else -> "$imgName.webp"
            }

            val file = File(makeSaveFolder(), fileName)
            outputStream = FileOutputStream(file)

            when (imageType) {
                ImageType.JPG -> bm.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

                ImageType.PNG -> bm.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

                ImageType.WEBP -> bm.compress(Bitmap.CompressFormat.WEBP, 100, outputStream)
            }
            return moveFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.flush()
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!bm.isRecycled) {
                try {
                    bm.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return null
    }

    private fun saveImageToExternal(imgName: String, bm: Bitmap, imageType: ImageType): String? {

        var outputStream: OutputStream? = null
        try {
            val fileName = when(imageType) {
                ImageType.JPG -> "$imgName.jpeg"
                ImageType.PNG -> "$imgName.png"
                else -> "$imgName.webp"
            }
            val documentFile = DocumentFile.fromTreeUri(this, AppSettings.treeUri!!.toUri())
            val newDocument = documentFile!!.createFile("image/*", fileName)
            val uri = newDocument!!.uri
            outputStream = contentResolver.openOutputStream(uri, "w")
            when (imageType) {
                ImageType.JPG -> bm.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

                ImageType.PNG -> bm.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

                ImageType.WEBP -> bm.compress(Bitmap.CompressFormat.WEBP, 100, outputStream)
            }
            return uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.flush()
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!bm.isRecycled) {
                try {
                    bm.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return null
    }

    private fun makeSaveFolder(): File {
        var file = if (isQ()) {
            File(
                getExternalFilesDir(null)!!.path
                        + File.separator
                        + "SimpleSAFDemo"
            )
        } else {
            @Suppress("DEPRECATION")
            File(
                Environment.getExternalStorageDirectory().path
                        + File.separator
                        + "SimpleSAFDemo"
            )
        }

        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = if (isQ()) {
                    getExternalFilesDir(null)!!
                } else {
                    @Suppress("DEPRECATION")
                    Environment.getExternalStorageDirectory()
                }
            }
        }
        return file
    }

    private suspend fun moveFile(src: String, mimeType: String = "*/*") = withContext(Dispatchers.IO) {
        val context = this@MainActivity

        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        try {
            val fileSrc = File(src)
            val documentFile = DocumentFile.fromTreeUri(context,  AppSettings.treeUri!!.toUri())
            val newDocument = documentFile!!.createFile(mimeType, fileSrc.name)
            val uri = newDocument!!.uri
            outputStream = context.contentResolver.openOutputStream(uri, "w")

            inputStream = FileInputStream(fileSrc)
            val byteArray = ByteArray(1024)
            var read = inputStream.read(byteArray)
            while (read != -1) {
                outputStream!!.write(byteArray)
                read = inputStream.read(byteArray)
            }
            fileSrc.delete()
            Log.d(MainActivity::class.java.simpleName, "Move file successful!")
            return@withContext uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try {
                inputStream?.close()
                outputStream?.flush()
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    //https://github.com/ianhanniballake/LocalStorage/blob/master/mobile/src/main/java/com/ianhanniballake/localstorage/MainActivity.kt#L80-L90
    private fun chooseFolderBeforeSave(imageType: ImageType) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            // Filter to only show results that can be "opened", such as
            // a file (as opposed to a list of contacts or timezones).
            addCategory(Intent.CATEGORY_OPENABLE)
            // Create a file with the requested MIME type.
            val extension = when(imageType) {
                ImageType.JPG -> ".jpeg"
                ImageType.PNG -> ".png"
                else -> ".webp"
            }
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TITLE, "${Constant.fileName}$extension")
        }
        startActivityForResult(intent, RC_OPEN_DOCUMENT)
    }

    private fun saveImageInFolderSelected(bm: Bitmap, uri: Uri, imageType: ImageType): String? {

        var outputStream: OutputStream? = null
        try {
            outputStream = contentResolver.openOutputStream(uri, "w")
            when (imageType) {
                ImageType.JPG -> bm.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

                ImageType.PNG -> bm.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

                ImageType.WEBP -> bm.compress(Bitmap.CompressFormat.WEBP, 100, outputStream)
            }
            return uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.flush()
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!bm.isRecycled) {
                try {
                    bm.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return null
    }

    object Constant {
        val fileName: String
            get() {
                val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                return simpleDateFormat.format(Date())
            }

        const val REQUEST_SAF = 1

        const val RC_OPEN_DOCUMENT = 2

        const val TEST_URL =
            "https://instagram.fhan3-2.fna.fbcdn.net/vp/1e448bffe2dcae96544245d5898bd00f/5DF4D893/t51.2885-15/e35/p1080x1080/59418343_444411752988369_5531962462080782_n.jpg?_nc_ht=instagram.fhan3-2.fna.fbcdn.net"
    }

    enum class ImageType {
        PNG, JPG, WEBP
    }
}
