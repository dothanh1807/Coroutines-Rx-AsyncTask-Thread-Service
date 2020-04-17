package com.vllenin.corountinerx

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCoroutine.setOnClickListener {
            displayButton(false)
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(null)
                .replace(R.id.fragmentContainer, FragmentCorountine(), FragmentCorountine::class.toString())
                .commit()
        }

        btnRxKotlin.setOnClickListener {
            displayButton(false)

        }
    }

    private fun displayButton(show: Boolean) {
        if (show) {
            btnCoroutine.visibility = View.VISIBLE
            btnRxKotlin.visibility = View.VISIBLE
        } else {
            btnCoroutine.visibility = View.INVISIBLE
            btnRxKotlin.visibility = View.INVISIBLE
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        displayButton(true)
    }

}

enum class Link(val path: String){
    ONE("https://anhdephd.com/wp-content/uploads/2018/02/bo-hinh-nen-thien-nhien-phong-canh-dep-cho-may-tinh-pc-laptop.jpg"),
    TWO("https://www.gettyimages.ca/gi-resources/images/Homepage/Hero/UK/CMS_Creative_164657191_Kingfisher.jpg")
}
