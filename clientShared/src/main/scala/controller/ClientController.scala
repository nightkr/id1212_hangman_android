package se.nullable.kth.id1212.hangman.client.controller

import org.slf4j.LoggerFactory
import scala.collection.mutable.MutableList
import se.nullable.kth.id1212.hangman.proto.Packet
import se.nullable.kth.id1212.hangman.client.net.Connection

class ClientController {
  private val listeners = MutableList[UpdateListener]()
  private val connection = new Connection(receivePacket)

  private val log = LoggerFactory.getLogger(getClass)

  def addListener(listener: UpdateListener): Unit = {
    listeners += listener
  }

  def start(host: String, port: String): Unit = {
    connection.start(host, port)
  }

  def stop(): Unit = {
    connection.stop()
  }

  private def receivePacket(packet: Packet): Unit = packet match {
    case pkt: Packet.GameState =>
      listeners.foreach(_.gameStateUpdate(pkt))
    case pkt: Packet.GameOver =>
      listeners.foreach(_.gameOver(pkt.win))
    case pkt =>
      log.error(s"Invalid packet: $pkt")
  }

  def tryLetter(letter: Char): Unit = {
    connection.sendPacket(Packet.TryLetter(letter))
  }

  def tryWord(word: String): Unit = {
    connection.sendPacket(Packet.TryWord(word))
  }

  def restart(): Unit = {
    connection.sendPacket(Packet.Restart)
  }
}

trait UpdateListener {
  def gameStateUpdate(state: Packet.GameState): Unit
  def gameOver(win: Boolean): Unit
}
