package com.vllenin.corountinerx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.MalformedURLException
import java.net.URL

/**
 * Created by Vllenin on 2020-04-17.
 */
class FragmentCorountine : Fragment() {

    private val coroutineScopeInThisFragment =
        CoroutineScope(Dispatchers.Default + CoroutineName("CoroutinesDefault"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        button.setOnClickListener {
            coroutineScopeInThisFragment.launch(Dispatchers.Main) {
                downloadImage(Link.ONE.path)
                    .onCompletion { exception ->
                        /** When cancel coroutineScope at [onStop] then onCompletion still called,
                         * but variable [isActive] = false
                         */
                        if (exception == null) {
                            if (isActive) {
                                Log.d("XXX", "onCompleted: - ${Thread.currentThread().name}")
                            }
                        } else {
                            Log.d("XXX", "onFailed: - ${Thread.currentThread().name}")
                        }
                    }
                    .onEach { value ->
                        Log.d("XXX", "collect: $value - ${Thread.currentThread().name}")
                        delay(10)
                        if (value is Int) {
                            seekBar1.progress = value
                        } else if (value is Bitmap) {
                            imageView1.setImageBitmap(value)
                        }
                    }
                    .launchIn(this)/** Dùng [launchIn] để các coroutines ở dưới được chạy
                                            song song(cùng lúc), k phải đợi thằng này xong.
                                            Nếu dùng [collect] thì các coroutines ở dưới phải đợi
                                            thằng này chạy xong thì mới được chạy -> Mất nhiều
                                            tgian hơn.*/

                downloadImage(Link.TWO.path)
                    .onCompletion { exception ->
                        if (exception == null) {

                        } else {

                        }
                    }
                    .onEach { value ->
                        delay(20)
                        if (value is Int) {
                            seekBar2.progress = value
                        } else if (value is Bitmap) {
                            imageView2.setImageBitmap(value)
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        coroutineScopeInThisFragment.cancel()
    }

    private fun downloadImage(path: String): Flow<Any> = flow {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val url = URL(path)
            val connection = url.openConnection()
            val sizeFile = connection?.contentLength ?: -1
            inputStream = BufferedInputStream(url.openStream())
            val byteArrayOutputStream = ByteArrayOutputStream()
            outputStream = BufferedOutputStream(byteArrayOutputStream)

            val dataType = ByteArray(1024)
            var data: Int
            var totalData: Long = 0
            while (true) {
                data = inputStream.read(dataType)
                if (data > 0) {
                    totalData += data.toLong()
                    Log.d("XXX", "emitting: $totalData - ${Thread.currentThread().name}")
                    emit((totalData * 100 / sizeFile).toInt())
                    outputStream.write(dataType, 0, data)
                } else {
                    break
                }
            }
            outputStream.flush()
            val dataCompleted = byteArrayOutputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(dataCompleted, 0,
                dataCompleted.size, BitmapFactory.Options())
            emit(bitmap)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            /**
             * When cancel corountineScrop at [onStop] then block finally still called
             */
            Log.d("XXX", "finally")
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }.flowOn(Dispatchers.Default)

}