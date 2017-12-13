package se.nullable.kth.id1212.hangman.android.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.{AsyncTask, Bundle}
import android.view.View
import android.widget.{Button, EditText, ProgressBar, Toast}
import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.SocketChannel
import scala.concurrent.Future

import io.taig.android.concurrent.Executor._

class ConnectActivity extends Activity {
  implicit val context = this

  private var connectBtn: Button = _
  private var connectProgress: ProgressBar = _

  private var ipInput: EditText = _
  private var portInput: EditText = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val vh: TypedViewHolder.connect =
      TypedViewHolder.setContentView(this, TR.layout.connect)
    vh.connect_btn.setOnClickListener(ConnectListener)
    connectBtn = vh.connect_btn
    connectProgress = vh.connect_progress
    ipInput = vh.ip_input
    portInput = vh.port_input
  }

  object ConnectListener extends View.OnClickListener {
    override def onClick(v: View): Unit = {
      connectBtn.setVisibility(View.GONE)
      connectProgress.setVisibility(View.VISIBLE)

      val (ip, port) =
        (ipInput.getText.toString(), portInput.getText.toString())
      val addr = Future {
        val addr = new InetSocketAddress(ip, port.toInt)
        val socket = SocketChannel.open()
        try {
          socket.connect(addr)
        } finally {
          socket.close()
        }
        addr
      }
      addr.onFailure {
        case _: Exception =>
          Toast
            .makeText(context,
                      TR.string.connect_failed.value,
                      Toast.LENGTH_SHORT)
            .show()
          connectBtn.setVisibility(View.VISIBLE)
          connectProgress.setVisibility(View.GONE)
      }(Ui)
      addr.foreach { addr =>
        val intent = new Intent(ConnectActivity.this, classOf[MainActivity])
        intent.setData(
          new Uri.Builder()
            .scheme("hangman")
            .encodedAuthority(addr.toString().drop(1))
            .build())
        startActivity(intent)
        finish()
      }(Ui)
    }
  }
}
