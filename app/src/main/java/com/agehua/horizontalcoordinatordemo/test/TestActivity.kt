package com.agehua.horizontalcoordinatordemo.test

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.agehua.horizontalcoordinatordemo.HorizontalAppBarLayout
import com.agehua.horizontalcoordinatordemo.R

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        val listView = findViewById<View>(R.id.list_view) as RecyclerView
        val data = arrayOf(
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "0"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "1"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "2"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "3"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "4"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "5"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "6"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "7"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "8"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "9"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "0"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "1"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "2"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "3"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "4"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "5"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "6"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "7"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "8"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "9"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "0"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "1"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "2"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "3"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "4"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "5"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "6"),
            TestData(R.drawable.ic_text_rotation_angleup_black_48dp, "7"),
            TestData(R.drawable.ic_text_rotation_none_black_48dp, "8"),
            TestData(R.drawable.ic_text_rotation_angledown_black_48dp, "9")
        )
        listView.isNestedScrollingEnabled = true
        val adapter = MyRecyclerViewAdapter(listView)
        listView.adapter = adapter
        adapter.setData(listOf(*data))

        val appbar = findViewById<HorizontalAppBarLayout>(R.id.appbar)
        val floatingBtn = findViewById<View>(R.id.btn_app_bar_mini_floating)
        floatingBtn.setOnClickListener {
            appbar.setExpanded(true, true)
        }
    }
}