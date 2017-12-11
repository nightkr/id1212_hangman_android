package se.nullable.kth.id1212.hangman.proto

import java.io.{ ByteArrayOutputStream, OutputStream }


class PacketWriter(stream: OutputStream) {
  private def writeInt(os: OutputStream, value: Int): Unit = {
    val buf = new Array[Byte](4)
    for (i <- 0 until buf.length) {
      val offset = (buf.length - 1 - i) * 8
      buf(i) = (value >> offset).toByte
    }
    os.write(buf)
  }

  private def writeBoolean(os: OutputStream, value: Boolean): Unit = {
    val buf = Array[Byte](if (value) 1 else 0)
    os.write(buf)
  }

  private def writeChar(os: OutputStream, value: Char): Unit = {
    val buf = Array(value.toByte)
    os.write(buf)
  }

  private def writeString(os: OutputStream, value: Seq[Char]): Unit = {
    writeInt(os, value.length)
    value.foreach(writeChar(os, _))
  }

  def write(packet: Packet): Unit = {
    val frame = new ByteArrayOutputStream()
    packet match {
      case pkt: Packet.TryLetter =>
        writeInt(frame, Packet.Types.TRY_LETTER)
        writeChar(frame, pkt.letter)
      case pkt: Packet.GameState =>
        writeInt(frame, Packet.Types.GAME_STATE)
        writeInt(frame, pkt.triesRemaining)
        writeString(frame, pkt.triedLetters.mkString)
        writeString(frame, pkt.clue.map(_.getOrElse('\u0000')))
      case pkt: Packet.GameOver =>
        writeInt(frame, Packet.Types.GAME_OVER)
        writeBoolean(frame, pkt.win)
      case pkt: Packet.TryWord =>
        writeInt(frame, Packet.Types.TRY_WORD)
        writeString(frame, pkt.word)
      case Packet.Restart =>
        writeInt(frame, Packet.Types.RESTART)
    }
    writeInt(stream, frame.size())
    frame.writeTo(stream)
    stream.flush()
  }
}
