package com.callrecorder.cleaner

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.cleaner.adapter.RecordingAdapter
import com.callrecorder.cleaner.engine.SpeechAnalyzer
import com.callrecorder.cleaner.model.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var startDateButton: Button
    private lateinit var endDateButton: Button
    private lateinit var keywordInput: EditText
    private lateinit var selectFolderButton: Button
    private lateinit var scanButton: Button
    private lateinit var deleteButton: Button
    private lateinit var statusText: TextView
    private lateinit var recordingsRecyclerView: RecyclerView
    private lateinit var recordingAdapter: RecordingAdapter

    private var selectedStartDate: Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 1, 0, 0, 0)
    }
    private var selectedEndDate: Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 15, 23, 59, 59)
    }

    private var selectedFolderUri: Uri? = null

    private val recordings = mutableListOf<Recording>()
    private val matchedRecordings = mutableListOf<Recording>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        updateDateButtons()
    }

    private fun initViews() {
        startDateButton = findViewById(R.id.startDateButton)
        endDateButton = findViewById(R.id.endDateButton)
        keywordInput = findViewById(R.id.keywordInput)
        selectFolderButton = findViewById(R.id.selectFolderButton)
        scanButton = findViewById(R.id.scanButton)
        deleteButton = findViewById(R.id.deleteButton)
        statusText = findViewById(R.id.statusText)
        recordingsRecyclerView = findViewById(R.id.recordingsRecyclerView)

        recordingAdapter = RecordingAdapter(recordings)
        recordingsRecyclerView.layoutManager = LinearLayoutManager(this)
        recordingsRecyclerView.adapter = recordingAdapter

        keywordInput.setText("Paytm, Sound Box, Soundbox")
    }

    private fun setupClickListeners() {
        startDateButton.setOnClickListener { showDatePicker(true) }
        endDateButton.setOnClickListener { showDatePicker(false) }
        selectFolderButton.setOnClickListener { openFolderPicker() }
        scanButton.setOnClickListener { startScan() }
        deleteButton.setOnClickListener { confirmAndDelete() }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) selectedStartDate else selectedEndDate
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val newDate = Calendar.getInstance().apply {
                    set(year, month, day, if (isStartDate) 0 else 23, if (isStartDate) 0 else 59, if (isStartDate) 0 else 59)
                }
                if (isStartDate) selectedStartDate = newDate else selectedEndDate = newDate
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

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        startActivityForResult(intent, FOLDER_PICKER_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FOLDER_PICKER_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                selectedFolderUri = uri
                val folderName = DocumentFile.fromTreeUri(this, uri)?.name ?: "Selected"
                statusText.text = "Folder selected: $folderName"
                scanButton.isEnabled = true
            }
        }
    }

    private fun startScan() {
        if (selectedFolderUri == null) {
            Toast.makeText(this, "Please select recordings folder first", Toast.LENGTH_SHORT).show()
            return
        }

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
                val recordingsInRange = withContext(Dispatchers.IO) {
                    findRecordingsInRange()
                }

                recordings.clear()
                recordings.addAll(recordingsInRange)
                recordingAdapter.notifyDataSetChanged()

                if (recordingsInRange.isEmpty()) {
                    statusText.text = "No recordings found in selected folder and date range"
                    scanButton.isEnabled = true
                    return@launch
                }

                statusText.text = "Found ${recordingsInRange.size} recordings. Analyzing..."

                val analyzer = SpeechAnalyzer()
                analyzer.initialize(this@MainActivity)

                val matched = withContext(Dispatchers.IO) {
                    analyzer.findRecordingsWithKeywords(recordingsInRange, keywords) { progress ->
                        runOnUiThread {
                            statusText.text = "Analyzing: $progress%"
                        }
                    }
                }

                matchedRecordings.clear()
                matchedRecordings.addAll(matched)
                recordingAdapter.updateMatched(matched.map { it.uri.toString() })

                statusText.text = "Found ${matched.size} recordings with keywords"
                deleteButton.isEnabled = matched.isNotEmpty()
                scanButton.isEnabled = true

            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Scan failed: ${e.message}", Toast.LENGTH_LONG).show()
                scanButton.isEnabled = true
            }
        }
    }

    private fun findRecordingsInRange(): List<Recording> {
        val results = mutableListOf<Recording>()
        val folderUri = selectedFolderUri ?: return results

        val treeDoc = DocumentFile.fromTreeUri(this, folderUri) ?: return results
        val children = treeDoc.listFiles()

        for (child in children) {
            if (!child.isFile) continue

            val name = child.name ?: continue
            val extension = name.substringAfterLast(".", "").lowercase()
            if (extension !in listOf("m4a", "mp3", "wav", "amr", "3gp", "ogg", "aac", "flac")) continue

            val lastModified = child.lastModified()
            val fileDate = Calendar.getInstance().apply {
                timeInMillis = lastModified
            }

            // Also try parsing date from filename
            val filenameDate = parseDateFromFilename(name)

            val effectiveDate = filenameDate ?: fileDate

            if (effectiveDate.timeInMillis >= selectedStartDate.timeInMillis &&
                effectiveDate.timeInMillis <= selectedEndDate.timeInMillis
            ) {
                results.add(Recording(child.uri, name, child.length(), effectiveDate))
            }
        }

        return results
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
            .setMessage("Delete ${matchedRecordings.size} recordings? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteMatchedRecordings() }
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
                        try {
                            val doc = DocumentFile.fromSingleUri(this@MainActivity, recording.uri)
                            if (doc?.delete() == true) {
                                deletedCount++
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                recordings.removeAll(matchedRecordings)
                matchedRecordings.clear()
                recordingAdapter.notifyDataSetChanged()

                statusText.text = "Deleted $deletedCount recordings"
                Toast.makeText(this@MainActivity, "Deleted $deletedCount recordings", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                statusText.text = "Delete failed: ${e.message}"
            } finally {
                deleteButton.isEnabled = false
            }
        }
    }

    companion object {
        private const val FOLDER_PICKER_CODE = 1001
    }
}
