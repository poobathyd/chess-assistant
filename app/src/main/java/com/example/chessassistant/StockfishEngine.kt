package com.example.chessassistant

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import android.content.Context

class StockfishEngine {

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null

    fun startEngine(context: Context) {
        try {
            val binaryFile = java.io.File(context.filesDir, "stockfish")
            if (!binaryFile.exists()) {
                installBinaryFromAssets(context, binaryFile)
            }
            if (!binaryFile.canExecute()) {
                binaryFile.setExecutable(true)
            }

            val processBuilder = ProcessBuilder(binaryFile.absolutePath)
            process = processBuilder.start()
            
            writer = OutputStreamWriter(process!!.outputStream)
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            sendCommand("uci")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun installBinaryFromAssets(context: Context, outFile: java.io.File) {
        try {
            context.assets.open("stockfish").use { inputStream ->
                java.io.FileOutputStream(outFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendCommand(command: String) {
        try {
            writer?.write("$command\n")
            writer?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBestMove(fen: String, callback: (String) -> Unit) {
        Thread {
            sendCommand("position fen $fen")
            sendCommand("go movetime 1000") // Analyze for 1 second

            var line: String?
            try {
                while (reader?.readLine().also { line = it } != null) {
                    if (line!!.startsWith("bestmove")) {
                        val move = line!!.split(" ")[1]
                        callback(move)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stopEngine() {
        try {
            sendCommand("quit")
            process?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
