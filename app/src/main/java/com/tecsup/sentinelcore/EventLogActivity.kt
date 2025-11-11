package com.tecsup.sentinelcore

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EventLogActivity : AppCompatActivity() {

    // Vistas
    private lateinit var tvAttacksSent: TextView
    private lateinit var tvAttacksBlocked: TextView
    private lateinit var tvTotalEvents: TextView

    private lateinit var btnFilterAll: MaterialButton
    private lateinit var btnFilterAttacks: MaterialButton
    private lateinit var btnFilterDefense: MaterialButton

    private lateinit var recyclerEvents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var fabClear: FloatingActionButton

    // Datos
    private val allEvents = mutableListOf<SecurityEvent>()
    private lateinit var eventAdapter: EventAdapter
    private var currentFilter = FilterType.ALL

    enum class FilterType {
        ALL, ATTACKS, DEFENSE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_log)

        initViews()
        setupToolbar()
        setupRecyclerView()
        loadMockData()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        tvAttacksSent = findViewById(R.id.tvAttacksSent)
        tvAttacksBlocked = findViewById(R.id.tvAttacksBlocked)
        tvTotalEvents = findViewById(R.id.tvTotalEvents)

        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterAttacks = findViewById(R.id.btnFilterAttacks)
        btnFilterDefense = findViewById(R.id.btnFilterDefense)

        recyclerEvents = findViewById(R.id.recyclerEvents)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        fabClear = findViewById(R.id.fabClear)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(allEvents)
        recyclerEvents.layoutManager = LinearLayoutManager(this)
        recyclerEvents.adapter = eventAdapter
    }

    private fun setupListeners() {
        btnFilterAll.setOnClickListener {
            applyFilter(FilterType.ALL)
        }

        btnFilterAttacks.setOnClickListener {
            applyFilter(FilterType.ATTACKS)
        }

        btnFilterDefense.setOnClickListener {
            applyFilter(FilterType.DEFENSE)
        }

        fabClear.setOnClickListener {
            showClearDialog()
        }
    }

    private fun applyFilter(filter: FilterType) {
        currentFilter = filter

        // Resetear colores de botones
        resetButtonColors()

        // Filtrar eventos
        val filteredEvents = when (filter) {
            FilterType.ALL -> {
                btnFilterAll.setBackgroundColor(getColor(R.color.gray_light))
                allEvents
            }
            FilterType.ATTACKS -> {
                btnFilterAttacks.setBackgroundColor(getColor(R.color.red_team_light))
                allEvents.filter {
                    it.type == EventType.ATTACK_SENT || it.type == EventType.INTRUSION_DETECTED
                }
            }
            FilterType.DEFENSE -> {
                btnFilterDefense.setBackgroundColor(getColor(R.color.blue_team_light))
                allEvents.filter {
                    it.type == EventType.ATTACK_BLOCKED ||
                            it.type == EventType.DEFENSE_ACTIVATED ||
                            it.type == EventType.FIREWALL_BLOCK
                }
            }
        }

        eventAdapter.updateEvents(filteredEvents)
        updateEmptyState(filteredEvents.isEmpty())
    }

    private fun resetButtonColors() {
        btnFilterAll.setBackgroundColor(getColor(android.R.color.transparent))
        btnFilterAttacks.setBackgroundColor(getColor(android.R.color.transparent))
        btnFilterDefense.setBackgroundColor(getColor(android.R.color.transparent))
    }

    private fun updateUI() {
        val attacksSent = allEvents.count { it.type == EventType.ATTACK_SENT }
        val attacksBlocked = allEvents.count {
            it.type == EventType.ATTACK_BLOCKED || it.type == EventType.FIREWALL_BLOCK
        }

        tvAttacksSent.text = attacksSent.toString()
        tvAttacksBlocked.text = attacksBlocked.toString()
        tvTotalEvents.text = allEvents.size.toString()

        updateEmptyState(allEvents.isEmpty())
        applyFilter(currentFilter)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerEvents.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
        } else {
            recyclerEvents.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
        }
    }

    private fun showClearDialog() {
        if (allEvents.isEmpty()) {
            Toast.makeText(this, "No hay eventos para limpiar", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Limpiar Registro")
            .setMessage("¿Estás seguro de que quieres eliminar todos los eventos?")
            .setPositiveButton("Sí") { _, _ ->
                clearAllEvents()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun clearAllEvents() {
        allEvents.clear()
        updateUI()
        Toast.makeText(this, "Eventos eliminados", Toast.LENGTH_SHORT).show()
    }

    private fun loadMockData() {
        // Cargar eventos globales primero
        val globalEvents = getEvents()
        if (globalEvents.isNotEmpty()) {
            allEvents.addAll(globalEvents)
        }

        // Si no hay eventos, cargar ejemplos
        if (allEvents.isEmpty()) {
            allEvents.addAll(listOf(
                SecurityEvent(
                    type = EventType.DEFENSE_ACTIVATED,
                    title = "Bienvenido a Sentinel Core",
                    description = "Sistema iniciado. Usa Red Team para atacar y Blue Team para defenderte.",
                    sourceIp = "Sistema",
                    status = EventStatus.SUCCESS,
                    timestamp = System.currentTimeMillis()
                )
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        // Recargar eventos cuando volvamos a esta pantalla
        allEvents.clear()
        allEvents.addAll(getEvents())
        updateUI()
    }

    companion object {
        private val globalEvents = mutableListOf<SecurityEvent>()

        fun addEvent(event: SecurityEvent) {
            globalEvents.add(0, event)
        }

        fun getEvents(): List<SecurityEvent> {
            return globalEvents.toList()
        }

        fun clearEvents() {
            globalEvents.clear()
        }
    }
}