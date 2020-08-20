package com.agehua.horizontalcoordinatordemo.test

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agehua.horizontalcoordinatordemo.R
import com.agehua.horizontalcoordinatordemo.test.MyRecyclerViewAdapter.RecyclerHolder
import java.util.*

class MyRecyclerViewAdapter(recyclerView: RecyclerView) : RecyclerView.Adapter<RecyclerHolder>() {

    private val mContext: Context = recyclerView.context
    private val dataList: MutableList<TestData> = ArrayList()

    fun setData(dataList: List<TestData>?) {
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
        val data = dataList.getOrNull(position)
        holder.textView.text = data?.text
        data?.imgRes?.let {
            holder.imageView.setImageResource(it)
        } ?: run {
            holder.imageView.setImageDrawable(null)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    class RecyclerHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
        val imageView: ImageView = itemView.findViewById(R.id.image)
    }

}