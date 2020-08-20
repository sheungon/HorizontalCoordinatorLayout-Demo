package com.agehua.horizontalcoordinatordemo.test

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agehua.horizontalcoordinatordemo.R
import com.agehua.horizontalcoordinatordemo.test.MyRecyclerViewAdapter.RecyclerHolder
import java.util.*

class MyRecyclerViewAdapter(recyclerView: RecyclerView) : RecyclerView.Adapter<RecyclerHolder>() {

    private val mContext: Context = recyclerView.context
    private val dataList: MutableList<String> = ArrayList()

    fun setData(dataList: List<String>?) {
        if (null != dataList) {
            this.dataList.clear()
            this.dataList.addAll(dataList)
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.simple_list_item_1, parent, false)
        return RecyclerHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerHolder, position: Int) {
        holder.textView.text = dataList[position]
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    class RecyclerHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        var textView: TextView

        init {
            textView = itemView.findViewById<View>(android.R.id.text1) as TextView
        }
    }

}