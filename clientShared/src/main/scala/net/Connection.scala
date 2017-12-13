package se.nullable.kth.id1212.hangman.client.net

import java.net.InetSocketAddress
import java.nio.channels.{ ClosedChannelException, SelectionKey, Selector, SocketChannel }

import scala.collection.JavaConverters._
import se.nullable.kth.id1212.hangman.proto.{AsyncPacketReader, AsyncPacketWriter, Packet}

class Connection(packetListener: Packet => Unit) {
  private var thread: Option[ConnectionThread] = None

  def start(host: String, port: String): Unit = {
    stop()
    val t = new ConnectionThread(host, port.toInt, packetListener)
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

class ConnectionThread(host: String, port: Int, listener: Packet => Unit) extends Thread {
  setDaemon(true)

  private val selector = Selector.open()
  private var socketKey: Option[SelectionKey] = None // socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)

  private var writer: Option[AsyncPacketWriter] = None // new AsyncPacketWriter(socket)

  override def run(): Unit = {
    val socket = SocketChannel.open()
    socket.connect(new InetSocketAddress(host, port))
    socket.configureBlocking(false)

    socketKey = Some(socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE))
    val reader = new AsyncPacketReader(socket)
    writer = Some(new AsyncPacketWriter(socket))

    try {
      while (selector.isOpen()) {
        selector.select()
        if (selector.isOpen()) {
          for (key <- selector.selectedKeys().asScala) {
            if (key.isReadable()) {
              read(reader)
            }
            if (key.isWritable()) {
              flush()
            }
          }
          selector.selectedKeys().clear()
        }
      }
    } finally {
      socket.close()
    }
  }

  def read(reader: AsyncPacketReader): Unit = {
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
    writer.get.synchronized {
      writer.get.write(packet)
      socketKey.get.interestOps(socketKey.get.interestOps() | SelectionKey.OP_WRITE)
      selector.wakeup()
    }
  }

  def flush(): Unit = {
    writer.get.synchronized {
      if (!writer.get.flush()) {
        socketKey.get.interestOps(socketKey.get.interestOps() & ~SelectionKey.OP_WRITE)
      }
    }
  }

  def close(): Unit = {
    selector.close()
  }
}
