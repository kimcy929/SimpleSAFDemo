package com.example.simplesafdemo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.drawToBitmap
import androidx.documentfile.provider.DocumentFile
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
            }
        }
    }

    private fun isQ() = true //Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private suspend fun saveImageImmediately() = withContext(Dispatchers.IO) {
        saveImageToExternal(Constant.fileName, imageView.drawToBitmap(), ImageType.JPG)
    }

    private suspend fun saveAndMove(imgName: String, bm: Bitmap, fileType: ImageType): String? {
        var outputStream: OutputStream? = null

        try {
            val fileName = when(fileType) {
                ImageType.JPG -> "$imgName.jpg"
                ImageType.PNG -> "$imgName.png"
                else -> "$imgName.webp"
            }

            val file = File(makeSaveFolder(), fileName)
            outputStream = FileOutputStream(file)

            when (fileType) {
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

    private fun saveImageToExternal(imgName: String, bm: Bitmap, fileType: ImageType): String? {

        var outputStream: OutputStream? = null
        try {
            val fileName = when(fileType) {
                ImageType.JPG -> "$imgName.jpg"
                ImageType.PNG -> "$imgName.png"
                else -> "$imgName.webp"
            }
            val documentFile = DocumentFile.fromTreeUri(this, AppSettings.treeUri!!.toUri())
            val newDocument = documentFile!!.createFile("image/*", fileName)
            val uri = newDocument!!.uri
            outputStream = contentResolver.openOutputStream(uri, "w")
            when (fileType) {
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

    object Constant {
        val fileName: String
            get() {
                val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                return simpleDateFormat.format(Date())
            }

        const val REQUEST_SAF = 1

        const val TEST_URL =
            "https://instagram.fhan3-2.fna.fbcdn.net/vp/1e448bffe2dcae96544245d5898bd00f/5DF4D893/t51.2885-15/e35/p1080x1080/59418343_444411752988369_5531962462080782_n.jpg?_nc_ht=instagram.fhan3-2.fna.fbcdn.net"
    }

    enum class ImageType {
        PNG, JPG, WEBP
    }
}
