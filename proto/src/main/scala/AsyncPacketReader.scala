package se.nullable.kth.id1212.hangman.proto

import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, ReadableByteChannel}

import scala.util.Try

class AsyncPacketReader(channel: ReadableByteChannel) {
  private val buf = ByteBuffer.allocate(5000)

  private def readBoolean(): Boolean =
    buf.get() match {
      case 0 => false
      case 1 => true
      case v => throw new InvalidPacketException(s"$v is not a boolean value")
    }

  private def readChar(): Char = buf.get().toChar

  private def readString(): String = {
    val length = buf.getInt()
    val strBuf = new Array[Char](length)
    for (i <- 0 until strBuf.length) {
      strBuf(i) = readChar()
    }
    new String(strBuf)
  }

  private def hasReadFrame: Boolean = buf.position() >= 4 && buf.position() >= buf.getInt(0) + 4

  private def readFrame(): Option[Int] = {
    if (!hasReadFrame) {
      if (channel.read(buf) == -1) {
        throw new ClosedChannelException()
      }
    }
    if (buf.position() >= 4 && buf.getInt(0) >= buf.capacity()) {
      throw new InvalidPacketException(s"Frame is too long: ${buf.getInt(0)} > ${buf.capacity() - 4}")
    }
    if (hasReadFrame) {
      buf.flip()
      val length = buf.getInt()
      Some(length + 4)
    } else {
      None
    }
  }

  private def doneReadingFrame(length: Int): Unit = {
    val read = buf.position()
    buf.position(length) // Avoid corrupting future reads
    buf.compact()
    if (read != length) {
      throw new InvalidPacketException(s"Read $read bytes from frame of length $length")
    }
  }

  def readNext(): Option[Packet] = {
    readFrame().map { length =>
      val tryTypeCode = Try(buf.getInt())
      try {
        val typeCode = tryTypeCode.get
        typeCode match {
          case Packet.Types.TRY_LETTER =>
            val character = readChar()
            Packet.TryLetter(character)
          case Packet.Types.GAME_STATE =>
            val triesLeft = buf.getInt()
            val triedChars = readString()
            val clue = readString()
            Packet.GameState(triesLeft, triedChars.toSet, clue.map(Some(_).filter(_ != '\u0000')))
          case Packet.Types.GAME_OVER =>
            val win = readBoolean()
            Packet.GameOver(win)
          case Packet.Types.TRY_WORD =>
            val word = readString()
            Packet.TryWord(word)
          case Packet.Types.RESTART =>
            Packet.Restart
          case _ =>
            throw new InvalidPacketException("Unknown packet type")
        }
      } catch {
        case e: Exception =>
          throw new InvalidPacketException(s"Failed to parse packet type: $tryTypeCode")
      } finally {
        doneReadingFrame(length)
      }
    }
  }
}
