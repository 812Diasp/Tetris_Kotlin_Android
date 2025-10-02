package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TetrisApp()
        }
    }
}

// Модели данных
data class Cell(val x: Int, val y: Int, val color: Color)

enum class TetrominoType { I, O, T, S, Z, J, L }

data class Tetromino(
    val type: TetrominoType,
    val cells: List<Cell>,
    val color: Color
) {
    fun rotate(): Tetromino = when (type) {
        TetrominoType.O -> this
        else -> {
            val pivot = cells[1]
            copy(cells = cells.map { cell ->
                val relX = cell.x - pivot.x
                val relY = cell.y - pivot.y
                Cell(pivot.x - relY, pivot.y + relX, cell.color)
            })
        }
    }

    fun move(dx: Int, dy: Int) = copy(cells = cells.map { it.copy(x = it.x + dx, y = it.y + dy) })
}

class TetrisGame {
    companion object {
        const val WIDTH = 10
        const val HEIGHT = 20
    }

    private val grid = Array(WIDTH) { Array<Color?>(HEIGHT) { null } }
    private var currentPiece: Tetromino? = null
    private var _score = 0
    private var _level = 1
    private var _linesCleared = 0
    private var _isGameOver = false
    private var lastDropTime = System.currentTimeMillis()

    // Observable state для Compose
    var score: Int
        get() = _score
        private set(value) { _score = value }

    var level: Int
        get() = _level
        private set(value) { _level = value }

    var linesCleared: Int
        get() = _linesCleared
        private set(value) { _linesCleared = value }

    var isGameOver: Boolean
        get() = _isGameOver
        private set(value) { _isGameOver = value }

    init {
        spawnNewPiece()
    }

    private fun createTetromino(type: TetrominoType): Tetromino {
        val color = when (type) {
            TetrominoType.I -> Color.Cyan
            TetrominoType.O -> Color.Yellow
            TetrominoType.T -> Color.Magenta
            TetrominoType.S -> Color.Green
            TetrominoType.Z -> Color.Red
            TetrominoType.J -> Color.Blue
            TetrominoType.L -> Color(0xFFFFA500) // Orange
        }

        val cells = when (type) {
            TetrominoType.I -> listOf(Cell(4,0,color), Cell(5,0,color), Cell(6,0,color), Cell(7,0,color))
            TetrominoType.O -> listOf(Cell(4,0,color), Cell(5,0,color), Cell(4,1,color), Cell(5,1,color))
            TetrominoType.T -> listOf(Cell(4,0,color), Cell(3,1,color), Cell(4,1,color), Cell(5,1,color))
            TetrominoType.S -> listOf(Cell(4,0,color), Cell(5,0,color), Cell(3,1,color), Cell(4,1,color))
            TetrominoType.Z -> listOf(Cell(3,0,color), Cell(4,0,color), Cell(4,1,color), Cell(5,1,color))
            TetrominoType.J -> listOf(Cell(3,0,color), Cell(3,1,color), Cell(4,1,color), Cell(5,1,color))
            TetrominoType.L -> listOf(Cell(5,0,color), Cell(3,1,color), Cell(4,1,color), Cell(5,1,color))
        }

        return Tetromino(type, cells, color)
    }

    fun spawnNewPiece() {
        currentPiece = createTetromino(enumValues<TetrominoType>().random())
        if (currentPiece!!.cells.any { it.x !in 0 until WIDTH || it.y !in 0 until HEIGHT || grid[it.x][it.y] != null }) {
            isGameOver = true
        }
    }

    fun movePiece(dx: Int, dy: Int): Boolean {
        if (isGameOver || currentPiece == null) return false
        val moved = currentPiece!!.move(dx, dy)
        return if (isValidPosition(moved)) {
            currentPiece = moved
            true
        } else false
    }

    fun rotatePiece(): Boolean {
        if (isGameOver || currentPiece == null) return false
        val rotated = currentPiece!!.rotate()
        return if (isValidPosition(rotated)) {
            currentPiece = rotated
            true
        } else {
            // Попробуем сдвинуть фигуру для вращения (wall kick)
            val kicks = listOf(0, 1, -1, 2, -2)
            for (dx in kicks) {
                val kicked = rotated.move(dx, 0)
                if (isValidPosition(kicked)) {
                    currentPiece = kicked
                    return true
                }
            }
            false
        }
    }

    private fun isValidPosition(piece: Tetromino): Boolean {
        return piece.cells.all { cell ->
            cell.x in 0 until WIDTH &&
                    cell.y in 0 until HEIGHT &&
                    grid[cell.x][cell.y] == null
        }
    }

    fun dropPiece(): Boolean {
        if (isGameOver || currentPiece == null) return false
        return if (!movePiece(0, 1)) {
            lockPiece()
            clearLines()
            spawnNewPiece()
            false
        } else {
            true
        }
    }

    private fun lockPiece() {
        currentPiece!!.cells.forEach { cell ->
            if (cell.y >= 0) {
                grid[cell.x][cell.y] = cell.color
            }
        }
    }

    private fun clearLines() {
        var linesClearedCount = 0
        var y = HEIGHT - 1
        while (y >= 0) {
            if ((0 until WIDTH).all { x -> grid[x][y] != null }) {
                // Удаляем линию
                for (x in 0 until WIDTH) {
                    grid[x][y] = null
                }
                // Сдвигаем все линии выше вниз
                for (yy in y downTo 1) {
                    for (x in 0 until WIDTH) {
                        grid[x][yy] = grid[x][yy - 1]
                    }
                }
                // Очищаем самую верхнюю линию
                for (x in 0 until WIDTH) {
                    grid[x][0] = null
                }
                linesClearedCount++
                // Не уменьшаем y, так как нужно проверить ту же позицию снова после сдвига
            } else {
                y--
            }
        }

        if (linesClearedCount > 0) {
            linesCleared += linesClearedCount
            score += when (linesClearedCount) {
                1 -> 100 * level
                2 -> 300 * level
                3 -> 500 * level
                4 -> 800 * level
                else -> 0
            }
            level = linesCleared / 10 + 1
        }
    }

    fun hardDrop() {
        if (isGameOver || currentPiece == null) return
        while (movePiece(0, 1)) { /* continue */ }
        lockPiece()
        clearLines()
        spawnNewPiece()
    }

    fun shouldAutoDrop(): Boolean {
        val currentTime = System.currentTimeMillis()
        val dropInterval = maxOf(1000L - (level - 1) * 100, 100L)
        return if (currentTime - lastDropTime > dropInterval) {
            lastDropTime = currentTime
            true
        } else false
    }

    fun reset() {
        for (x in 0 until WIDTH) {
            for (y in 0 until HEIGHT) {
                grid[x][y] = null
            }
        }
        currentPiece = null
        score = 0
        level = 1
        linesCleared = 0
        isGameOver = false
        lastDropTime = System.currentTimeMillis()
        spawnNewPiece()
    }

    // Методы для получения состояния для отображения
    fun getGridState(): List<List<Color?>> {
        val displayGrid = List(WIDTH) { x -> List(HEIGHT) { y -> grid[x][y] } }

        // Добавляем текущую фигуру
        currentPiece?.cells?.forEach { cell ->
            if (cell.x in 0 until WIDTH && cell.y in 0 until HEIGHT) {
                (displayGrid[cell.x] as MutableList<Color?>)[cell.y] = cell.color
            }
        }

        return displayGrid
    }

    fun getCurrentPiece(): Tetromino? = currentPiece
}

@Composable
fun TetrisApp() {
    var currentScreen by remember { mutableStateOf("main_menu") }
    var highScore by remember { mutableStateOf(0) }

    when (currentScreen) {
        "main_menu" -> MainMenuScreen(
            onNewGame = { currentScreen = "game" },
            highScore = highScore,
            onResetScore = { highScore = 0 }
        )
        "game" -> GameScreen(
            onBackToMenu = {
                currentScreen = "main_menu"
            },
            onUpdateHighScore = { score ->
                if (score > highScore) highScore = score
            }
        )
    }
}

@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    highScore: Int,
    onResetScore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TETRIS",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "HIGH SCORE: $highScore",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onNewGame,
            modifier = Modifier.width(200.dp)
        ) {
            Text("NEW GAME")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onResetScore,
            modifier = Modifier.width(200.dp)
        ) {
            Text("RESET SCORE")
        }
    }
}

@Composable
fun GameScreen(
    onBackToMenu: () -> Unit,
    onUpdateHighScore: (Int) -> Unit
) {
    val game = remember { TetrisGame() }
    var isPaused by remember { mutableStateOf(false) }

    // Состояние для принудительного обновления UI
    var refreshCounter by remember { mutableStateOf(0) }

    // Функция для обновления UI
    fun refreshUI() {
        refreshCounter++
    }

    // Игровой цикл
    LaunchedEffect(key1 = isPaused) {
        while (true) {
            if (!game.isGameOver && !isPaused) {
                if (game.shouldAutoDrop()) {
                    game.dropPiece()
                    onUpdateHighScore(game.score)
                    refreshUI()
                }
            }
            delay(16) // ~60 FPS
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "Tetris by Tkachenko Denis",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // Левая панель информации
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Кнопка назад в главное меню
                Button(
                    onClick = onBackToMenu,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("← BACK TO MENU")
                }

                InfoCard("SCORE", game.score.toString(), Color.Yellow)
                InfoCard("LEVEL", game.level.toString(), Color.Cyan)
                InfoCard("LINES", game.linesCleared.toString(), Color.Cyan)

                Button(
                    onClick = {
                        isPaused = !isPaused
                        refreshUI()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isPaused) "RESUME" else "PAUSE")
                }
                Button(
                    onClick = {
                        game.reset()
                        isPaused = false
                        refreshUI()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESTART")
                }

                // Кнопки управления
                if (!isPaused && !game.isGameOver) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                game.movePiece(-1, 0)
                                refreshUI()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("←")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    game.rotatePiece()
                                    refreshUI()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("↻")
                            }
                            Button(
                                onClick = {
                                    game.movePiece(1, 0)
                                    refreshUI()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("→")
                            }
                        }
                        Button(
                            onClick = {
                                game.dropPiece()
                                refreshUI()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("↓")
                        }
                        Button(
                            onClick = {
                                game.hardDrop()
                                refreshUI()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("HARD DROP")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Игровое поле
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .background(Color.DarkGray)
                    .border(2.dp, Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Тап для поворота
                                if (!game.isGameOver && !isPaused) {
                                    game.rotatePiece()
                                    refreshUI()
                                }
                            }
                        )
                    }
            ) {
                // Используем ключ refreshCounter для принудительного обновления
                GameGrid(game = game, refreshCounter = refreshCounter)

                if (game.isGameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "GAME OVER",
                                color = Color.Red,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Score: ${game.score}",
                                color = Color.White,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            Button(
                                onClick = {
                                    game.reset()
                                    isPaused = false
                                    refreshUI()
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("PLAY AGAIN")
                            }
                        }
                    }
                }

                if (isPaused && !game.isGameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "PAUSED",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackToMenu,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("BACK TO MENU")
        }
    }
}

@Composable
fun GameGrid(game: TetrisGame, refreshCounter: Int) {
    val gridState = remember(refreshCounter) { game.getGridState() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(TetrisGame.WIDTH),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(TetrisGame.WIDTH * TetrisGame.HEIGHT) { index ->
            val x = index % TetrisGame.WIDTH
            val y = index / TetrisGame.WIDTH

            val cellColor = if (x in gridState.indices && y in gridState[x].indices) {
                gridState[x][y] ?: Color(0xFF222222)
            } else {
                Color(0xFF222222)
            }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(cellColor)
                    .border(1.dp, Color(0xFF444444))
            )
        }
    }
}

@Composable
fun InfoCard(title: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF333333))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}