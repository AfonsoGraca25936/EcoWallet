package pt.ipt.dam.ecowallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CalendarView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        val calendarView = findViewById<CalendarView>(R.id.calendarView)
        val btnViewDay = findViewById<Button>(R.id.btnViewDay)

        // Data de hoje por defeito
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        selectedDate = sdf.format(Calendar.getInstance().time)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // O mês começa em 0 (Janeiro = 0)
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDate = sdf.format(cal.time)
        }

        btnViewDay.setOnClickListener {
            val intent = Intent(this, DayReportActivity::class.java)
            intent.putExtra("SELECTED_DATE", selectedDate)
            startActivity(intent)
        }
    }
}