package com.callrecorder.cleaner

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.cleaner.model.Recording
import com.callrecorder.cleaner.adapter.RecordingAdapter
import com.callrecorder.cleaner.engine.AudioProcessor
import com.callrecorder.cleaner.engine.SpeechAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var startDateButton: Button
    private lateinit var endDateButton: Button
    private lateinit var keywordInput: EditText
    private lateinit var scanButton: Button
    private lateinit var deleteButton: Button
    private lateinit var statusText: TextView
    private lateinit var recordingsRecyclerView: RecyclerView
    private lateinit var recordingAdapter: RecordingAdapter
    
    private var selectedStartDate: Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 1)
    }
    private var selectedEndDate: Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 15)
    }
    
    private val recordings = mutableListOf<Recording>()
    private val matchedRecordings = mutableListOf<Recording>()
    
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        updateDateButtons()
        checkPermissions()
    }

    private fun initViews() {
        startDateButton = findViewById(R.id.startDateButton)
        endDateButton = findViewById(R.id.endDateButton)
        keywordInput = findViewById(R.id.keywordInput)
        scanButton = findViewById(R.id.scanButton)
        deleteButton = findViewById(R.id.deleteButton)
        statusText = findViewById(R.id.statusText)
        recordingsRecyclerView = findViewById(R.id.recordingsRecyclerView)
        
        recordingAdapter = RecordingAdapter(recordings)
        recordingsRecyclerView.layoutManager = LinearLayoutManager(this)
        recordingsRecyclerView.adapter = recordingAdapter
        
        // Default keywords
        keywordInput.setText("Paytm, Sound Box, Soundbox")
    }

    private fun setupClickListeners() {
        startDateButton.setOnClickListener { showDatePicker(true) }
        endDateButton.setOnClickListener { showDatePicker(false) }
        scanButton.setOnClickListener { startScan() }
        deleteButton.setOnClickListener { confirmAndDelete() }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) selectedStartDate else selectedEndDate
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val newDate = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                }
                if (isStartDate) {
                    selectedStartDate = newDate
                } else {
                    selectedEndDate = newDate
                }
                updateDateButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateButtons() {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        startDateButton.text = "From: ${sdf.format(selectedStartDate.time)}"
        endDateButton.text = "To: ${sdf.format(selectedEndDate.time)}"
    }

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions required to access recordings", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startScan() {
        val keywords = keywordInput.text.toString()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        
        if (keywords.isEmpty()) {
            Toast.makeText(this, "Please enter keywords", Toast.LENGTH_SHORT).show()
            return
        }

        statusText.text = "Scanning recordings..."
        scanButton.isEnabled = false
        deleteButton.isEnabled = false

        lifecycleScope.launch {
            try {
                // Step 1: Find recordings in date range
                val recordingsInRange = withContext(Dispatchers.IO) {
                    findRecordingsInRange()
                }
                
                recordings.clear()
                recordings.addAll(recordingsInRange)
                recordingAdapter.notifyDataSetChanged()
                
                if (recordingsInRange.isEmpty()) {
                    statusText.text = "No recordings found in selected date range"
                    scanButton.isEnabled = true
                    return@launch
                }
                
                statusText.text = "Found ${recordingsInRange.size} recordings. Analyzing..."
                
                // Step 2: Analyze each recording for keywords
                val analyzer = SpeechAnalyzer()
                analyzer.initialize(this@MainActivity)
                
                val matched = withContext(Dispatchers.IO) {
                    analyzer.findRecordingsWithKeywords(recordingsInRange, keywords) { progress ->
                        lifecycleScope.launch {
                            statusText.text = "Analyzing: $progress%"
                        }
                    }
                }
                
                matchedRecordings.clear()
                matchedRecordings.addAll(matched)
                recordingAdapter.updateMatched(matched.map { it.file.absolutePath })
                
                statusText.text = "Found ${matched.size} recordings with keywords. Review and delete."
                deleteButton.isEnabled = matched.isNotEmpty()
                
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Scan failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                scanButton.isEnabled = true
            }
        }
    }

    private fun findRecordingsInRange(): List<Recording> {
        val recordingsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Recordings/Call"
        )
        
        // Also check common alternatives
        val possiblePaths = listOf(
            "/storage/emulated/0/Recordings/Call",
            "/storage/emulated/0/DCIM/Recordings/Call",
            "/storage/emulated/0/Music/Recordings/Call",
            "/storage/emulated/0/Sounds/Call recordings"
        )
        
        var folder: File? = null
        for (path in possiblePaths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                folder = dir
                break
            }
        }
        
        folder ?: return emptyList()
        
        return folder.listFiles { file ->
            file.extension.lowercase() in listOf("m4a", "mp3", "wav", "amr", "3gp", "ogg")
        }?.mapNotNull { file ->
            // Parse date from filename or use file modification time
            val fileDate = parseDateFromFilename(file.name) ?: Calendar.getInstance().apply {
                timeInMillis = file.lastModified()
            }
            
            // Check if date is in range
            if (fileDate.timeInMillis >= selectedStartDate.timeInMillis &&
                fileDate.timeInMillis <= selectedEndDate.timeInMillis
            ) {
                Recording(file, fileDate)
            } else {
                null
            }
        } ?: emptyList()
    }

    private fun parseDateFromFilename(filename: String): Calendar? {
        // Format: Call Name_YYMMDD_HHMMSS.m4a
        val regex = Regex("_(\\d{6})_(\\d{6})\\.")
        val match = regex.find(filename) ?: return null
        
        return try {
            val dateStr = match.groupValues[1]
            val year = dateStr.substring(0, 2).toInt() + 2000
            val month = dateStr.substring(2, 4).toInt() - 1
            val day = dateStr.substring(4, 6).toInt()
            
            Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun confirmAndDelete() {
        if (matchedRecordings.isEmpty()) return
        
        AlertDialog.Builder(this)
            .setTitle("Delete Recordings")
            .setMessage("Are you sure you want to delete ${matchedRecordings.size} recordings? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteMatchedRecordings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMatchedRecordings() {
        statusText.text = "Deleting..."
        deleteButton.isEnabled = false

        lifecycleScope.launch {
            try {
                var deletedCount = 0
                withContext(Dispatchers.IO) {
                    matchedRecordings.forEach { recording ->
                        if (recording.file.delete()) {
                            deletedCount++
                        }
                    }
                }
                
                recordings.removeAll(matchedRecordings)
                matchedRecordings.clear()
                recordingAdapter.notifyDataSetChanged()
                
                statusText.text = "Deleted $deletedCount recordings successfully"
                Toast.makeText(
                    this@MainActivity,
                    "Deleted $deletedCount recordings",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                statusText.text = "Delete failed: ${e.message}"
                Toast.makeText(this@MainActivity, "Delete failed", Toast.LENGTH_SHORT).show()
            } finally {
                deleteButton.isEnabled = false
            }
        }
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
    }
}
