package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R

data class ActionAppRow(
    val id: String,
    val title: String,
    val subtitle: String
)

class ActionAppsAdapter(
    private val items: List<ActionAppRow>,
    private val actionLabel: String,
    private val onAction: (ActionAppRow) -> Unit
) : RecyclerView.Adapter<ActionAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val subtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val button: Button = view.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_action_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
        holder.button.text = actionLabel
        holder.button.setOnClickListener {
            onAction(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
