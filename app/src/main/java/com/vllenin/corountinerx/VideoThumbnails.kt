package com.vllenin.corountinerx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Created by Vllenin on 2020-04-18.
 */
class VideoThumbnails constructor(
    context: Context,
    attrs: AttributeSet
) : View(context, attrs) {

    companion object {
        private const val DISTANCE_TIME_THUMBNAILS = 2000
    }

    private var timeStart = 0L
    private var thumbWidth = 0
    private var thumbHeight = 0
    private var widthScreen = 0
    private var listThumbnails = arrayListOf<Bitmap?>()

    private val disposables = CompositeDisposable()
    private val corountineScope = CoroutineScope(Dispatchers.Default)

    init {
        val extSizeAttr = intArrayOf(android.R.attr.layout_height)
        val typeArray = context.obtainStyledAttributes(attrs, extSizeAttr)
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        widthScreen = displayMetrics.widthPixels
        thumbHeight = typeArray.getDimensionPixelSize(0, -1)
        thumbWidth = thumbHeight
        typeArray.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (listThumbnails.isNotEmpty()) {
            var x = 0f

            for (i in 0 until listThumbnails.size) {
                val bitmap = listThumbnails.getOrNull(i)
                bitmap?.let {
                    canvas.drawBitmap(bitmap, x, 0f, null)
                    x += bitmap.width
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disposables.clear()
        disposables.dispose()
        corountineScope.cancel()
    }

    /********************************* Use RxKotlin *********************************************/

    fun generateThumbnailsWithRx(path: String, callback: (measureTime: Long) -> Unit) {
        timeStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(path))
        val durationVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        val numThumbnails = (durationVideo / DISTANCE_TIME_THUMBNAILS).toInt()
        val interval = durationVideo * 1000 / numThumbnails

        val newLayoutParams = layoutParams
        newLayoutParams.width = thumbWidth * numThumbnails
        layoutParams = newLayoutParams

        thumbnailsFactoryRx(retriever, numThumbnails, interval)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                callback.invoke(System.currentTimeMillis() - timeStart)
            }
            .subscribe({ thumbnail ->
                listThumbnails.add(thumbnail)
                invalidate()
            }, { exception ->
                exception.printStackTrace()
            })
            .addTo(disposables)
    }

    private fun thumbnailsFactoryRx(retriever: MediaMetadataRetriever,
                                    numThumbnails: Int,
                                    interval: Long): Observable<Bitmap> = Observable.create { emitter ->
        var frameVideo: Bitmap? = null
        var centerCropBitmap: Bitmap? = null
        try {
            (0 until numThumbnails).forEach { index ->
                frameVideo = retriever.getFrameAtTime(index * interval,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                frameVideo?.let { bitmapOrigin ->
                    centerCropBitmap = if (bitmapOrigin.height > bitmapOrigin.width) {
                        Bitmap.createBitmap(bitmapOrigin,
                            0,
                            bitmapOrigin.height / 2 - bitmapOrigin.width / 2,
                            bitmapOrigin.width,
                            bitmapOrigin.width)
                    } else {
                        Bitmap.createBitmap(bitmapOrigin,
                            bitmapOrigin.width / 2 - bitmapOrigin.height / 2,
                            0,
                            bitmapOrigin.height,
                            bitmapOrigin.height)
                    }

                    centerCropBitmap?.let { copped ->
                        val bitmapScaled =
                            Bitmap.createScaledBitmap(copped, thumbWidth, thumbHeight, false)
                        emitter.onNext(bitmapScaled)/**~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~*/
                    }
                }
            }
            emitter.onComplete()/**~~~~~~~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } finally {
            retriever.release()
            frameVideo?.recycle()
            centerCropBitmap?.recycle()
        }
    }

    /******************************** Use Kotlin Coroutine **************************************/

    fun generateThumbnailsWithCorountines(path: String, callback: (measureTime: Long) -> Unit) {
        timeStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(path))
        val durationVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        val numThumbnails = (durationVideo / DISTANCE_TIME_THUMBNAILS).toInt()
        val interval = durationVideo * 1000 / numThumbnails

        val newLayoutParams = layoutParams
        newLayoutParams.width = thumbWidth * numThumbnails
        layoutParams = newLayoutParams

        corountineScope.launch(Dispatchers.Main) {
            thumbnailsFactoryCorountine(retriever, numThumbnails, interval)
                .flowOn(Dispatchers.Default)
                .onCompletion {
                    callback.invoke(System.currentTimeMillis() - timeStart)
                }
                .collect { thumbnail ->
                    listThumbnails.add(thumbnail)
                    invalidate()
                }
        }

    }

    private fun thumbnailsFactoryCorountine(retriever: MediaMetadataRetriever,
                                            numThumbnails: Int,
                                            interval: Long): Flow<Bitmap> = flow {
        var frameVideo: Bitmap? = null
        var centerCropBitmap: Bitmap? = null
        try {
            (0 until numThumbnails).forEach { index ->
                frameVideo = retriever.getFrameAtTime(index * interval,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                frameVideo?.let { bitmapOrigin ->
                    centerCropBitmap = if (bitmapOrigin.height > bitmapOrigin.width) {
                        Bitmap.createBitmap(bitmapOrigin,
                            0,
                            bitmapOrigin.height / 2 - bitmapOrigin.width / 2,
                            bitmapOrigin.width,
                            bitmapOrigin.width)
                    } else {
                        Bitmap.createBitmap(bitmapOrigin,
                            bitmapOrigin.width / 2 - bitmapOrigin.height / 2,
                            0,
                            bitmapOrigin.height,
                            bitmapOrigin.height)
                    }

                    centerCropBitmap?.let { copped ->
                        val bitmapScaled =
                            Bitmap.createScaledBitmap(copped, thumbWidth, thumbHeight, false)
                        emit(bitmapScaled)/**~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~*/
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } finally {
            retriever.release()
            frameVideo?.recycle()
            centerCropBitmap?.recycle()
        }
    }
}