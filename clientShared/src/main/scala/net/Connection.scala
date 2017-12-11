package se.nullable.kth.id1212.hangman.client.net

import java.net.InetSocketAddress
import java.nio.channels.{ ClosedChannelException, SelectionKey, Selector, SocketChannel }

import scala.collection.JavaConverters._
import se.nullable.kth.id1212.hangman.proto.{AsyncPacketReader, AsyncPacketWriter, Packet}

class Connection(packetListener: Packet => Unit) {
  private var thread: Option[ConnectionThread] = None

  def start(host: String, port: String): Unit = {
    stop()
    val socket = SocketChannel.open()
    socket.connect(new InetSocketAddress(host, port.toInt))
    socket.configureBlocking(false)
    val t = new ConnectionThread(socket, packetListener)
    t.start()
    thread = Some(t)
  }

  def stop(): Unit = {
    thread.foreach { t =>
      t.close()
      t.join()
    }
  }

  def sendPacket(packet: Packet): Unit = {
    thread.get.write(packet)
  }
}

class ConnectionThread(socket: SocketChannel, listener: Packet => Unit) extends Thread {
  setDaemon(true)

  private val selector = Selector.open()
  private val socketKey = socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)

  private val reader = new AsyncPacketReader(socket)
  private val writer = new AsyncPacketWriter(socket)

  override def run(): Unit = {
    while (selector.isOpen()) {
      selector.select()
      for (key <- selector.selectedKeys().asScala) {
        selector.selectedKeys().remove(key)
        if (key.isReadable()) {
          read()
        }
        if (key.isWritable()) {
          flush()
        }
      }
    }
  }

  def read(): Unit = {
    try {
      var hasMore = true
      while(hasMore) {
        reader.readNext() match {
          case Some(packet) =>
            listener(packet)
          case None =>
            hasMore = false
        }
      }
    } catch {
      case _: ClosedChannelException =>
        // Game over, terminate loop
        close()
    }
  }

  def write(packet: Packet): Unit = {
    writer.synchronized {
      writer.write(packet)
      socketKey.interestOps(socketKey.interestOps() | SelectionKey.OP_WRITE)
    }
  }

  def flush(): Unit = {
    writer.synchronized {
      if (!writer.flush()) {
        socketKey.interestOps(socketKey.interestOps() & ~SelectionKey.OP_WRITE)
      }
    }
  }

  def close(): Unit = {
    for (key <- selector.keys().asScala) {
      key.channel().close()
    }
    selector.close()
  }
}
