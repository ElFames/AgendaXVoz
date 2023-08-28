package agenda.x.voz.data.repositories

import agenda.x.voz.data.database.dao.AlarmDao
import agenda.x.voz.data.database.entities.AlarmEntity
import agenda.x.voz.data.database.entities.toDatabase
import agenda.x.voz.domain.model.Alarm
import agenda.x.voz.domain.model.toDomain
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class AlarmRepository @Inject constructor(
    private val dao: AlarmDao
) {
    var alarms = mutableListOf<Alarm>()

    init {
        runBlocking {
            alarms = getAllAlarms()
            checkRepeatingAlarms()
        }
    }

    fun insertNewAlarm(alarmData: MutableMap<String, Any>): AlarmEntity? {
        var alarm: AlarmEntity? = null
        val insertAlarm = CoroutineScope(Dispatchers.IO).async {
            val alarmEntity = AlarmEntity(
                alarmData["name"]!!.toString(),
                alarmData["day"]!!.toString().toInt(),
                alarmData["month"]!!.toString().toInt(),
                alarmData["year"]!!.toString().toInt(),
                alarmData["hour"]!!.toString().toInt(),
                alarmData["minute"]!!.toString().toInt(),
                alarmData["repeat"]!!.toString().toBoolean(),
                alarmData["complete"]!!.toString().toBoolean(),
                alarmData["audioFilePath"]!!.toString()
            )
            dao.insert(alarmEntity)
        }
        CoroutineScope(Dispatchers.IO).launch {
            insertAlarm.await()
            alarm = dao.getAlarmByDate(
                alarmData["day"]!!.toString().toInt(),
                alarmData["month"]!!.toString().toInt(),
                alarmData["year"]!!.toString().toInt(),
                alarmData["hour"]!!.toString().toInt(),
                alarmData["minute"]!!.toString().toInt())
        }
        return alarm
    }

    fun getAlarmsFromDay(day: Int, month: Int, year: Int): MutableList<Alarm> {
        val myAlarms = mutableListOf<Alarm>()
        alarms.forEach {
            if (it.day == day && it.month == month && it.year == year)
                myAlarms.add(it)
        }
        return myAlarms
    }

    fun getTodayAlarms(): MutableList<Alarm> {
        val todayAlarms = mutableListOf<Alarm>()
        alarms.forEach {
            if (it.day == currentDay && it.month == currentMonth && it.year == currentYear)
                todayAlarms.add(it)
        }
        return todayAlarms
    }

    fun getTomorrowAlarms(): MutableList<Alarm> {
        val tomorrowAlarms = mutableListOf<Alarm>()
        alarms.forEach {
            if (isTomorrowAlarm(it)) tomorrowAlarms.add(it)
        }
        return tomorrowAlarms
    }

    private suspend fun getAllAlarms(): MutableList<Alarm> {
        return dao.getAllAlarms().map { it.toDomain() }.toMutableList()
    }

    private fun checkRepeatingAlarms() {
        var dateStr: String
        var timeStr: String
        alarms.forEach {
            if (it.repeat) {
                dateStr = "${it.day}/${it.month}/${it.year}"
                timeStr = "${it.hour}:${it.minute}"
                if (isDateTimePassed(dateStr, timeStr))
                    updateTimeFromAlarmRepeating(it.toDatabase())
            }
        }
    }

    private fun isTomorrowAlarm(alarm: Alarm): Boolean {
        return when(currentMonth) {
            1,3,5,7,8,10,12 -> checkDay(31,alarm)
            4,6,9,11 -> checkDay(30,alarm)
            else -> {
                if (isBisiestoYear(currentYear))
                    checkDay(29,alarm)
                else checkDay(28,alarm)
            }
        }
    }

    private fun checkDay(limitDays: Int, alarm: Alarm): Boolean {
        return if (currentDay == limitDays) {
            if (currentMonth == 12) {
                alarm.day == 1 && alarm.month == 1 && alarm.year == currentYear + 1
            } else {
                alarm.day == 1 && alarm.month == currentMonth + 1 && alarm.year == currentYear
            }
        } else if (currentMonth == 12) {
            alarm.day == currentDay + 1 && alarm.month == currentMonth && alarm.year == currentYear
        } else {
            alarm.day == currentDay + 1 && alarm.year == currentYear && alarm.month == currentMonth
        }
    }

    private fun isBisiestoYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    private fun updateTimeFromAlarmRepeating(alarm: AlarmEntity) {
        val currentDate = Calendar.getInstance()
        currentDate.apply {
            set(Calendar.HOUR, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.YEAR, alarm.year!!)
            set(Calendar.MONTH, alarm.month!!)
            set(Calendar.DAY_OF_MONTH, alarm.day!!)
            add(Calendar.DAY_OF_YEAR, 6)
        }
        val day = currentDate.get(Calendar.DAY_OF_MONTH)
        val month = currentDate.get(Calendar.MONTH)
        val year = currentDate.get(Calendar.YEAR)
        alarm.day = day
        alarm.month = month
        alarm.year = year
        CoroutineScope(Dispatchers.IO).launch {
            dao.insert(alarm)
        }
    }
    private fun isDateTimePassed(dateStr: String, timeStr: String): Boolean {
        val dateTimePattern = "dd/MM/yyyy HH:mm"
        val currentDate = Calendar.getInstance().time

        val dateFormat = SimpleDateFormat(dateTimePattern, Locale.getDefault())
        val dateTime = dateFormat.parse("$dateStr $timeStr") ?: return false

        return dateTime.before(currentDate)
    }

    fun updateAlarm(alarm: Alarm) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.insert(alarm.toDatabase())
        }
    }

    companion object {
        private val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    }
}