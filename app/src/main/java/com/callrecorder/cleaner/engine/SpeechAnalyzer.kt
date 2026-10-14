package com.callrecorder.cleaner.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.callrecorder.cleaner.model.Recording
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechAnalyzer {

    private var model: Model? = null

    /**
     * Initialize Vosk model - call once from Activity
     */
    fun initialize(context: Context): Boolean {
        return try {
            // Model path should be: /storage/emulated/0/vosk-model-small-en-us-0.15
            val modelPath = "/storage/emulated/0/vosk-model-small-en-us-0.15"
            model = Model(modelPath)
            model != null
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Find recordings that contain any of the keywords
     */
    fun findRecordingsWithKeywords(
        recordings: List<Recording>,
        keywords: List<String>,
        onProgress: (Int) -> Unit
    ): List<Recording> {
        val matched = mutableListOf<Recording>()
        
        if (model == null) {
            // If model not loaded, try simple filename matching as fallback
            return recordings.filter { recording ->
                val hasKeyword = keywords.any { keyword ->
                    recording.file.name.lowercase().contains(keyword.lowercase())
                }
                if (hasKeyword) {
                    recording.isMatched = true
                }
                hasKeyword
            }
        }
        
        recordings.forEachIndexed { index, recording ->
            try {
                val recognizedText = recognizeAudio(recording.file)
                val lowerText = recognizedText.lowercase()
                
                val hasKeyword = keywords.any { keyword ->
                    lowerText.contains(keyword.lowercase())
                }
                
                if (hasKeyword) {
                    recording.isMatched = true
                    matched.add(recording)
                }
                
                val progress = ((index + 1) * 100) / recordings.size
                onProgress(progress)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return matched
    }

    /**
     * Recognize speech from audio file
     */
    private fun recognizeAudio(file: java.io.File): String {
        val model = this.model ?: return ""
        
        return try {
            val samples = decodeAudioToPcm(file)
            if (samples.isEmpty()) return ""
            
            val recognizer = Recognizer(model, 16000.0f)
            recognizer.acceptWaveForm(samples, samples.size)
            val result = recognizer.finalResult
            recognizer.close()
            
            // Extract text from JSON result
            extractTextFromVoskResult(result)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Decode audio file to 16-bit PCM samples
     */
    private fun decodeAudioToPcm(file: java.io.File): ShortArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.path)
        
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
            
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }
        
        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            return ShortArray(0)
        }
        
        extractor.selectTrack(audioTrackIndex)
        
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return ShortArray(0)
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()
        
        val samples = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        
        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufferIndex, 0, sampleSize,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }
            
            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                val dataSize = bufferInfo.size
                
                val byteArray = ByteArray(dataSize)
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.get(byteArray)
                
                val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
                while (buffer.remaining() >= 2) {
                    samples.add(buffer.short)
                }
                
                decoder.releaseOutputBuffer(outputBufferIndex, false)
                
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }
        
        decoder.stop()
        decoder.release()
        extractor.release()
        
        return samples.toShortArray()
    }

    /**
     * Extract text from Vosk JSON result
     */
    private fun extractTextFromVoskResult(json: String): String {
        // Vosk returns: {"text":"hello world"}
        return try {
            val start = json.indexOf("\"text\":\"") + 8
            val end = json.indexOf("\"", start)
            if (start > 7 && end > start) {
                json.substring(start, end)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
