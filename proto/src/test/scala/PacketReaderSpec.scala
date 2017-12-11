package se.nullable.kth.id1212.hangman.proto

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, EOFException}
import java.nio.ByteBuffer
import java.nio.channels.{ ClosedChannelException, Pipe }

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.prop.PropertyChecks

class PacketReaderSpec extends WordSpec with Matchers with PropertyChecks {
  private def seqReader(bytes: Byte*): PacketReader = new PacketReader(new ByteArrayInputStream(bytes.toArray))

  "A PacketReader given an InputStream" when {
    "empty" should {
      "throw an EOFException" in {
        val reader = seqReader()
        assertThrows[EOFException](reader.readNext())
      }
    }

    "containing an invalid packet type" should {
      "throw an InvalidPacketException" in {
        val reader = seqReader(
          0, 0, 0, 4, // Packet length
          0, 0, 0, 0  // Type 0 (reserved invalid)
        )
        assertThrows[InvalidPacketException](reader.readNext())
      }
    }

    "reading a TryLetter packet" should {
      "decode the packet" in {
        val reader = seqReader(
          0, 0, 0, 5, // Packet length
          0, 0, 0, 1, // Type 1 (TryLetter)
          'c'.toByte
        )
        reader.readNext() shouldEqual Packet.TryLetter('c')
      }
    }

    "reading a GameState packet" should {
      "decode the packet" in {
        val reader = seqReader(
          0, 0, 0, 24, // Packet length
          0, 0, 0, 2,  // Type 2 (GameState)
          0, 0, 0, 5,  // Remaining tries
          0, 0, 0, 2,  // Tried character count
          'g'.toByte, 'h'.toByte,
          0, 0, 0, 6,  // Clue length
          'q'.toByte, 'w'.toByte, 'e'.toByte, 'r'.toByte, 't'.toByte, 'y'.toByte
        )
        reader.readNext() shouldEqual Packet.GameState(5, Set('g', 'h'), "qwerty".map(Some(_)))
      }
    }
  }

  val genPacketTryLetter: Gen[Packet.TryLetter] = for {
    letter <- Gen.alphaChar
  } yield Packet.TryLetter(letter)
  val genPacketGameState: Gen[Packet.GameState] = for {
    triesLeft <- Gen.choose(0, 500)
    triedChars <- Gen.listOf(Gen.alphaLowerChar)
    clue <- Gen.listOf(Gen.option(Gen.alphaNumChar))
  } yield Packet.GameState(triesLeft, triedChars.toSet, clue)
  val genPacketGameOver: Gen[Packet.GameOver] = for {
    win <- Gen.oneOf(false, true)
  } yield Packet.GameOver(win)
  val gamePacketTryWord: Gen[Packet.TryWord] = for {
    word <- Gen.alphaStr
  } yield Packet.TryWord(word)
  val gamePacketRestart: Gen[Packet.Restart.type] = Gen.const(Packet.Restart)

  implicit val genPacket: Arbitrary[Packet] = Arbitrary(Gen.oneOf(genPacketTryLetter, genPacketGameState, genPacketGameOver, gamePacketTryWord, gamePacketRestart))

  "A PacketReader" when {
    "given a value serialized by PacketWriter" should {
      "give back the same value" in {
        forAll { packet: Packet =>
          val bos = new ByteArrayOutputStream
          val writer = new PacketWriter(bos)
          writer.write(packet)
          val reader = new PacketReader(new ByteArrayInputStream(bos.toByteArray()))
          val readPkt = reader.readNext()
          readPkt shouldEqual packet
        }
      }
    }
  }

  "An AsyncPacketReader" when {
    "given a value serialized by PacketWriter" should {
      "give back the same value" in {
        forAll { packet: Packet =>
          val bos = new ByteArrayOutputStream()
          val writer = new PacketWriter(bos)
          writer.write(packet)
          val pipe = Pipe.open()
          pipe.source().configureBlocking(false)
          pipe.sink().write(ByteBuffer.wrap(bos.toByteArray()))
          val reader = new AsyncPacketReader(pipe.source())
          reader.readNext() shouldEqual Some(packet)
        }
      }
    }

    "given a partial packet" should {
      "give back None until the full value is available" in {
        val packet = Seq[Byte](
          0, 0, 0, 5, // Packet length
          0, 0, 0, 1, // Type 1 (TryLetter)
          'c'.toByte
        )
        val pipe = Pipe.open()
        pipe.source.configureBlocking(false)
        val reader = new AsyncPacketReader(pipe.source())
        for (byte <- packet) {
          reader.readNext() shouldEqual None
          pipe.sink.write(ByteBuffer.wrap(Array(byte)))
        }
        reader.readNext() shouldEqual Some(Packet.TryLetter('c'))
      }
    }

    "given multiple packets" should {
      "give back one at a time" in {
        val packets = 5
        val packet = ByteBuffer.wrap((0 to packets).flatMap(_ => Seq[Byte](
          0, 0, 0, 5, // Packet length
          0, 0, 0, 1, // Type 1 (TryLetter)
          'c'.toByte
        )).toArray)
        val pipe = Pipe.open()
        pipe.source.configureBlocking(false)
        pipe.sink().write(packet)
        val reader = new AsyncPacketReader(pipe.source())
        for (i <- 0 to packets) {
          reader.readNext() shouldEqual Some(Packet.TryLetter('c'))
        }
        reader.readNext() shouldEqual None
      }
    }

    "given an EOF" should {
      "throw ClosedChannelException" in {
        val pipe = Pipe.open()
        pipe.source().configureBlocking(false)
        val reader = new AsyncPacketReader(pipe.source())
        pipe.sink().close()
        assertThrows[ClosedChannelException](reader.readNext())
      }
    }
  }

  "An AsyncPacketWriter" should {
    "given a packet" should {
      "produce the same value as PacketWriter" in {
        forAll { packet: Packet =>
          val bos = new ByteArrayOutputStream()
          val writer = new PacketWriter(bos)
          writer.write(packet)
          val pipe = Pipe.open()
          pipe.source().configureBlocking(false)
          val asyncWriter = new AsyncPacketWriter(pipe.sink())
          asyncWriter.write(packet)
          asyncWriter.flush()
          val asyncBuf = ByteBuffer.allocate(bos.size() + 1)
          pipe.source().read(asyncBuf)
          asyncBuf.flip()
          val asyncBufArray = Array.ofDim[Byte](asyncBuf.limit())
          asyncBuf.get(asyncBufArray)
          asyncBufArray shouldEqual bos.toByteArray()
        }
      }
    }
  }
}
