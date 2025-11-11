package com.tecsup.sentinelcore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter(private var events: List<SecurityEvent>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewTypeIndicator: View = view.findViewById(R.id.viewTypeIndicator)
        val tvEventIcon: TextView = view.findViewById(R.id.tvEventIcon)
        val tvEventTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvEventStatus: TextView = view.findViewById(R.id.tvEventStatus)
        val tvEventDescription: TextView = view.findViewById(R.id.tvEventDescription)
        val tvEventTime: TextView = view.findViewById(R.id.tvEventTime)
        val tvEventSource: TextView = view.findViewById(R.id.tvEventSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        val context = holder.itemView.context

        // Configurar según el tipo de evento
        when (event.type) {
            EventType.ATTACK_SENT -> {
                holder.viewTypeIndicator.setBackgroundColor(
                    context.getColor(R.color.red_team_accent))
                holder.tvEventIcon.text = "⚡"
            }
            EventType.ATTACK_BLOCKED -> {
                holder.viewTypeIndicator.setBackgroundColor(
                    context.getColor(R.color.blue_team))
                holder.tvEventIcon.text = "🛡️"
            }
            EventType.DEFENSE_ACTIVATED -> {
                holder.viewTypeIndicator.setBackgroundColor(
                    context.getColor(R.color.blue_team_dark))
                holder.tvEventIcon.text = "✅"
            }
            EventType.INTRUSION_DETECTED -> {
                holder.viewTypeIndicator.setBackgroundColor(
                    context.getColor(R.color.warning))
                holder.tvEventIcon.text = "⚠️"
            }
            EventType.FIREWALL_BLOCK -> {
                holder.viewTypeIndicator.setBackgroundColor(
                    context.getColor(R.color.blue_team))
                holder.tvEventIcon.text = "🚫"
            }
        }

        // Configurar estado
        when (event.status) {
            EventStatus.SUCCESS -> {
                holder.tvEventStatus.text = "Exitoso"
                holder.tvEventStatus.setBackgroundColor(context.getColor(R.color.success))
            }
            EventStatus.BLOCKED -> {
                holder.tvEventStatus.text = "Bloqueado"
                holder.tvEventStatus.setBackgroundColor(context.getColor(R.color.blue_team))
            }
            EventStatus.FAILED -> {
                holder.tvEventStatus.text = "Fallido"
                holder.tvEventStatus.setBackgroundColor(context.getColor(R.color.red_team_dark))
            }
            EventStatus.DETECTED -> {
                holder.tvEventStatus.text = "Detectado"
                holder.tvEventStatus.setBackgroundColor(context.getColor(R.color.warning))
            }
        }

        holder.tvEventTitle.text = event.title
        holder.tvEventDescription.text = event.description
        holder.tvEventSource.text = event.sourceIp

        // Formatear tiempo
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        holder.tvEventTime.text = sdf.format(Date(event.timestamp))
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<SecurityEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }
}