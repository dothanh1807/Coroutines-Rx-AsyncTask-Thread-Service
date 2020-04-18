package com.vllenin.corountinerx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment.*
import java.io.*
import java.net.MalformedURLException
import java.net.URL

/**
 * Created by Vllenin on 2020-04-18.
 */
class FragmentRx: Fragment() {

    /**
     * Các Observable cần phải được add vào CompositeDisposable này, để khi [onStop] sẽ clear
     * tránh memory leak.
     */
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textView.text = FragmentRx::class.java.simpleName
        btnDownload.setOnClickListener {
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video
            videoThumbnailsRx.generateThumbnailsWithRx(pathVideo) { measureTime ->
                textViewMeasureTime.text = measureTime.toString()
            }

            compositeDisposable.plusAssign(
                downloadImage(Link.ONE.path, 10)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ value ->
                        /**
                         * Code ở đây sẽ chạy trên thread được quy định bởi observeOn(..)
                         * Trường hợp này là observeOn(AndroidSchedulers.mainThread()) tức là
                         * chạy trên mainThread.
                         */
                        if (value is Int) {
                            Log.d("XXX", "subscribe: $value - ${Thread.currentThread().name}")
                            seekBar1.progress = value
                        } else if (value is Bitmap) {
                            imageView1.setImageBitmap(value)
                        }
                    }, { exception ->
                        /**
                         * Nếu trong quá trình Observalble(tức là code trong function [downloadImage])
                         * chạy gặp exception mà ta quên chưa xử lý thì sẽ exception đó sẽ được
                         * bắn vào đây.
                         */
                        exception.printStackTrace()
                        Log.d("XXX", "onError: - ${Thread.currentThread().name} $exception")
                    })
            )

            downloadImage(Link.TWO.path, 30)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ value ->
                    if (value is Int) {
                        seekBar2.progress = value
                    } else if (value is Bitmap) {
                        imageView2.setImageBitmap(value)
                    }
                }, {

                }).addTo(compositeDisposable)

        }
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
        compositeDisposable.dispose()
    }

    /**
     * Code ở function này sẽ chạy trên thread được quy định tại subscribeOn(..)
     * Trường hợp này là: subscribeOn(Schedulers.io()) tức là sẽ chạy ở thread khác, k phải
     * mainThread.
     */
    private fun downloadImage(path: String, timeDelay: Long): Observable<Any> = Observable.create { emitter ->
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
                if (emitter.isDisposed) {
                    return@create
                }
                data = inputStream.read(dataType)
                if (data > 0) {
                    totalData += data.toLong()
                    Log.d("XXX", "emitting: $totalData - ${Thread.currentThread().name}")
                    Thread.sleep(timeDelay)
                    emitter.onNext((totalData * 100 / sizeFile).toInt())/**~~~~~~~ emit ~~~~~~~~~~*/
                    outputStream.write(dataType, 0, data)
                } else {
                    break
                }
            }
            outputStream.flush()
            val dataCompleted = byteArrayOutputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(dataCompleted, 0,
                dataCompleted.size, BitmapFactory.Options())

            emitter.onNext(bitmap)/**~~~~~~~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("XXX", "$e")
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}