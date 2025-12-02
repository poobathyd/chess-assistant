package com.example.chessassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "OverlayServiceChannel"
        private const val TAG = "OverlayService"
        private const val ANALYSIS_INTERVAL_MS = 2500L  // Analyze every 2.5 seconds
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    
    // UI elements
    private lateinit var tvBestMove: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvConfidence: TextView
    
    // Detection components
    private var yoloDetector: YoloDetector? = null
    private var boardAnalyzer: BoardAnalyzer? = null
    private var stockfishEngine: StockfishEngine? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    
    // Throttling
    private var lastAnalysisTime = 0L
    private var isAnalyzing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OverlayService onCreate")
        
        createNotificationChannel()
        startForeground(1, createNotification())

        // Initialize OpenCV
        if (!OpenCVLoaderUtil.initOpenCV(this)) {
            Log.e(TAG, "Failed to initialize OpenCV")
            stopSelf()
            return
        }

        // Initialize YOLO detector
        yoloDetector = YoloDetector(this)
        if (!yoloDetector!!.loadModel()) {
            Log.e(TAG, "Failed to load YOLO model")
            updateStatus("✗ Model load failed")
            // Continue anyway to show UI
        } else {
            Log.i(TAG, "YOLO model loaded successfully")
        }

        // Initialize board analyzer
        boardAnalyzer = BoardAnalyzer(yoloDetector!!)
        
        // Initialize Stockfish
        stockfishEngine = StockfishEngine()
        stockfishEngine?.startEngine(this)

        // Setup overlay UI
        setupOverlay()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.service_overlay, null)

        // Get UI elements
        tvBestMove = overlayView.findViewById(R.id.tvBestMove)
        tvStatus = overlayView.findViewById(R.id.tvStatus)
        tvConfidence = overlayView.findViewById(R.id.tvConfidence)

        // Set initial text
        tvBestMove.text = "Initializing..."
        tvStatus.text = "Ready"
        tvConfidence.text = "Waiting for board..."

        // Configure window parameters
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Position overlay at center-right of screen
        params.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        params.x = 20  // 20px from right edge
        params.y = 0

        // Setup drag listener
        setupDragListener()

        // Add overlay to window
        windowManager.addView(overlayView, params)
        Log.i(TAG, "Overlay view added")
    }

    private fun setupDragListener() {
        overlayView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (initialTouchX - event.rawX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Handle Screen Capture setup
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != 0 && resultData != null) {
            startScreenCapture(resultCode, resultData)
        }

        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        Log.i(TAG, "Starting screen capture")
        
        screenCaptureManager = ScreenCaptureManager(this, resultCode, resultData)
        
        screenCaptureManager?.startCapture { bitmap ->
            // Throttle analysis to avoid overloading the system
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
                return@startCapture  // Skip this frame
            }
            
            if (isAnalyzing) {
                return@startCapture  // Previous analysis still running
            }
            
            lastAnalysisTime = currentTime
            isAnalyzing = true
            
            // Run analysis on background thread
            analysisScope.launch {
                try {
                    analyzeBoard(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during analysis", e)
                    mainHandler.post {
                        updateStatus("✗ Analysis error")
                    }
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    private suspend fun analyzeBoard(bitmap: android.graphics.Bitmap) {
        val analyzer = boardAnalyzer ?: return
        
        // Analyze bitmap to get FEN
        val result = analyzer.analyzeBitmap(bitmap)
        
        // Update UI on main thread
        mainHandler.post {
            if (result.boardFound) {
                tvStatus.text = "✓ Board detected"
                tvConfidence.text = "${result.detectedPieces}/${result.expectedPieces} pieces | " +
                        "${(result.confidence * 100).toInt()}% | ${result.processingTimeMs}ms"
                
                Log.d(TAG, "FEN: ${result.fen}")
                
                // Get best move from Stockfish
                stockfishEngine?.getBestMove(result.fen) { bestMove ->
                    mainHandler.post {
                        tvBestMove.text = "Best: $bestMove"
                    }
                }
            } else {
                tvStatus.text = "✗ No board detected"
                tvConfidence.text = "Move board into view"
                tvBestMove.text = "Waiting..."
            }
        }
    }

    private fun updateStatus(status: String) {
        mainHandler.post {
            tvStatus.text = status
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "OverlayService onDestroy")
        
        // Cancel all coroutines
        analysisScope.cancel()
        
        // Stop screen capture
        screenCaptureManager?.stopCapture()
        
        // Stop Stockfish
        stockfishEngine?.stopEngine()
        
        // Close YOLO detector
        yoloDetector?.close()
        
        // Remove overlay
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chess Assistant Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows chess move suggestions overlay"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chess Assistant")
            .setContentText("Overlay is active - analyzing board")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
