package se.nullable.kth.id1212.hangman.proto

import java.io.IOException

sealed trait Packet
object Packet {
  object Types {
    final val TRY_LETTER: Int = 1
    final val GAME_STATE: Int = 2
    final val GAME_OVER: Int = 3
    final val TRY_WORD: Int = 4
    final val RESTART: Int = 5
  }

  case class TryLetter(letter: Char) extends Packet
  case class GameState(triesRemaining: Int, triedLetters: Set[Char], clue: Seq[Option[Char]]) extends Packet
  case class GameOver(win: Boolean) extends Packet
  case class TryWord(word: String) extends Packet
  case object Restart extends Packet
}

class InvalidPacketException(msg: String, cause: Throwable = null) extends IOException(msg, cause)
