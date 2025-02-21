import java.io.*;
import java.util.*;

public class ChessGame {
    private static Board board;
    private static Scanner scanner;
    private static boolean gameOver;

    public static void main(String[] args) {
        scanner = new Scanner(System.in);
        board = new Board();
        gameOver = false;

        while (!gameOver) {
            board.printBoard();
            System.out.println("Aktualny gracz: " + (board.isWhiteTurn() ? "Białe" : "Czarne"));
            System.out.print("Podaj ruch (np. e2 e3) lub wpisz 'stalemate' dla pozycji patowej, 'exit' aby zakończyć: ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("exit")) {
                saveGameToFile();
                gameOver = true;
                continue;
            }
            if (input.equalsIgnoreCase("stalemate")) {
                board.setupStalemate();
                continue;
            }

            String[] moves = input.split(" ");
            if (moves.length != 2) {
                System.out.println("Nieprawidłowy format ruchu!");
                continue;
            }

            int[] from = parsePosition(moves[0]);
            int[] to = parsePosition(moves[1]);
            
            if (from == null || to == null) {
                System.out.println("Nieprawidłowa pozycja!");
                continue;
            }

            if (board.movePiece(from[0], from[1], to[0], to[1])) {
                if (board.isCheckmate()) {
                    board.printBoard();
                    System.out.println("SZACH-MAT!");
                    gameOver = true;
                } else if (board.isStalemate()) {
                    board.printBoard();
                    System.out.println("PAT!");
                    gameOver = true;
                } else if (board.isCheck()) {
                    System.out.println("SZACH!");
                }
            } else {
                System.out.println("Nieprawidłowy ruch!");
            }
        }
        saveGameToFile();
    }

    private static int[] parsePosition(String pos) {
        if (pos.length() != 2) return null;
        int col = pos.charAt(0) - 'a';
        int row = 8 - Character.getNumericValue(pos.charAt(1));
        if (col < 0 || col > 7 || row < 0 || row > 7) return null;
        return new int[]{row, col};
    }

    private static void saveGameToFile() {
        try (PrintWriter writer = new PrintWriter(new File("szachy.txt"))) {
            for (String move : board.getMoveHistory()) {
                writer.println(move);
            }
            System.out.println("Gra została zapisana do pliku szachy.txt");
        } catch (FileNotFoundException e) {
            System.out.println("Błąd zapisu pliku: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class Board {
        private Piece[][] grid;
        private boolean whiteTurn;
        private List<String> moveHistory;
        private String enPassantTarget;
        private Map<String, Boolean> castlingRights;

        public Board() {
            whiteTurn = true;
            moveHistory = new ArrayList<>();
            enPassantTarget = null;
            castlingRights = new HashMap<>();
            castlingRights.put("whiteKing", true);
            castlingRights.put("whiteQueen", true);
            castlingRights.put("whiteKingSide", true);
            castlingRights.put("whiteQueenSide", true);
            castlingRights.put("blackKing", true);
            castlingRights.put("blackQueen", true);
            castlingRights.put("blackKingSide", true);
            castlingRights.put("blackQueenSide", true);
            
            initializeBoard();
        }

        private void initializeBoard() {
            grid = new Piece[8][8];
            for (int i = 0; i < 8; i++) {
                grid[1][i] = new Pawn(false);
                grid[6][i] = new Pawn(true);
            }

            String[] pieces = {Rook.class.getSimpleName(), Knight.class.getSimpleName(), Bishop.class.getSimpleName(), 
                              Queen.class.getSimpleName(), King.class.getSimpleName(), Bishop.class.getSimpleName(), 
                              Knight.class.getSimpleName(), Rook.class.getSimpleName()};
            
            for (int i = 0; i < 8; i++) {
                try {
                    grid[0][i] = (Piece) Class.forName("ChessGame$" + pieces[i])
                        .getConstructor(boolean.class).newInstance(false);
                    grid[7][i] = (Piece) Class.forName("ChessGame$" + pieces[i])
                        .getConstructor(boolean.class).newInstance(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Ustawia planszę w pozycji patowej (teraz ustawia poprostu remis, jeżeli obydwie strony chcą remis)
        public void setupStalemate() {
            grid = new Piece[8][8];
            grid[0][7] = new King(false);       // Czarny król na h8
            grid[1][5] = new King(true);          // Biały król na f7
            grid[2][6] = new Queen(true);         // Biały hetman na g6
            whiteTurn = false;
            moveHistory.add("Pozycja patowa ustawiona przez komendę 'stalemate'");
        }

        public String getEnPassantTarget() {
            return enPassantTarget;
        }

        public boolean movePiece(int fromRow, int fromCol, int toRow, int toCol) {
            Piece piece = grid[fromRow][fromCol];
            if (piece == null || piece.isWhite() != whiteTurn) return false;
            
            if (!(piece instanceof King && Math.abs(fromCol - toCol) == 2)) {
                if (grid[toRow][toCol] != null && grid[toRow][toCol].isWhite() == piece.isWhite())
                    return false;
            }

            if (piece.isValidMove(fromRow, fromCol, toRow, toCol, grid)) {
                Piece captured = grid[toRow][toCol];
                grid[toRow][toCol] = piece;
                grid[fromRow][fromCol] = null;
                boolean wasInCheck = isCheck();
                grid[fromRow][fromCol] = piece;
                grid[toRow][toCol] = captured;

                if (wasInCheck) {
                    return false;
                }

                handleSpecialMoves(piece, fromRow, fromCol, toRow, toCol);

                // Obsługa en passant
                if (piece instanceof Pawn && Math.abs(fromCol - toCol) == 1 && grid[toRow][toCol] == null) {
                    String targetSquare = "" + (char)('a' + toCol) + (8 - toRow);
                    if (targetSquare.equals(enPassantTarget)) {
                        int capturedRow = toRow + (piece.isWhite() ? 1 : -1);
                        grid[capturedRow][toCol] = null;
                    }
                }
                
                grid[toRow][toCol] = piece;
                grid[fromRow][fromCol] = null;
                whiteTurn = !whiteTurn;
                recordMove(piece, fromRow, fromCol, toRow, toCol);
                return true;
            }
            return false;
        }

        private void handleSpecialMoves(Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
            if (piece instanceof Pawn && Math.abs(fromRow - toRow) == 2) {
                enPassantTarget = "" + (char)('a' + fromCol) + (8 - ((fromRow + toRow) / 2));
            } else {
                enPassantTarget = null;
            }

            if (piece instanceof King && Math.abs(fromCol - toCol) == 2) {
                int rookCol = toCol > fromCol ? 7 : 0;
                int newRookCol = toCol > fromCol ? 5 : 3;
                grid[fromRow][newRookCol] = grid[fromRow][rookCol];
                grid[fromRow][rookCol] = null;
            }
        }

        private void recordMove(Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
            String move = piece.getAlgebraicNotation(fromRow, fromCol, toRow, toCol, grid);
            moveHistory.add(move);
        }
        
        private String posToString(int row, int col) {
            return "" + (char)('a' + col) + (8 - row);
        }

        public boolean isCheck() {
            boolean currentPlayerIsWhite = whiteTurn;
            int kingRow = -1, kingCol = -1;

            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    Piece piece = grid[i][j];
                    if (piece instanceof King && piece.isWhite() == currentPlayerIsWhite) {
                        kingRow = i;
                        kingCol = j;
                        break;
                    }
                }
                if (kingRow != -1) break;
            }

            if (kingRow == -1) {
                return false;
            }

            boolean opponentIsWhite = !currentPlayerIsWhite;
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    Piece piece = grid[i][j];
                    if (piece != null && piece.isWhite() == opponentIsWhite) {
                        if (piece.isValidMove(i, j, kingRow, kingCol, grid))
                            return true;
                    }
                }
            }
            return false;
        }

        public boolean isCheckmate() {
            return isCheck() && !hasLegalMoves();
        }

        public boolean isStalemate() {
            return !isCheck() && !hasLegalMoves();
        }

        private boolean hasLegalMoves() {
            boolean currentPlayerIsWhite = whiteTurn;
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    Piece piece = grid[i][j];
                    if (piece != null && piece.isWhite() == currentPlayerIsWhite) {
                        for (int x = 0; x < 8; x++) {
                            for (int y = 0; y < 8; y++) {
                                if (piece.isValidMove(i, j, x, y, grid)) {
                                    Piece temp = grid[x][y];
                                    grid[x][y] = piece;
                                    grid[i][j] = null;
                                    boolean isInCheck = isCheck();
                                    grid[i][j] = piece;
                                    grid[x][y] = temp;
                                    if (!isInCheck) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        public void printBoard() {
            System.out.println("  a b c d e f g h");
            for (int i = 0; i < 8; i++) {
                System.out.print(8 - i + " ");
                for (int j = 0; j < 8; j++) {
                    Piece piece = grid[i][j];
                    System.out.print(piece != null ? piece.getSymbol() : ".");
                    System.out.print(" ");
                }
                System.out.println(8 - i);
            }
            System.out.println("  a b c d e f g h");
        }

        public boolean isWhiteTurn() {
            return whiteTurn;
        }

        public List<String> getMoveHistory() {
            return moveHistory;
        }
    }

    abstract static class Piece {
        protected boolean white;
        protected char symbol;

        public Piece(boolean white) {
            this.white = white;
        }

        public boolean isWhite() {
            return white;
        }

        public char getSymbol() {
            return white ? Character.toUpperCase(symbol) : Character.toLowerCase(symbol);
        }

        public abstract boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board);
        public abstract String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board);
    }

    static class Pawn extends Piece {
        public Pawn(boolean white) {
            super(white);
            symbol = 'P';
        }

        @Override
        public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            int direction = white ? -1 : 1;
            int startRow = white ? 6 : 1;
            if (fromCol == toCol) {
                if (board[toRow][toCol] != null) return false;
                if (toRow == fromRow + direction) return true;
                if (fromRow == startRow && toRow == fromRow + 2 * direction && board[fromRow + direction][fromCol] == null)
                    return true;
            }
            if (Math.abs(toCol - fromCol) == 1 && toRow == fromRow + direction) {
                if (board[toRow][toCol] != null && board[toRow][toCol].isWhite() != white)
                    return true;
                String targetSquare = "" + (char)('a' + toCol) + (8 - toRow);
                if (targetSquare.equals(ChessGame.board.getEnPassantTarget()))
                    return true;
            }
            return false;
        }

        @Override
        public String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            return "" + (char)('a' + fromCol) + (8 - fromRow) + "-" + (char)('a' + toCol) + (8 - toRow);
        }
    }

    static class Rook extends Piece {
        public Rook(boolean white) {
            super(white);
            symbol = 'R';
        }

        @Override
        public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            if (board[toRow][toCol] != null && board[toRow][toCol].isWhite() == this.white)
                return false;
            if (fromRow != toRow && fromCol != toCol) return false;
            return isPathClear(fromRow, fromCol, toRow, toCol, board);
        }

        @Override
        public String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            return "R" + (char)('a' + fromCol) + (8 - fromRow) + "-" + (char)('a' + toCol) + (8 - toRow);
        }
    }

    static class Knight extends Piece {
        public Knight(boolean white) {
            super(white);
            symbol = 'N';
        }

        @Override
        public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            if (board[toRow][toCol] != null && board[toRow][toCol].isWhite() == this.white)
                return false;
            int dx = Math.abs(toCol - fromCol);
            int dy = Math.abs(toRow - fromRow);
            return (dx == 1 && dy == 2) || (dx == 2 && dy == 1);
        }

        @Override
        public String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            return "N" + (char)('a' + fromCol) + (8 - fromRow) + "-" + (char)('a' + toCol) + (8 - toRow);
        }
    }

    static class Bishop extends Piece {
        public Bishop(boolean white) {
            super(white);
            symbol = 'B';
        }

        @Override
        public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            if (board[toRow][toCol] != null && board[toRow][toCol].isWhite() == this.white)
                return false;
            if (Math.abs(fromRow - toRow) != Math.abs(fromCol - toCol)) return false;
            return isPathClear(fromRow, fromCol, toRow, toCol, board);
        }

        @Override
        public String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            return "B" + (char)('a' + fromCol) + (8 - fromRow) + "-" + (char)('a' + toCol) + (8 - toRow);
        }
    }

    static class Queen extends Piece {
        public Queen(boolean white) {
            super(white);
            symbol = 'Q';
        }

        @Override
        public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            if (board[toRow][toCol] != null && board[toRow][toCol].isWhite() == this.white)
                return false;
            boolean rookMove = (fromRow == toRow || fromCol == toCol);
            boolean bishopMove = (Math.abs(fromRow - toRow) == Math.abs(fromCol - toCol));
            return (rookMove || bishopMove) && isPathClear(fromRow, fromCol, toRow, toCol, board);
        }

        @Override
        public String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            return "Q" + (char)('a' + fromCol) + (8 - fromRow) + "-" + (char)('a' + toCol) + (8 - toRow);
        }
    }

    static class King extends Piece {
        public King(boolean white) {
            super(white);
            symbol = 'K';
        }

        @Override
        public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            int dx = Math.abs(toCol - fromCol);
            int dy = Math.abs(toRow - fromRow);
            if (dx <= 1 && dy <= 1) {
                if (board[toRow][toCol] != null && board[toRow][toCol].isWhite() == this.white)
                    return false;
                return true;
            }
            if (dx == 2 && dy == 0) {
                return canCastle(fromRow, fromCol, toCol, board);
            }
            return false;
        }

        private boolean canCastle(int row, int fromCol, int toCol, Piece[][] board) {
            int direction = (toCol > fromCol) ? 1 : -1;
            if (isSquareUnderAttack(row, fromCol, !white, board)) return false;
            if (isSquareUnderAttack(row, fromCol + direction, !white, board)) return false;
            if (isSquareUnderAttack(row, fromCol + 2 * direction, !white, board)) return false;

            int rookCol = (direction == 1) ? 7 : 0;
            Piece rook = board[row][rookCol];
            if (!(rook instanceof Rook) || rook.isWhite() != this.white) return false;

            for (int i = fromCol + direction; i != rookCol; i += direction) {
                if (board[row][i] != null) return false;
            }
            return true;
        }

        private boolean isSquareUnderAttack(int row, int col, boolean attackerIsWhite, Piece[][] board) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    Piece piece = board[i][j];
                    if (piece != null && piece.isWhite() == attackerIsWhite) {
                        if (piece.isValidMove(i, j, row, col, board)) return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String getAlgebraicNotation(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
            return "K" + (char)('a' + fromCol) + (8 - fromRow) + "-" + (char)('a' + toCol) + (8 - toRow);
        }
    }

    private static boolean isPathClear(int fromRow, int fromCol, int toRow, int toCol, Piece[][] board) {
        int rowStep = Integer.compare(toRow, fromRow);
        int colStep = Integer.compare(toCol, fromCol);
        int steps = Math.max(Math.abs(toRow - fromRow), Math.abs(toCol - fromCol));
        for (int i = 1; i < steps; i++) {
            int checkRow = fromRow + i * rowStep;
            int checkCol = fromCol + i * colStep;
            if (board[checkRow][checkCol] != null) return false;
        }
        return true;
    }
}