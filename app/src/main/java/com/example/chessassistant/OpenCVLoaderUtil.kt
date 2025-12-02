package com.example.chessassistant

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Utility class to handle OpenCV initialization for Android.
 * Ensures OpenCV is loaded before any OpenCV operations are performed.
 */
object OpenCVLoaderUtil {
    
    private const val TAG = "OpenCVLoader"
    private var isInitialized = false
    
    /**
     * Initialize OpenCV library.
     * @return true if OpenCV is successfully loaded, false otherwise
     */
    fun initOpenCV(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "OpenCV already initialized")
            return true
        }
        
        return try {
            val success = OpenCVLoader.initLocal()
            if (success) {
                isInitialized = true
                val version = org.opencv.core.Core.VERSION
                Log.i(TAG, "✓ OpenCV loaded successfully! Version: $version")
            } else {
                Log.e(TAG, "✗ OpenCV initialization failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error initializing OpenCV", e)
            false
        }
    }
    
    /**
     * Check if OpenCV is initialized.
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Get OpenCV version string.
     */
    fun getVersion(): String {
        return if (isInitialized) {
            org.opencv.core.Core.VERSION
        } else {
            "Not initialized"
        }
    }
}
