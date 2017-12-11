package se.nullable.kth.id1212.hangman.proto

import java.io.{ ByteArrayOutputStream, OutputStream }
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel


class AsyncPacketWriter(channel: WritableByteChannel) {
  private val buf = ByteBuffer.allocate(5000)

  private def writeBoolean(value: Boolean): Unit = {
    buf.put((if (value) 1 else 0): Byte)
  }

  private def writeChar(value: Char): Unit = {
    buf.put(value.toByte)
  }

  private def writeString(value: Seq[Char]): Unit = {
    buf.putInt(value.length)
    value.foreach(writeChar)
  }

  def write(packet: Packet): Unit = {
    val pos = buf.position()
    try {
      buf.putInt(0) // Placeholder length
      packet match {
        case pkt: Packet.TryLetter =>
          buf.putInt(Packet.Types.TRY_LETTER)
          writeChar(pkt.letter)
        case pkt: Packet.GameState =>
          buf.putInt(Packet.Types.GAME_STATE)
          buf.putInt(pkt.triesRemaining)
          writeString(pkt.triedLetters.mkString)
          writeString(pkt.clue.map(_.getOrElse('\u0000')))
        case pkt: Packet.GameOver =>
          buf.putInt(Packet.Types.GAME_OVER)
          writeBoolean(pkt.win)
        case pkt: Packet.TryWord =>
          buf.putInt(Packet.Types.TRY_WORD)
          writeString(pkt.word)
        case Packet.Restart =>
          buf.putInt(Packet.Types.RESTART)
      }
      buf.putInt(pos, buf.position() - pos - 4) // Update length
    } catch {
      case ex: Exception =>
        buf.position(pos) // Discard half-written packet
        throw ex
    }
    flush()
  }

  def flush(): Boolean = {
    buf.flip()
    try {
      if (buf.hasRemaining()) {
        channel.write(buf)
      }
      buf.hasRemaining()
    } finally {
      buf.compact()
    }
  }
}
