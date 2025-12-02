package com.example.chessassistant

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log

/**
 * Result of board analysis containing FEN and metadata.
 */
data class BoardAnalysisResult(
    val fen: String,
    val confidence: Float,           // Average confidence of all detections
    val detectedPieces: Int,         // Number of pieces found
    val expectedPieces: Int = 32,    // Expected count for starting position
    val boardFound: Boolean,
    val processingTimeMs: Long
)

/**
 * Analyzes chess board images to detect pieces and generate FEN notation.
 * Uses YOLO detector for piece recognition and OpenCV for board localization.
 */
class BoardAnalyzer(private val yoloDetector: YoloDetector) {
    
    companion object {
        private const val TAG = "BoardAnalyzer"
        private const val BOARD_SIZE = 8
    }
    
    /**
     * Analyze a bitmap image to detect chess pieces and generate FEN.
     * @param bitmap Screenshot containing the chess board
     * @return BoardAnalysisResult with FEN and metadata
     */
    fun analyzeBitmap(bitmap: Bitmap): BoardAnalysisResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Step 1: Detect pieces using YOLO
            val detections = yoloDetector.detect(bitmap)
            
            if (detections.isEmpty()) {
                Log.w(TAG, "No pieces detected")
                return BoardAnalysisResult(
                    fen = "8/8/8/8/8/8/8/8 w - - 0 1",  // Empty board
                    confidence = 0f,
                    detectedPieces = 0,
                    boardFound = false,
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }
            
            // Step 2: Map detections to board squares
            val board = mapDetectionsToBoard(detections, bitmap.width, bitmap.height)
            
            // Step 3: Generate FEN from board
            val fen = generateFEN(board)
            
            // Calculate average confidence
            val avgConfidence = detections.map { it.confidence }.average().toFloat()
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Analysis complete: ${detections.size} pieces, FEN: $fen, time: ${processingTime}ms")
            
            return BoardAnalysisResult(
                fen = fen,
                confidence = avgConfidence,
                detectedPieces = detections.size,
                boardFound = true,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing bitmap", e)
            return BoardAnalysisResult(
                fen = "8/8/8/8/8/8/8/8 w - - 0 1",
                confidence = 0f,
                detectedPieces = 0,
                boardFound = false,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Map YOLO detections to an 8x8 chess board grid.
     * Assumes board occupies most of the image.
     */
    private fun mapDetectionsToBoard(detections: List<Detection>, imageWidth: Int, imageHeight: Int): Array<Array<String?>> {
        // Create empty 8x8 board (rank 8 to rank 1)
        val board = Array(BOARD_SIZE) { Array<String?>(BOARD_SIZE) { null } }
        
        // Find board bounds from detections
        val bounds = findBoardBounds(detections)
        
        // Map each detection to a square
        for (detection in detections) {
            val square = getSquareFromBoundingBox(detection.boundingBox, bounds)
            if (square != null) {
                val (rank, file) = square
                if (rank in 0 until BOARD_SIZE && file in 0 until BOARD_SIZE) {
                    // Convert piece name to FEN notation
                    val fenPiece = pieceNameToFEN(detection.className)
                    board[rank][file] = fenPiece
                    
                    // Store board square in detection for debugging
                    detection.boardSquare = "${('a' + file)}${8 - rank}"
                }
            }
        }
        
        return board
    }
    
    /**
     * Find the bounding box that contains all detected pieces.
     */
    private fun findBoardBounds(detections: List<Detection>): RectF {
        if (detections.isEmpty()) {
            return RectF(0f, 0f, 1f, 1f)
        }
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        for (detection in detections) {
            val box = detection.boundingBox
            minX = minOf(minX, box.left)
            minY = minOf(minY, box.top)
            maxX = maxOf(maxX, box.right)
            maxY = maxOf(maxY, box.bottom)
        }
        
        // Add some padding (5%)
        val padding = 0.05f
        val width = maxX - minX
        val height = maxY - minY
        
        return RectF(
            maxOf(0f, minX - width * padding),
            maxOf(0f, minY - height * padding),
            minOf(1f, maxX + width * padding),
            minOf(1f, maxY + height * padding)
        )
    }
    
    /**
     * Determine which square a bounding box belongs to.
     * @return Pair of (rank, file) where rank 0 = rank 8, file 0 = file a
     */
    private fun getSquareFromBoundingBox(box: RectF, boardBounds: RectF): Pair<Int, Int>? {
        // Get center of bounding box
        val centerX = (box.left + box.right) / 2
        val centerY = (box.top + box.bottom) / 2
        
        // Normalize to board coordinates
        val relativeX = (centerX - boardBounds.left) / (boardBounds.right - boardBounds.left)
        val relativeY = (centerY - boardBounds.top) / (boardBounds.bottom - boardBounds.top)
        
        if (relativeX < 0 || relativeX > 1 || relativeY < 0 || relativeY > 1) {
            return null
        }
        
        // Convert to square indices
        val file = (relativeX * BOARD_SIZE).toInt().coerceIn(0, BOARD_SIZE - 1)
        val rank = (relativeY * BOARD_SIZE).toInt().coerceIn(0, BOARD_SIZE - 1)
        
        return Pair(rank, file)
    }
    
    /**
     * Convert piece class name to FEN notation.
     */
    private fun pieceNameToFEN(className: String): String {
        return when (className.lowercase()) {
            "white_pawn" -> "P"
            "white_knight" -> "N"
            "white_bishop" -> "B"
            "white_rook" -> "R"
            "white_queen" -> "Q"
            "white_king" -> "K"
            "black_pawn" -> "p"
            "black_knight" -> "n"
            "black_bishop" -> "b"
            "black_rook" -> "r"
            "black_queen" -> "q"
            "black_king" -> "k"
            else -> "?"
        }
    }
    
    /**
     * Generate FEN notation from 8x8 board array.
     */
    private fun generateFEN(board: Array<Array<String?>>): String {
        val fenParts = mutableListOf<String>()
        
        // Generate board position (rank by rank, from rank 8 to rank 1)
        for (rank in 0 until BOARD_SIZE) {
            var emptyCount = 0
            val rankString = StringBuilder()
            
            for (file in 0 until BOARD_SIZE) {
                val piece = board[rank][file]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        rankString.append(emptyCount)
                        emptyCount = 0
                    }
                    rankString.append(piece)
                }
            }
            
            if (emptyCount > 0) {
                rankString.append(emptyCount)
            }
            
            fenParts.add(rankString.toString())
        }
        
        val boardFEN = fenParts.joinToString("/")
        
        // Determine whose turn it is (heuristic: check if white has moved)
        val turn = determineTurn(board)
        
        // Castling rights (assume all available for now)
        val castling = determineCastlingRights(board)
        
        // En passant (not implemented, always -)
        val enPassant = "-"
        
        // Halfmove clock (not tracked, default to 0)
        val halfmove = "0"
        
        // Fullmove number (default to 1)
        val fullmove = "1"
        
        return "$boardFEN $turn $castling $enPassant $halfmove $fullmove"
    }
    
    /**
     * Determine whose turn it is based on piece positions.
     * Heuristic: if pieces are in starting position, it's white's turn.
     * Otherwise, count moves and alternate.
     */
    private fun determineTurn(board: Array<Array<String?>>): String {
        // Simple heuristic: check if any white pawn has moved
        val whitePawnsOnRank2 = board[6].count { it == "P" }
        val blackPawnsOnRank7 = board[1].count { it == "p" }
        
        // If all white pawns are on rank 2 and all black pawns on rank 7, white to move
        if (whitePawnsOnRank2 == 8 && blackPawnsOnRank7 == 8) {
            return "w"
        }
        
        // Otherwise, alternate based on total pieces moved (very rough heuristic)
        return "w"  // Default to white
    }
    
    /**
     * Determine castling rights based on king and rook positions.
     */
    private fun determineCastlingRights(board: Array<Array<String?>>): String {
        val rights = StringBuilder()
        
        // White castling
        val whiteKingOnE1 = board[7][4] == "K"
        val whiteRookOnH1 = board[7][7] == "R"
        val whiteRookOnA1 = board[7][0] == "R"
        
        if (whiteKingOnE1 && whiteRookOnH1) rights.append("K")
        if (whiteKingOnE1 && whiteRookOnA1) rights.append("Q")
        
        // Black castling
        val blackKingOnE8 = board[0][4] == "k"
        val blackRookOnH8 = board[0][7] == "r"
        val blackRookOnA8 = board[0][0] == "r"
        
        if (blackKingOnE8 && blackRookOnH8) rights.append("k")
        if (blackKingOnE8 && blackRookOnA8) rights.append("q")
        
        return if (rights.isEmpty()) "-" else rights.toString()
    }
    
    /**
     * Get best move coordinates for drawing on screen (legacy method).
     */
    fun getBestMoveCoordinates(bestMove: String): FloatArray {
        // This would require knowing the actual board position on screen
        // For now, return dummy coordinates
        return floatArrayOf(500f, 1000f, 600f, 800f)
    }
}
