package agenda.x.voz.ui.views.alarm_views

import agenda.x.voz.R
import agenda.x.voz.core.notifications.MyAlarmManager
import agenda.x.voz.data.model.AlarmModel
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import agenda.x.voz.databinding.FragmentEditAlarmBinding
import agenda.x.voz.domain.model.Alarm
import agenda.x.voz.domain.model.toDomain
import agenda.x.voz.ui.viewModels.EditAlarmViewModel
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

@AndroidEntryPoint
class EditAlarmFragment : Fragment() {
    private lateinit var binding: FragmentEditAlarmBinding
    private val editAlarmViewModel: EditAlarmViewModel by viewModels()
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private var filesDir: File? = null
    private lateinit var audioFilePath: File
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var selectedHour = 0
    private var selectedMinute = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEditAlarmBinding.inflate(layoutInflater)
        ActivityCompat.requestPermissions(
            requireActivity(),
            permissions,
            REQUEST_RECORD_AUDIO_PERMISSION
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        filesDir = requireContext().filesDir
        val alarm = arguments?.getParcelable<AlarmModel>("alarm")
        editAlarmViewModel.setAlarm(alarm!!.toDomain())
        editAlarmViewModel.alarmToEditAlarm.observe(viewLifecycleOwner) {
            setTimePicker(it)
            setDatePicker(it)
            binding.etiquetaEditText.setText(it.name)
            binding.repeatSwitch.isChecked = it.repeat
            binding.repeatDaySwitch.isChecked = it.repeatDay
            audioFilePath = Path(it.audioFilePath).toFile()
            onClickListeners(it)
        }
        onClickCancelButton()
        onTimeChange()
    }

    private fun onClickListeners(alarm: Alarm) {
        onClickSaveButton(alarm)
        onClickPlayButton()
        onClickRecordButton()
        onClickRepeatWeekSwitch()
        onClickRepeatDaySwitch()
    }

    private fun onClickRepeatWeekSwitch() {
        binding.repeatSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.repeatDaySwitch.isChecked = false
            }
        }
    }

    private fun onClickRepeatDaySwitch() {
        binding.repeatDaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                binding.repeatSwitch.isChecked = false
        }
    }

    private fun onClickPlayButton() {
        binding.playButton.setOnClickListener {
            isPlaying = !isPlaying
            onPlay(isPlaying)
        }
    }

    private fun onClickRecordButton() {
        binding.recordButton.setOnClickListener {
            isRecording = !isRecording
            val alarmName = binding.etiquetaEditText.text.toString()
            if (alarmName.isEmpty())
                Toast.makeText(
                    requireContext(),
                    "Ponle un nombre a la tarea primero!",
                    Toast.LENGTH_SHORT
                ).show()
            else {
                onRecord(isRecording, alarmName)
            }
        }
    }

    private fun onClickCancelButton() {
        binding.cancelButton.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setDatePicker(alarm: Alarm) {
        binding.dayPicker.minValue = 1
        binding.dayPicker.maxValue = 31
        binding.monthPicker.minValue = 1
        binding.monthPicker.maxValue = 12
        binding.yearPicker.minValue = 2023
        binding.yearPicker.maxValue = 2100
        binding.dayPicker.value = alarm.day!!
        binding.monthPicker.value = alarm.month!!
        binding.yearPicker.value = alarm.year!!
    }

    private fun setTimePicker(alarm: Alarm) {
        binding.timePicker.hour = alarm.hour
        binding.timePicker.minute = alarm.minute
    }

    private fun onTimeChange() {
        binding.timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
        }
    }

    private fun onClickSaveButton(alarm: Alarm) {
        binding.saveButton.setOnClickListener {
            val alarmEdited = getAlarmProperties(alarm)
            editAlarmViewModel.editMyAlarm(alarmEdited)
            val calendar = getAlarmDate(alarm)
            val notificationId = alarmEdited.id.toInt()
            cancelNotification(requireContext(), notificationId)
            MyAlarmManager.notification1HourBefore(
                alarmEdited,
                requireActivity(),
                calendar,
                alarmEdited.hour - 1
            )
            MyAlarmManager.notificationInTimePassed(alarmEdited, requireActivity(), calendar)
            Toast.makeText(requireContext(), "Tarea editada con éxito", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_editAlarmsFragment_to_calendarFragment)
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun getAlarmDate(alarm: Alarm): Calendar {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, alarm.year!!)
        calendar.set(Calendar.MONTH, alarm.month!!)
        calendar.set(Calendar.DAY_OF_MONTH, alarm.day!!)
        calendar.set(Calendar.HOUR_OF_DAY, alarm.hour)
        calendar.set(Calendar.MINUTE, alarm.minute)
        return calendar
    }

    private fun getAlarmProperties(alarm: Alarm) = Alarm(
        alarm.id,
        binding.etiquetaEditText.text.toString(),
        binding.dayPicker.value,
        binding.monthPicker.value,
        binding.yearPicker.value,
        selectedHour,
        selectedMinute,
        binding.repeatSwitch.isChecked,
        binding.repeatDaySwitch.isChecked,
        alarm.complete,
        audioFilePath.path
    )

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) onStop()
    }

    private fun onRecord(start: Boolean, alarmName: String) =
        if (start) {
            binding.playButton.visibility = View.GONE
            binding.recordButtonLabel.text = "Grabando..."
            binding.recordButton.text = resources.getString(R.string.cuadrado)
            audioFilePath = File(filesDir, alarmName)
            audioFilePath.toPath().deleteIfExists()
            audioFilePath.createNewFile()
            startRecording()
        } else {
            if (audioFilePath.exists()) {
                binding.recordButtonLabel.text = "Capturado"
                binding.playButton.visibility = View.VISIBLE
            } else {
                binding.recordButtonLabel.text = "Grabar"
                binding.playButton.visibility = View.GONE
            }
            binding.recordButton.text = resources.getString(R.string.red_circle)
            stopRecording()
        }


    private fun onPlay(start: Boolean) =
        if (start) {
            if (audioFilePath.exists()) {
                binding.playButton.setImageResource(R.drawable.ic_stop_button)
                startPlaying()
                player?.setOnPreparedListener {
                    binding.progressBar.max = it.duration
                    val updateHandler = Handler()
                    val updateRunnable = object : Runnable {
                        override fun run() {
                            val currentPosition = player?.currentPosition
                            binding.progressBar.progress = currentPosition ?: 0
                            updateHandler.postDelayed(this, 100) // Actualizar cada 100ms
                        }
                    }
                    updateHandler.postDelayed(updateRunnable, 0)
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "No hay audio. Para grabar uno ves a editar tarea",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            binding.playButton.setImageResource(R.drawable.ic_play_button)
            binding.progressBar.progress = 0
            stopPlaying()
        }

    private fun startPlaying() {
        player = MediaPlayer().apply {
            try {
                setDataSource(audioFilePath.path)
                prepare()
                setOnCompletionListener {
                    this@EditAlarmFragment.isPlaying = !this@EditAlarmFragment.isPlaying
                    onPlay(this@EditAlarmFragment.isPlaying)
                }
                start()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }

    private fun stopPlaying() {
        player?.release()
        player = null
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFilePath.path)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
            start()
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null
        player?.release()
        player = null
    }

    companion object {
        private val currentDate = Calendar.getInstance()
        private const val LOG_TAG = "AudioRecordTest"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
}