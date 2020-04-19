package com.vllenin.corountinerx

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCoroutines.setOnClickListener {
            displayButton(false)
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.fragmentContainer, FragmentCoroutine(), FragmentCoroutine::class.toString())
                .commit()
        }

        btnRxKotlin.setOnClickListener {
            displayButton(false)
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.fragmentContainer, FragmentRx(), FragmentRx::class.toString())
                .commit()
        }

        btnAsyncTask.setOnClickListener {
            displayButton(false)
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.fragmentContainer, FragmentAsyncTask(), FragmentAsyncTask::class.toString())
                .commit()
        }
    }

    private fun displayButton(show: Boolean) {
        if (show) {
            btnCoroutines.visibility = View.VISIBLE
            btnRxKotlin.visibility = View.VISIBLE
            btnAsyncTask.visibility = View.VISIBLE
            btnThread.visibility = View.VISIBLE
            btnService.visibility = View.VISIBLE
        } else {
            btnCoroutines.visibility = View.INVISIBLE
            btnRxKotlin.visibility = View.INVISIBLE
            btnAsyncTask.visibility = View.INVISIBLE
            btnThread.visibility = View.INVISIBLE
            btnService.visibility = View.INVISIBLE
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        displayButton(true)
    }

}

object Values{
    const val LINK_ONE = "https://anhdephd.com/wp-content/uploads/2018/02/bo-hinh-nen-thien-nhien-phong-canh-dep-cho-may-tinh-pc-laptop.jpg"
    const val LINK_TWO = "https://www.gettyimages.ca/gi-resources/images/Homepage/Hero/UK/CMS_Creative_164657191_Kingfisher.jpg"

    const val DISTANCE_TIME_THUMBNAILS = 2000
    const val DISTANCE_DELAY_ONE = 10L
    const val DISTANCE_DELAY_TWO = 30L
}
